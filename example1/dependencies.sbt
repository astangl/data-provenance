
libraryDependencies ++= Seq(
  "com.cibo"                          %% "provenance"               % "0.12.0-SNAPSHOT",

  "com.github.scopt"                  %% "scopt"                    % "3.7.1",
  "com.typesafe"                      %  "config"                   % "1.3.4",
  "com.typesafe.scala-logging"        %% "scala-logging"            % "3.9.2",
  "ch.qos.logback"                    %  "logback-classic"          % "1.2.3"
)

libraryDependencies ++= Seq(
  "ch.qos.logback"             %  "logback-classic"             % "1.2.3",
  "org.scalatest"              %% "scalatest"                   % "3.0.7",
  "org.pegdown"                %  "pegdown"                     % "1.6.0"
).map(_ % "test")

