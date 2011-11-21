
organization := "com.typesafe.sbteclipse"

name := "sbteclipse"

sbtPlugin := true

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.3"

scalacOptions ++= Seq("-unchecked", "-deprecation")

publishTo <<= (version) { version =>
  val (name, url) =
    if (version endsWith "SNAPSHOT")
      "typesafe-ivy-snapshots" -> "http://repo.typesafe.com/typesafe/ivy-snapshots/"
    else
      "typesafe-ivy-releases" -> "http://repo.typesafe.com/typesafe/ivy-releases/"
  Some(Resolver.url(name, new java.net.URL(url))(Resolver.ivyStylePatterns))
}

publishMavenStyle := false

credentials += Credentials(Path.userHome / ".ivy2" / ".typesafe-credentials")
