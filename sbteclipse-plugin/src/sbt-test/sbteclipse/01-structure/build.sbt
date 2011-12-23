
organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

//TaskKey[Unit]("verify-command-name") <<= EclipseKeys.commandName map (name =>
//  if (name != "eclipse") error("Expected command-name to be eclipse, but was %s!" format name)
//)
