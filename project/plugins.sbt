
// Waiting for a 0.5 release, capable of sbt-0.11.3 and sbt-0.12.x and published to OSS
//addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.4.0")

libraryDependencies <+= (sbtVersion)(sbtVersion =>
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion
)
