lazy val dependencies = Seq(
  "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0",
  "org.specs2" % "specs2-core_2.12" % "3.9.4" % "test"
)

lazy val suba =
  Project("suba", new File("suba")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      scalaVersion := "2.10.6"
    )
  )

lazy val subb =
  Project("subb", new File("subb")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      scalaVersion := "2.12.19"
    )
  )
