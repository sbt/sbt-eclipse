import scala.xml.XML
import sys.error

EclipseKeys.withJavadoc := false

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.0.1",
  "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0" withSources(),
  "org.specs2" % "specs2-core_2.12" % "3.9.4" % "test" withSources()
)

TaskKey[Unit]("verify-classpath-xml") := {
  val dir = baseDirectory.value
  val home = System.getProperty("user.home")
  val classpath = XML.loadFile(dir / ".classpath")
  // lib entries with sources
  def verifySrcClasspathEntry(libPath: String, srcPath: String) =
    if (!(classpath.child contains <classpathentry kind="lib" path={ libPath } sourcepath={ srcPath } />))
      error("""Expected .classpath of project to contain <classpathentry kind="lib" path="%s" sourcepath="%s" />: %s""".format(libPath, srcPath, classpath))
  verifySrcClasspathEntry(home + "/.ivy2/cache/ch.qos.logback/logback-classic/jars/logback-classic-1.0.1.jar", home + "/.ivy2/cache/ch.qos.logback/logback-classic/srcs/logback-classic-1.0.1-sources.jar")
  verifySrcClasspathEntry(home + "/.ivy2/cache/biz.aQute.bnd/biz.aQute.bndlib/jars/biz.aQute.bndlib-3.4.0.jar", home + "/.ivy2/cache/biz.aQute.bnd/biz.aQute.bndlib/srcs/biz.aQute.bndlib-3.4.0-sources.jar")
  verifySrcClasspathEntry(home + "/.ivy2/cache/org.specs2/specs2-core_2.12/jars/specs2-core_2.12-3.9.4.jar", home + "/.ivy2/cache/org.specs2/specs2-core_2.12/srcs/specs2-core_2.12-3.9.4-sources.jar")
}
