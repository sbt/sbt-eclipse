
organization := "com.weiglewilczek.sbteclipse"

name := "sbteclipse"

version := "0.5"

sbtPlugin := true

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.RC2"

publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
