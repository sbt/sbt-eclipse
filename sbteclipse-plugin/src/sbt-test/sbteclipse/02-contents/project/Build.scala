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
        "org.scala-lang" % "scala-compiler" % "2.9.2",
        "biz.aQute" % "bndlib" % "1.50.0"
      ),
      retrieveManaged := true
    ),
    aggregate = Seq(sub, javaProject)
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
    aggregate = Seq(suba, subb, subc)
  )

  lazy val suba = Project(
    "suba",
    new File("sub/suba"),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.1",
        "biz.aQute" % "bndlib" % "1.50.0",
        "org.specs2" %% "specs2" % "1.9" % "test"
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
      scalacOptions := Seq("-unchecked", "-deprecation", "-Xelide-below", "0"),
      EclipseKeys.configurations := Set(Configurations.Compile, Configurations.IntegrationTest)
    ),
    dependencies = Seq(suba, suba % "test->compile", subc % "test->test")
  )

  lazy val subc = {
	import com.typesafe.sbteclipse.core.Validation
	import scala.xml._
	import scala.xml.transform.RewriteRule
	import scalaz.Scalaz._
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
        EclipseKeys.classpathTransformerFactories := Seq(new EclipseTransformerFactory[RewriteRule] {
          override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] = {
            val rule = new RewriteRule {
              override def transform(node: Node): Seq[Node] = node match {
                case elem if (elem.label == "classpath") =>
                  val newChild = elem.child ++ Elem(elem.prefix, "foo", Attribute("bar", Text("baz"), Null), elem.scope)
                  Elem(elem.prefix, "classpath", elem.attributes, elem.scope, newChild: _*)
                case other =>
                  other
              }
            }
            rule.success
          }
        }),
        EclipseKeys.projectTransformerFactories := Seq(new EclipseTransformerFactory[RewriteRule] {
          override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] = {
            val rule = new RewriteRule {
              override def transform(node: Node): Seq[Node] = node match {
                case elem if (elem.label == "projectDescription") =>
                  val newChild = elem.child ++ Elem(elem.prefix, "foo", Attribute("bar", Text("baz"), Null), elem.scope)
                  Elem(elem.prefix, "projectDescription", elem.attributes, elem.scope, newChild: _*)
                case other =>
                  other
              }
            }
            rule.success
          }
        })
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
}
