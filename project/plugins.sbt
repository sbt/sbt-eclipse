
resolvers ++= Seq(
  "gseitz@github" at "http://gseitz.github.com/maven/",
  Resolver.url("heikoseeberger", new URL("http://hseeberger.github.com/releases"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.0")

addSbtPlugin("name.heikoseeberger.sbtproperties" % "sbtproperties" % "1.0.0")

addSbtPlugin("net.databinder" % "posterous-sbt" % "0.3.2")

libraryDependencies <+= (sbtVersion)(sbtVersion =>
  "org.scala-tools.sbt" %% "scripted-plugin" % sbtVersion
)
