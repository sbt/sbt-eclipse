import sbt._
import sbt.Keys._
import com.typesafe.sbteclipse.EclipsePlugin.{ EclipseKeys, EclipseExecutionEnvironment }

object Build extends Build {

  lazy val root = Project(
    "root",
    new File("."),
    settings = Project.defaultSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= baseDirectory(new File(_, "src/main/scala")),
      unmanagedSourceDirectories in Test <+= baseDirectory(new File(_, "src/test/scala")),
      libraryDependencies ++= Seq(
          "org.scala-lang" % "scala-compiler" % "2.9.1"
      )
    ),
    aggregate = Seq(sub)
  )

  lazy val sub: Project = Project(
    "sub",
    new File("sub"),
    settings = Project.defaultSettings ++ Seq(
      EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE16)
    ),
    aggregate = Seq(suba, subb)
  )

  lazy val suba = Project(
    "suba",
    new File("sub/suba"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7",
        "biz.aQute" % "bndlib" % "1.50.0"
      )
    )
  )

  lazy val subb = Project(
    "subb",
    new File("sub/subb"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "biz.aQute" % "bndlib" % "1.50.0"
      ),
      scalacOptions := Seq("-unchecked", "-deprecation")
    ),
    dependencies = Seq(suba, suba % "test->test")
  )  
}
