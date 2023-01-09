lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    Seq(
      organization := "com.github.sbt",
      sonatypeProfileName := "com.github.sbt.sbt-eclipse", // See https://issues.sonatype.org/browse/OSSRH-77819#comment-1203625
      name := "sbt-eclipse",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-target:jvm-1.8"),
      scalaVersion := "2.12.17",
      // Customise sbt-dynver's behaviour to make it work with tags which aren't v-prefixed
      ThisBuild / dynverVTagPrefix := false,
      // Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
      // https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
      Global / onLoad := (Global / onLoad).value.andThen { s =>
        dynverAssertTagVersion.value
        s
      },
      libraryDependencies ++= Seq(
        "org.scalaz"             %% "scalaz-core"   % "7.2.35",
        "org.scalaz"             %% "scalaz-effect" % "7.2.35",
        "org.scalatest"          %% "scalatest"     % "3.2.14" % "test"
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
