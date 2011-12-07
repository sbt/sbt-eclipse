
organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

TaskKey[Unit]("verify-command-name") <<= EclipseKeys.commandName map (name =>
  if (name != "eclipse") error("Expected command-name to be eclipse, but was %s!" format name)
)

TaskKey[Unit]("verify-execution-environment") <<= EclipseKeys.executionEnvironment map (env =>
  if (env != None) error("Expected execution-environment to be None, but was %s!" format env)
)

TaskKey[Unit]("verify-target") <<= (EclipseKeys.target, baseDirectory) map { (target, dir) =>
  val expectedTarget = new File(dir, ".target")
  if (target != expectedTarget) error("Expected target to be %s, but was %s!".format(expectedTarget, target))
}

TaskKey[Unit]("verify-skip-parents") <<= EclipseKeys.skipParents map { skipParents =>
  if (!skipParents) error("Expected skip-parents to be true, but was false!")
}

TaskKey[Unit]("verify-with-source") <<= EclipseKeys.withSource map { withSource =>
  if (withSource) error("Expected with-source to be false, but was true!")
}
