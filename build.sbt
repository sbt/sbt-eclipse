
organization := "com.typesafe.sbteclipse"

name := "sbteclipse"

version := "1.4.0-RC4"

sbtPlugin := true

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.2"

scalacOptions ++= Seq("-unchecked", "-deprecation")

publishTo <<= (version) { v =>
  import Classpaths._
  Option(if (v endsWith "SNAPSHOT") typesafeSnapshots else typesafeResolver)
}

publishMavenStyle := false

credentials += Credentials(Path.userHome / ".ivy2" / ".typesafe-credentials")
