package com.cibo.provenance

import io.circe.generic.auto._

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag
import scala.util.Try

/**
  * ValueWithProvenanceSerializable seale trait.
  * The hierarchy is a companion to the ValueWithProvenance sealed trait hierarchy.
  *
  * Each class in the tree is paired with another in the ValueWithProvenance tree,
  * but is structured to be serialized, and also deserialized in applications that do not
  * have access to the original softare classes.
  *
  * This means:
  * - type information is translated into String values
  * - nested values are serialized recursively, then referenced by storage ID rather than software reference
  * - storage IDs come from a digest of the consistently serialized data
  *
  * Each class in the hierarchy is a simple case class serializes as JSON.
  *
  * The companion object has an save() method that takes the related class and makes the *Serializable class,
  * along with an implicit ResultTracker, and actually "saves" deconstructs the object into pieces in storage.
  *
  * A matching load() method does the converse and turns a saved stub into a full object.  This will only
  * work in an application that has acces to the appropriate class, and in which the class has not
  * changed dramatically since it was saved.
  *
  * See also the *Deflated subclasses of ValueWithProvenance, which work in a library where the output
  * class is available, so the type is known, but it otherwise wraps these objects and defers deeper
  * instantiation. These allow for working with data where the output type is available but the inputs
  * only exist in a foreign library, or have been deleted/changed in the current library.
  *
  */
sealed trait ValueWithProvenanceSerializable {
  def load(implicit rt: ResultTracker): ValueWithProvenance[_]

  def loadAs[T <: ValueWithProvenance[_]](implicit rt: ResultTracker): T =
    load(rt).asInstanceOf[T]

  def tryLoad(implicit rt: ResultTracker): Try[ValueWithProvenance[_]] =
    Try(load)

  def tryLoadAs[T <: ValueWithProvenance[_]](implicit rt: ResultTracker): Try[T] =
    tryLoad.map(_.asInstanceOf[T])

  def wrap[O]: ValueWithProvenanceDeflated[O]

  @transient
  def toBytes: Array[Byte] = Codec.serialize(this)

  @transient
  def toDigest: Digest = Codec.digestBytes(toBytes)
}


object ValueWithProvenanceSerializable {

  // The codec for the *Serializable object tree is simple and can be auto-derived by circe.
  // The codec for the regular ValueWithProvenance[_] piggy-backs on this codec.
  import io.circe.generic.semiauto._
  implicit val codec: CirceJsonCodec[ValueWithProvenanceSerializable] =
    Codec(deriveEncoder[ValueWithProvenanceSerializable], deriveDecoder[ValueWithProvenanceSerializable])

  def save[O](value: ValueWithProvenance[O])(implicit rt: ResultTracker): ValueWithProvenanceSerializable = {
    value match {
      case result: FunctionCallResultWithProvenance[O] =>
        FunctionCallResultWithKnownProvenanceSerializable.save(result)(rt)
      case call: FunctionCallWithProvenance[O] =>
        FunctionCallWithProvenanceSerializable.save(call)(rt)
      case other =>
        throw new RuntimeException(f"Cannot untypify $other to a ValueWithProvenanceSaved! (Implement me.)")
    }
  }
}


sealed trait FunctionCallWithProvenanceSerializable extends ValueWithProvenanceSerializable {
  def outputClassName: String

  def load(implicit rt: ResultTracker): FunctionCallWithProvenance[_]

  def wrap[O]: FunctionCallWithProvenanceDeflated[O] = FunctionCallWithProvenanceDeflated[O](this)
}


object FunctionCallWithProvenanceSerializable {
  def save[O](call: FunctionCallWithProvenance[O])(implicit rt: ResultTracker): FunctionCallWithProvenanceSerializable = {
    call match {
      case u: UnknownProvenance[O] =>
        FunctionCallWithUnknownProvenanceSerializable.save[O](u)(rt)
      case known: FunctionCallWithProvenance[O] =>
        FunctionCallWithKnownProvenanceSerializable.save[O](known)(rt)
    }
  }
}


case class FunctionCallWithUnknownProvenanceSerializable(
  outputClassName: String,
  valueDigest: Digest
) extends FunctionCallWithProvenanceSerializable {
  def load(implicit rt: ResultTracker): UnknownProvenance[_] = {
    val clazz: Class[_] = Class.forName(outputClassName)
    loadWithKnownTypeAndUnknownCodec(clazz)
  }

  def loadWithKnownTypeAndUnknownCodec[T](clazz: Class[T])(
    implicit rt: ResultTracker
  ): UnknownProvenance[_] = {
    implicit val ct: ClassTag[T] = ClassTag(clazz)
    val (value, codec) = rt.loadValueWithCodec[T](valueDigest)
    UnknownProvenance(value)(codec)
  }

  def loadWithKnownTypeAndCodec[T : Codec](implicit rt: ResultTracker): UnknownProvenance[_] = {
    val value: T = rt.loadValue[T](valueDigest)
    UnknownProvenance(value)
  }
}


object FunctionCallWithUnknownProvenanceSerializable {
  def save[O](call: UnknownProvenance[O])(implicit rt: ResultTracker): FunctionCallWithUnknownProvenanceSerializable = {
    implicit val outputClassTag: ClassTag[O] = call.outputClassTag
    implicit val outputCodec: Codec[O] = call.outputCodec
    val outputClassName = Codec.classTagToSerializableName(outputClassTag)
    val valueDigest: Digest = rt.saveOutputValue(call.value)
    val callSerializable = FunctionCallWithUnknownProvenanceSerializable(outputClassName, valueDigest)
    rt.saveCallSerializable(callSerializable)
    callSerializable
  }
}


sealed trait FunctionCallWithKnownProvenanceSerializable extends FunctionCallWithProvenanceSerializable {
  def functionName: String
  def functionVersion: Version
  def outputClassName: String
  def load(implicit rt: ResultTracker): FunctionCallWithProvenance[_]
}


object FunctionCallWithKnownProvenanceSerializable {
  def save[O](call: FunctionCallWithProvenance[O])(implicit rt: ResultTracker): FunctionCallWithKnownProvenanceSerializableWithInputs =
    FunctionCallWithKnownProvenanceSerializableWithInputs.save(call)
}


case class FunctionCallWithKnownProvenanceSerializableWithInputs(
  functionName: String,
  functionVersion: Version,
  outputClassName: String,
  inputList: List[ValueWithProvenanceSerializable]
) extends FunctionCallWithKnownProvenanceSerializable {

  @transient
  lazy val inputGroupBytes: Array[Byte] = Codec.serialize(inputValueDigests.map(_.id))

  @transient
  lazy val inputGroupDigest: Digest = Codec.digestBytes(inputGroupBytes)

  def unexpandInputs: FunctionCallWithKnownProvenanceSerializableWithoutInputs =
    FunctionCallWithKnownProvenanceSerializableWithoutInputs(
      functionName,
      functionVersion,
      outputClassName,
      toDigest
    )

  def inputResultVector: Vector[FunctionCallResultWithProvenanceSerializable] =
    inputList.map(_.asInstanceOf[FunctionCallResultWithProvenanceSerializable]).toVector

  def inputValueDigests: Vector[Digest] =
    inputResultVector.map(_.outputDigest)

  def load(implicit rt: ResultTracker): FunctionCallWithProvenance[_] = {
    val clazz = Class.forName(outputClassName)
    load(clazz)(rt)
  }

  private def load[T](clazz: Class[T])(implicit rt: ResultTracker): FunctionCallWithProvenance[T] = {
    val f = FunctionWithProvenance.getByName(functionName)
    val call: FunctionCallWithProvenance[_] = f.deserializeCall(this)
    call.asInstanceOf[FunctionCallWithProvenance[T]]
  }
}


object FunctionCallWithKnownProvenanceSerializableWithInputs {
  def save[O](call: FunctionCallWithProvenance[O])(implicit rt: ResultTracker): FunctionCallWithKnownProvenanceSerializableWithInputs = {
    implicit val outputClassTag: ClassTag[O] = call.outputClassTag
    implicit val outputCodec: Codec[O] = call.outputCodec
    val outputClassName = Codec.classTagToSerializableName(outputClassTag)

    val callInSavableForm =
      call.versionValueAlreadyResolved match {
        case Some(versionValue) =>
          FunctionCallWithKnownProvenanceSerializableWithInputs(
            call.functionName,
            versionValue,
            outputClassName,
            call.inputs.toList.map {
              case dcall: FunctionCallWithProvenanceDeflated[_] =>
                dcall.data
              case dresult: FunctionCallResultWithProvenanceDeflated[_] =>
                dresult.data
              case u: UnknownProvenance[_] =>
                FunctionCallWithUnknownProvenanceSerializable.save(u)(rt)
              case u: UnknownProvenanceResolved[_] =>
                FunctionCallResultWithUnknownProvenanceSerializable.save(u)(rt)
              case kcall: FunctionCallWithProvenance[_] =>
                FunctionCallWithKnownProvenanceSerializableWithInputs.save(kcall)(rt).unexpandInputs
              case kresult: FunctionCallResultWithProvenance[_] =>
                FunctionCallResultWithKnownProvenanceSerializable.save(kresult)(rt)
              case other =>
                throw new RuntimeException(f"Unexpected sub-type of ValueWithProvenance: $other")
            }
          )
        case None =>
          // While this call cannot be saved directly, any call that uses it as an input
          // will simply store a "fatter" value.
          // This exception is intercepted when a "downstream" call that uses this as an input
          // tries to save its inputs, allowing it to respond accordingly.
          throw new UnresolvedVersionException(call)
      }

    rt.saveCallSerializable(callInSavableForm)

    callInSavableForm
  }
}

class UnresolvedVersionException(call: Call[_]) extends RuntimeException()

case class FunctionCallWithKnownProvenanceSerializableWithoutInputs(
  functionName: String,
  functionVersion: Version,
  outputClassName: String,
  digestOfEquivalentWithInputs: Digest
) extends FunctionCallWithKnownProvenanceSerializable {

  def expandInputs(rts: ResultTracker): FunctionCallWithKnownProvenanceSerializableWithInputs =
    rts.loadCallById(digestOfEquivalentWithInputs).map(_.data)
      .get.asInstanceOf[FunctionCallWithKnownProvenanceSerializableWithInputs]

  def load(implicit rt: ResultTracker): FunctionCallWithProvenance[_] =
    expandInputs(rt).load
}


object FunctionCallWithKnownProvenanceSerializableWithoutInputs {
  def save[O](call: FunctionCallWithProvenance[O])(implicit rt: ResultTracker): FunctionCallWithKnownProvenanceSerializableWithoutInputs =
    FunctionCallWithKnownProvenanceSerializableWithInputs.save(call).unexpandInputs
}


sealed trait FunctionCallResultWithProvenanceSerializable extends ValueWithProvenanceSerializable {
  def call: FunctionCallWithProvenanceSerializable
  def inputGroupDigest: Digest
  def outputDigest: Digest
  def commitId: String
  def buildId: String

  def wrap[O]: FunctionCallResultWithProvenanceDeflated[O] = FunctionCallResultWithProvenanceDeflated[O](this)

  def load(implicit rt: ResultTracker): FunctionCallResultWithProvenance[_]
}

object FunctionCallResultWithProvenanceSerializable {
  def save[O](result: FunctionCallResultWithProvenance[O])(implicit rt: ResultTracker): FunctionCallResultWithProvenanceSerializable = {
    result.call match {
      case _: UnknownProvenance[O] => FunctionCallResultWithUnknownProvenanceSerializable.save(result)
      case _ => FunctionCallResultWithKnownProvenanceSerializable.save(result)
    }
  }
}


case class FunctionCallResultWithKnownProvenanceSerializable(
  call: FunctionCallWithKnownProvenanceSerializableWithoutInputs,
  inputGroupDigest: Digest,
  outputDigest: Digest,
  commitId: String,
  buildId: String
) extends FunctionCallResultWithProvenanceSerializable {

  def load(implicit rt: ResultTracker): FunctionCallResultWithProvenance[_] = {
    val clazz = Class.forName(call.outputClassName)
    load(clazz)
  }

  private def load[T](clazz: Class[T])(implicit rt: ResultTracker): FunctionCallResultWithProvenance[T] = {
    val call = this.call.load(rt).asInstanceOf[FunctionCallWithProvenance[T]]
    implicit val cd = call.outputCodec
    implicit val ct = call.outputClassTag
    assert(ct == cd.classTag)
    val bi = BuildInfoBrief(commitId, buildId)
    val output: T = rt.loadValue[T](outputDigest)
    call.newResult(VirtualValue(Some(output), Some(outputDigest), None)(cd))(bi)
  }
}


object FunctionCallResultWithKnownProvenanceSerializable {
  def save[O](result: FunctionCallResultWithProvenance[O])(implicit rt: ResultTracker): FunctionCallResultWithKnownProvenanceSerializable = {
    val call = result.call

    rt.saveBuildInfo

    val callSaved: FunctionCallWithProvenanceDeflated[_] = call.save(rt)
    val callSavedWithInputs =
      callSaved.data.asInstanceOf[FunctionCallWithKnownProvenanceSerializableWithInputs]

    val output = result.output
    implicit val outputCodec: Codec[O] = call.outputCodec
    implicit val outputClassTag: ClassTag[O] = call.outputClassTag
    val outputDigest = rt.saveOutputValue(output)

    val resultInSavableForm =
      FunctionCallResultWithKnownProvenanceSerializable(
        callSavedWithInputs.unexpandInputs,
        callSavedWithInputs.inputGroupDigest,
        outputDigest,
        result.outputBuildInfo.commitId,
        result.outputBuildInfo.buildId
      )

    val in: Vector[FunctionCallResultWithProvenanceSerializable] = callSavedWithInputs.inputResultVector
    rt.saveResultSerializable(resultInSavableForm, in)

    resultInSavableForm
  }
}


case class FunctionCallResultWithUnknownProvenanceSerializable(
  call: FunctionCallWithUnknownProvenanceSerializable,
  outputDigest: Digest,
  commitId: String,
  buildId: String
) extends FunctionCallResultWithProvenanceSerializable {

  val inputGroupDigest: Digest = Codec.digestObject(List[Digest]())

  def load(implicit rt: ResultTracker): FunctionCallResultWithProvenance[_] =
    call.load.resolve
}


object FunctionCallResultWithUnknownProvenanceSerializable {
  def save[O](result: FunctionCallResultWithProvenance[O])(implicit rt: ResultTracker): FunctionCallResultWithUnknownProvenanceSerializable = {
    val call = result.call

    rt.saveBuildInfo

    val callSaved: FunctionCallWithUnknownProvenanceSerializable =
      FunctionCallWithProvenanceSerializable.save(call)(rt).asInstanceOf[FunctionCallWithUnknownProvenanceSerializable]

    val resultInSavableForm =
      FunctionCallResultWithUnknownProvenanceSerializable(
        callSaved,
        callSaved.valueDigest,
        result.outputBuildInfo.commitId,
        result.outputBuildInfo.buildId
      )
    resultInSavableForm
  }
}

case class FunctionWithProvenanceSerializable(
  functionNameClassName: String,
  typeParameterClassNames: List[String]
)