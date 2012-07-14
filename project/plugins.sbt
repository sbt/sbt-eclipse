// needed to resolve sbt-release in SBT versions before 0.12.x
resolvers += Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.5")

addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.4.0")

libraryDependencies <+= (sbtVersion)(sbtVersion =>
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion
)
