import sbt._
import sbt.Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.{ EclipseKeys } 

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
        "ch.qos.logback" % "logback-classic" % "1.0.1"
      )
    )
  )

  lazy val subb = Project(
    "subb",
    new File("sub/subb"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.1" % "test",
        "biz.aQute" % "bndlib" % "1.50.0"
      ),
      scalacOptions := Seq("-unchecked", "-deprecation"),
      TaskKey[Unit]("touch-pre-task") <<= baseDirectory map { dir => new File(dir, "touch-pre-task").mkdirs() },
      EclipseKeys.preTasks := Seq(TaskKey[Unit]("touch-pre-task"))
    ),
    dependencies = Seq(suba, suba % "test->test")
  )  

  lazy val subc = Project(
    "subc",
    new File("sub/subc"),
    settings = Project.defaultSettings ++ Seq(
      EclipseKeys.skipProject := true
    )
  )
}
