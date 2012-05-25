import com.typesafe.sbtscalariform.ScalariformPlugin._
import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin._

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
      libraryDependencies ++= Seq("org.scalaz" %% "scalaz-core" % "6.0.3")
    )
  )

  lazy val sbteclipsePlugin = Project(
    "sbteclipse-plugin",
    file("sbteclipse-plugin"),
    dependencies = Seq(sbteclipseCore),
    settings = commonSettings
  )

  def commonSettings = Defaults.defaultSettings ++
    Seq(
      organization := "com.typesafe.sbteclipse",
      // version is defined in version.sbt in order to support sbt-release
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      publishTo <<= isSnapshot(if (_) Some(Classpaths.typesafeSnapshots) else Some(Classpaths.typesafeResolver)),
      sbtPlugin := true,
      publishMavenStyle := false,
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false
    ) ++
    scalariformSettings ++
    scriptedSettings /*++
    Release.releaseSettings ++ Seq(
      ReleaseKeys.releaseProcess <<= thisProjectRef { ref =>
        import ReleaseStateTransformations._
        Seq[ReleasePart](
          initialGitChecks,
          checkSnapshotDependencies,
          releaseTask(check in Posterous in ref),
          inquireVersions,
          runTest,
          setReleaseVersion,
          commitReleaseVersion,
          tagRelease,
          releaseTask(publish in Global in ref),
          releaseTask(publish in Posterous in ref),
          setNextVersion,
          commitNextVersion
        )
      }
    )*/
}
