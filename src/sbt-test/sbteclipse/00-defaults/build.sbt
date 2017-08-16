
organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

TaskKey[Unit]("verify-command-name") := {
  val name = EclipseKeys.commandName.value
  if (name != "eclipse") sys.error(
    "Expected command-name to be eclipse, but was %s!" format name
  )
}
