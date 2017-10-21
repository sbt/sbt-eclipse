import sbt.ScriptedPlugin._
import _root_.bintray.BintrayPlugin.bintrayPublishSettings

crossSbtVersions := Seq("1.0.2", "0.13.16")

val baseVersion = "5.2.3-SNAPSHOT"

val maxMetaspaceSize = if (util.Properties.isJavaAtLeast("1.8")) {
  "-XX:MaxMetaspaceSize=384m"
} else {
  "-XX:MaxPermSize=384m"
}

lazy val root = Project(
  "sbteclipse-plugin",
  file("."),
  settings = commonSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.scalaz"    %% "scalaz-core"   % "7.2.14",
      "org.scalaz"    %% "scalaz-effect" % "7.2.14",
      "org.scalatest" %% "scalatest"     % "3.0.1" % "test")
  )
)

def commonSettings = {
  versionWithGit ++
  scriptedSettings ++
  sbtrelease.ReleasePlugin.projectSettings ++
  bintrayPublishSettings ++
  Seq(
    organization := "com.typesafe.sbteclipse",
    sbtPlugin := true,
    git.baseVersion := baseVersion,
    sbtVersion in GlobalScope := {
      System.getProperty("sbt.build.version", (sbtVersion in GlobalScope).value)
    },
    scalacOptions ++= Seq("-unchecked", "-deprecation",
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => "-target:jvm-1.8"
        case _             => "-target:jvm-1.7"
      }
    ),
    scalaVersion := {
      (sbtVersion in GlobalScope).value match {
        case sbt10  if sbt10.startsWith("1.0") => "2.12.4"
        case sbt013 if sbt013.startsWith("0.13.") => "2.10.6"
        case _ => "2.12.4"
      }
    },
    sbtDependency in GlobalScope := {
      (sbtDependency in GlobalScope).value.copy(revision = (sbtVersion in GlobalScope).value)
    },
    publishMavenStyle := false,
    bintrayOrganization := Some("sbt"),
    bintrayPackage := "sbteclipse",
    bintrayRepository := "sbt-plugin-releases",
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(maxMetaspaceSize, "-Dplugin.version=" + version.value)
  )
}
