val circeVersion = "0.9.3"
val awsSdkVersion = "1.11.560"

libraryDependencies ++= Seq(
  "com.amazonaws"              %  "aws-java-sdk-s3"             % awsSdkVersion,
  "commons-io"                 %  "commons-io"                  % "2.6",
  "com.typesafe.scala-logging" %% "scala-logging"               % "3.9.2",
  "com.github.dwhjames"        %% "aws-wrap"                    % "0.12.1",
  "com.google.guava"           %  "guava"                       % "27.1-jre",
  "org.scala-lang"             %  "scala-compiler"              % scalaVersion.value,

  "io.circe"                   %% "circe-core"                  % circeVersion,
  "io.circe"                   %% "circe-generic"               % circeVersion,
  "io.circe"                   %% "circe-parser"                % circeVersion,
  "io.circe"                   %% "circe-generic-extras"        % circeVersion
)

libraryDependencies ++= Seq(
  "ch.qos.logback"             %  "logback-classic"             % "1.2.3",
  "org.scalatest"              %% "scalatest"                   % "3.0.7",
  "org.pegdown"                %  "pegdown"                     % "1.6.0"
).map(_ % "test, it")
