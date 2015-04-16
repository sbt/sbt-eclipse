import sbt._
import sbt.Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._

object Build extends Build {

  lazy val root = Project(
    "root",
    new File("."),
    settings = Project.defaultSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= baseDirectory(new File(_, "src/main/scala")),
      unmanagedSourceDirectories in Test <+= baseDirectory(new File(_, "src/test/scala")),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % "2.10.2",
        "biz.aQute" % "bndlib" % "1.50.0"
      ),
      retrieveManaged := true
    ),
    aggregate = Seq(sub, javaProject, scalaProject)
  )

  lazy val sub: Project = Project(
    "sub",
    new File("sub"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "biz.aQute" % "bndlib" % "1.50.0",
        "javax.servlet" % "servlet-api" % "2.5",
        "javax.servlet" % "servlet-api" % "2.5" % "provided"
      ),
      retrieveManaged := true,
      EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE16),
      EclipseKeys.withSource := true
    ),
    aggregate = Seq(suba, subb, subc, subd, sube)
  )

  lazy val suba = Project(
    "suba",
    new File("sub/suba"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.1",
        "biz.aQute" % "bndlib" % "1.50.0",
        "org.specs2" %% "specs2" % "2.1.1" % "test"
      ),
      EclipseKeys.createSrc in Test := EclipseCreateSrc.ValueSet.empty,
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
      scalacOptions := Seq(
        "-Xelide-below", "1000",
        "-verbose",
        "-Xprompt",
        "-deprecation",
        "-unchecked"),
      EclipseKeys.configurations := Set(Configurations.Compile, Configurations.IntegrationTest)
    ),
    dependencies = Seq(suba, suba % "test->compile", subc % "test->test")
  )

  lazy val subc = {
	import com.typesafe.sbteclipse.core.Validation
	import scala.xml._
	import scala.xml.transform.RewriteRule
	import scalaz.Scalaz._
	import EclipseClasspathEntry.Lib
	import DefaultTransforms._
    Project(
      "subc",
      new File("sub/subc"),
      settings = Project.defaultSettings ++ Seq(
        libraryDependencies ++= Seq(
          "biz.aQute" % "bndlib" % "1.50.0"
        ),
        retrieveManaged := true,
        EclipseKeys.relativizeLibs := false,
        EclipseKeys.eclipseOutput := Some(".target"),
        EclipseKeys.classpathTransformerFactories ++= Seq(transformNode("classpath", Append(Lib("libs/my.jar")))),
        EclipseKeys.projectTransformerFactories ++= Seq(transformNode("projectDescription", Append(<foo bar="baz"/>)))
      )
    )
  }

  lazy val javaProject = Project(
    "java",
    new File("java"),
    settings = Project.defaultSettings ++ Seq(
      EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
    )
  )

  lazy val scalaProject = Project(
    "scala",
    new File("scala"),
    settings = Project.defaultSettings ++ Seq(
      EclipseKeys.projectFlavor := EclipseProjectFlavor.ScalaIDE
    )
  )

  lazy val subd = Project(
    id = "subd-id",
    base = new File("sub/subd"),
    settings = Project.defaultSettings ++ Seq(
      name := "subd",
      EclipseKeys.useProjectId := true
    )
  )

  lazy val sube = Project(
    id = "sube-id",
    base = new File("sub/sube"),
    settings = Project.defaultSettings ++ Seq(
      name := "sube"
    )
  )
}
