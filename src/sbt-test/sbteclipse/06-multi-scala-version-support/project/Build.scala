import sbt._
import sbt.Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._

object Build extends Build {
  
  lazy val dependencies = Seq(
    "biz.aQute" % "bndlib" % "1.50.0",
    "org.specs2" %% "specs2" % "2.1.1" % "test"
  )

  lazy val suba = Project(
    "suba",
    new File("suba"),
    settings = Project.defaultSettings ++ Seq(
      scalaVersion := "2.10.3"
    )
  )
  
  lazy val subb = Project(
    "subb",
    new File("subb"),
    settings = Project.defaultSettings ++ Seq(
      scalaVersion := "2.11.3"
    )
  )
  
}
