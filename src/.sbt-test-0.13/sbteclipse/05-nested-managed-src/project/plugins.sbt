{
  val pluginVersion = System.getProperty("plugin.version")
  if(pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % pluginVersion)
}

addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.3.2")

resolvers += "spray repo" at "http://repo.spray.io"

addSbtPlugin("io.spray" % "sbt-twirl" % "0.7.0")
