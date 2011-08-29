
organization := "com.typesafe.sbteclipse"

name := "sbteclipse"

version := "1.4-SNAPSHOT"

sbtPlugin := true

resolvers += ScalaToolsSnapshots

libraryDependencies += "org.scalaz" % "scalaz-core_2.9.1.RC1" % "6.0.2-SNAPSHOT"

scalacOptions ++= Seq("-unchecked", "-deprecation")

publishMavenStyle := false

publishTo <<= (version) { v =>
  import Classpaths._
  Option(if (v endsWith "SNAPSHOT") typesafeSnapshots else typesafeResolver)
}

credentials += Credentials(Path.userHome / ".ivy2" / ".typesafe-credentials")
