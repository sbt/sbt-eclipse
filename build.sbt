
organization := "com.weiglewilczek.sbteclipse"

name := "sbteclipse"

version := "0.4-SNAPSHOT"

sbtPlugin := true

resolvers += ScalaToolsSnapshots

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0-SNAPSHOT"

publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/")
