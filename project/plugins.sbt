
resolvers ++= Seq(
  Classpaths.typesafeSnapshots,
  Classpaths.sbtPluginSnapshots
)

libraryDependencies <+= (sbtVersion)("org.scala-sbt" % "scripted-plugin" % _)

//addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.5-SNAPSHOT")

addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.5.0-SNAPSHOT")
