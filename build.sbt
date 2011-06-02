
organization := "com.typesafe.sbteclipse"

name := "sbteclipse"

version := "0.8-SNAPSHOT"

sbtPlugin := true

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.RC2"

publishMavenStyle := false

projectID <<= (projectID, sbtVersion) { (id, version) => id.extra("sbtversion" -> version.toString) }

publishTo := {
  val typesafeRepoUrl = new java.net.URL("http://typesafe.artifactoryonline.com/typesafe/ivy-snapshots")
  val pattern = Patterns(false, "[organisation]/[module]/[sbtversion]/[revision]/[type]s/[module](-[classifier])-[revision].[ext]")
  Some(Resolver.url("Typesafe Repository", typesafeRepoUrl)(pattern))
}

credentials += Credentials(Path.userHome / ".ivy2" / ".typesafe-credentials")
