import sbt._
import sbt.Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.{ EclipseCreateSrc, EclipseKeys, EclipseExecutionEnvironment }

object Build extends Build {

  lazy val root = Project(
    "root",
    new File("."),
    settings = Project.defaultSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= baseDirectory(new File(_, "src/main/scala")),
      unmanagedSourceDirectories in Test <+= baseDirectory(new File(_, "src/test/scala")),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % "2.9.1",
        "biz.aQute" % "bndlib" % "1.50.0"
      ),
      retrieveManaged := true
    ),
    aggregate = Seq(sub)
  )

  lazy val sub: Project = Project(
    "sub",
    new File("sub"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "biz.aQute" % "bndlib" % "1.50.0"
      ),
      retrieveManaged := true,
      EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE16),
      EclipseKeys.withSource := true
    ),
    aggregate = Seq(suba, subb, subc)
  )

  lazy val suba = Project(
    "suba",
    new File("sub/suba"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7",
        "biz.aQute" % "bndlib" % "1.50.0",
        "org.specs2" %% "specs2" % "1.6.1" % "test"
      ),
      EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.Managed, EclipseCreateSrc.Resource),
      EclipseKeys.withSource := true
    )
  )

  lazy val subb = Project(
    "subb",
    new File("sub/subb"),
    configurations = Configurations.default :+ Configurations.IntegrationTest,
    settings = Project.defaultSettings ++ Defaults.itSettings ++ Seq(
      libraryDependencies ++= Seq(
        "biz.aQute" % "bndlib" % "1.50.0",
        "junit" % "junit" % "4.7" % "it"
      ),
      retrieveManaged := true,
      scalacOptions := Seq("-unchecked", "-deprecation"),
      EclipseKeys.configurations := Set(Configurations.Compile, Configurations.IntegrationTest)
    ),
    dependencies = Seq(suba, suba % "test->compile", subc % "test->test")
  )

  lazy val subc = Project(
    "subc",
    new File("sub/subc")
  )
}
