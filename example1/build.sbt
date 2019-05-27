
organization := "com.cibo"
name         := "provenance-example1"

crossScalaVersions := Seq("2.12.8")
scalaVersion := crossScalaVersions.value.head

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xfatal-warnings", "-target:jvm-1.8")

fork in run := false
mainClass in Compile := Some("com.cibo.provenance.examples.TrackMe")

// Test config
fork in Test := true
testOptions in Test ++= Seq(Tests.Argument("-oDF"), Tests.Argument("-h", "target/unit-test-reports"))

