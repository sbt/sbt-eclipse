lazy val dependencies = Seq(
  "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0",
  "org.specs2" % "specs2-core_2.12" % "3.9.4" % "test"
)

lazy val suba =
  Project("suba", new File("suba")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= dependencies
    )
  )

lazy val subb =
  Project("subb", new File("subb")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= dependencies,
      EclipseKeys.withSource := true
    )
  )

lazy val subc =
  Project("subc", new File("subc")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= dependencies,
      EclipseKeys.withJavadoc := true
    )
  )

lazy val subd =
  Project("subd", new File("subd")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= dependencies,
      EclipseKeys.withSource := true,
      EclipseKeys.withJavadoc := true
    )
  )
