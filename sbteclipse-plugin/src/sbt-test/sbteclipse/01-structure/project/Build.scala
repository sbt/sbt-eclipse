import sbt._
import sbt.Keys._

object Build extends Build {

  lazy val root = Project(
    "root",
    new File("."),
    aggregate = Seq(sub)
  )

  lazy val sub = Project(
    "sub",
    new File("sub"),
    aggregate = Seq(suba, subb)
  )

  lazy val suba = Project(
    "suba",
    new File("sub/suba"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7"
      )
    )
  )

  lazy val subb = Project(
    "subb",
    new File("sub/subb"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7" % "test",
        "biz.aQute" % "bndlib" % "1.50.0"
      ),
      scalacOptions := Seq("-unchecked", "-deprecation")
    ),
    dependencies = Seq(suba, suba % "test->test")
  )  
}
