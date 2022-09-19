(ThisBuild / EclipseKeys.skipParents) := false
(ThisBuild / EclipseKeys.withSource) := false
(ThisBuild / EclipseKeys.withJavadoc) := false

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

lazy val root =
  Project("root", new File(".")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      (Compile / unmanagedSourceDirectories) += { baseDirectory(new File(_, "src/main/scala")).value },
      (Test / unmanagedSourceDirectories) += { baseDirectory(new File(_, "src/test/scala")).value },
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % "2.12.17",
        "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0"
      ),
      retrieveManaged := true
    )
  ).
  aggregate(sub, javaProject, scalaProject)

lazy val sub: Project =
  Project("sub", new File("sub")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0",
        "javax.servlet" % "servlet-api" % "2.5",
        "javax.servlet" % "servlet-api" % "2.5" % "provided"
      ),
      retrieveManaged := true,
      EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE16),
      EclipseKeys.withSource := true
    )
  ).
  aggregate(suba, subb, subc, subd, sube)

lazy val suba =
  Project("suba", new File("sub/suba")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.1",
        "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0",
        "org.specs2" % "specs2-core_2.12" % "3.9.4" % "test"
      ),
      (Test / EclipseKeys.createSrc) := EclipseCreateSrc.ValueSet.empty,
      EclipseKeys.withSource := true
    )
  )

lazy val subb =
  Project("subb", new File("sub/subb")).
  configs(Configurations.IntegrationTest).
  settings(
    Defaults.coreDefaultSettings ++ Defaults.itSettings ++ Seq(
      libraryDependencies ++= Seq(
        "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0",
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
    )
  ).
  dependsOn(suba, suba % "test->compile", subc % "test->test")

lazy val subc = {
  import com.typesafe.sbteclipse.core.Validation
  import scala.xml._
  import scala.xml.transform.RewriteRule
  import scalaz.Scalaz._
  import EclipseClasspathEntry.Lib
  import DefaultTransforms._

  Project("subc", new File("sub/subc")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0"
      ),
      retrieveManaged := true,
      EclipseKeys.relativizeLibs := false,
      EclipseKeys.eclipseOutput := Some(".target"),
      EclipseKeys.classpathTransformerFactories ++= Seq(transformNode("classpath", Append(Lib("libs/my.jar")))),
      EclipseKeys.projectTransformerFactories ++= Seq(transformNode("projectDescription", Append(<foo bar="baz"/>)))
    )
  )
}

lazy val javaProject =
  Project("java", new File("java")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
    )
  )

lazy val scalaProject =
  Project("scala", new File("scala")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      EclipseKeys.projectFlavor := EclipseProjectFlavor.ScalaIDE
    )
  )

lazy val subd =
  Project("subd-id", new File("sub/subd")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      name := "subd",
      EclipseKeys.useProjectId := true
    )
  )

lazy val sube =
  Project("sube-id", new File("sub/sube")).
  settings(
    Defaults.coreDefaultSettings ++ Seq(
      name := "sube"
    )
  )
