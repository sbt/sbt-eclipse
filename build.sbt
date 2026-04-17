lazy val scala212 = "2.12.21"
lazy val scala3 = "3.8.3"

ThisBuild / crossScalaVersions := Seq(scala212, scala3)

lazy val root = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    Seq(
      organization := "com.github.sbt",
      name := "sbt-eclipse",
      crossScalaVersions := Seq(scala212, scala3),
      scalacOptions ++= {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, _)) => Seq("-unchecked", "-deprecation", "-Xsource:3", "-release:8")
          case _            => Seq("-unchecked", "-deprecation")
        }
      },
      scalaVersion := scala212,
      ThisBuild / (pluginCrossBuild / sbtVersion) := {
        scalaBinaryVersion.value match {
          case "2.12" => "1.12.9"
          case _      => "2.0.0-RC11"
        }
      },
      // Customise sbt-dynver's behaviour to make it work with tags which aren't v-prefixed
      ThisBuild / dynverVTagPrefix := false,
      // Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
      // https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
      Global / onLoad := (Global / onLoad).value.andThen { s =>
        dynverAssertTagVersion.value
        s
      },
      libraryDependencies ++= Seq(
        "org.scalaz"             %% "scalaz-core"   % "7.2.36",
        "org.scalaz"             %% "scalaz-effect" % "7.2.36",
        "org.scalatest"          %% "scalatest"     % "3.2.20" % "test"
      ),
      homepage := Some(url("https://github.com/sbt/sbt-eclipse")),
      licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
      developers += Developer(
        "playframework",
        "The Play Framework Team",
        "contact@playframework.com",
        url("https://github.com/playframework")
      ),
      scriptedBufferLog := false,
      scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    )
  )
