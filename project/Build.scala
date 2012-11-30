import com.typesafe.sbtscalariform.ScalariformPlugin._
import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin._
import sbtrelease.ReleasePlugin._

object Build extends Build {

  lazy val root = Project(
    "sbteclipse",
    file("."),
    aggregate = Seq(sbteclipseCore, sbteclipsePlugin),
    settings = commonSettings ++ Seq(
      publishArtifact := false
    )
  )

  lazy val sbteclipseCore = Project(
    "sbteclipse-core",
    file("sbteclipse-core"),
    settings = commonSettings ++ Seq(
      libraryDependencies ++= Seq("org.scalaz" %% "scalaz-core" % "6.0.4")
    )
  )

  lazy val sbteclipsePlugin = Project(
    "sbteclipse-plugin",
    file("sbteclipse-plugin"),
    dependencies = Seq(sbteclipseCore),
    settings = commonSettings
  )

  def commonSettings = Defaults.defaultSettings ++
    scalariformSettings ++
    scriptedSettings ++
    releaseSettings ++
    Seq(
      organization := "com.typesafe.sbteclipse",
      // version is defined in version.sbt in order to support sbt-release
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      publishTo <<= isSnapshot { isSnapshot =>
        val id = if (isSnapshot) "snapshots" else "releases"
        val uri = "https://typesafe.artifactoryonline.com/typesafe/ivy-" + id
        Some(Resolver.url("typesafe-" + id, url(uri))(Resolver.ivyStylePatterns))
      },
      sbtPlugin := true,
      publishMavenStyle := false,
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false,
      scriptedLaunchOpts += "-Xmx1024m"
    )
}
