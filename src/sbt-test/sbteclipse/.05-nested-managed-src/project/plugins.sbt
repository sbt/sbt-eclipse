{
  val pluginVersion = System.getProperty("plugin.version")
  if(pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("com.github.sbt" % "sbt-eclipse" % pluginVersion)
}

addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.4")
