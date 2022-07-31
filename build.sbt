import _root_.bintray.BintrayPlugin.bintrayPublishSettings

val baseVersion = "6.0.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, SbtPlugin)
  .settings(
    versionWithGit ++
    sbtrelease.ReleasePlugin.projectSettings ++
    bintrayPublishSettings ++
    Seq(
      organization := "com.typesafe.sbteclipse",
      name := "sbteclipse-plugin",
      sbtPlugin := true,
      git.baseVersion := baseVersion,
      sbtVersion in GlobalScope := {
        System.getProperty("sbt.build.version", (sbtVersion in GlobalScope).value)
      },
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-target:jvm-1.8"),
      scalaVersion := "2.12.16",
      libraryDependencies ++= Seq(
        "org.scala-lang.modules" %% "scala-xml"     % "1.3.0",
        "org.scalaz"             %% "scalaz-core"   % "7.2.34",
        "org.scalaz"             %% "scalaz-effect" % "7.2.34",
        "org.scalatest"          %% "scalatest"     % "3.2.13" % "test"
      ),
      publishMavenStyle := false,
      bintrayOrganization := Some("sbt"),
      bintrayPackage := "sbteclipse",
      bintrayRepository := "sbt-plugin-releases",
      licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false,
      scriptedBufferLog := false,
      scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    )
  )
