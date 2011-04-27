
organization := "com.weiglewilczek.sbteclipse"

name := "sbteclipse"

version := "0.4-SNAPSHOT"

resolvers += ScalaToolsSnapshots

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0-SNAPSHOT"

sbtPlugin := true

publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/")
