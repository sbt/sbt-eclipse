import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin._
import sbtrelease.ReleasePlugin._
import com.typesafe.sbt.SbtScalariform._

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

  def commonSettings =
    Defaults.defaultSettings ++
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
      sbtVersion in GlobalScope <<= (sbtVersion in GlobalScope) { sbtVersion =>
        System.getProperty("sbt.build.version", sbtVersion)
      },
      sbtBinaryVersion in GlobalScope <<= (sbtVersion in GlobalScope) { sbtVersion =>
        // This isn't needed once we start using SBT 0.13 to build
        if (CrossVersion.isStable(sbtVersion)) CrossVersion.binarySbtVersion(sbtVersion) else sbtVersion
      },
      scalaVersion <<= (sbtVersion in GlobalScope) {
        case sbt013 if sbt013.startsWith("0.13.") => "2.10.2"
        case sbt012 if sbt012.startsWith("0.12.") => "2.9.1"
        case _ => "2.9.1"
      },
      sbtDependency in GlobalScope <<= (sbtDependency in GlobalScope, sbtVersion in GlobalScope) { (dep, sbtVersion) =>
        dep.copy(revision = sbtVersion)
      },
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false,
      scriptedLaunchOpts += "-Xmx1024m"
    )
}
