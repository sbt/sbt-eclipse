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
      libraryDependencies ++= dependencies
    )
  )
  
  lazy val subb = Project(
    "subb",
    new File("subb"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= dependencies,
      EclipseKeys.withSource := true
    )
  )
  
  lazy val subc = Project(
    "subc",
    new File("subc"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= dependencies,
      EclipseKeys.withJavadoc := true
    )
  )
  
  lazy val subd = Project(
    "subd",
    new File("subd"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= dependencies,
      EclipseKeys.withSource := true,
      EclipseKeys.withJavadoc := true
    )
  )
}
