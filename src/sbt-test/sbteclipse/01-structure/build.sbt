import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

lazy val root =
  Project("root", new File(".")).
  aggregate(sub)

lazy val sub =
  Project("sub", new File("sub")).
  aggregate(suba, subb)

lazy val suba =
  Project("suba", new File("sub/suba")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.1"
      )
    )
  )

lazy val subb =
  Project("subb", new File("sub/subb")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.1" % "test",
        "biz.aQute" % "bndlib" % "1.50.0"
      ),
      scalacOptions := Seq("-unchecked", "-deprecation"),
      TaskKey[Unit]("touch-pre-task") := {
        new File(baseDirectory.value, "touch-pre-task").mkdirs
      },
      EclipseKeys.preTasks := Seq(TaskKey[Unit]("touch-pre-task"))
    )
  ).
  dependsOn(suba, suba % "test->test")

lazy val subc =
  Project("subc", new File("sub/subc")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      EclipseKeys.skipProject := true
    )
  )
