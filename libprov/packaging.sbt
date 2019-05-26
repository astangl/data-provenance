publishArtifact in Test := false
publishArtifact in IntegrationTest := false

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

dockerRepository := Some("473168459077.dkr.ecr.us-east-1.amazonaws.com")
dockerBaseImage := "473168459077.dkr.ecr.us-east-1.amazonaws.com/service-base-image:0.1.0-6"
mainClass in Docker in Compile := Some("com.cibo.provenance.ExampleMain")
dockerUpdateLatest := true
