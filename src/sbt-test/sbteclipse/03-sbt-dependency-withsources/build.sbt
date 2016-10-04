import scala.xml.XML

EclipseKeys.withJavadoc := false

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.1"

libraryDependencies += "biz.aQute" % "bndlib" % "1.50.0" withSources()

libraryDependencies += "org.specs2" %% "specs2" % "2.1.1" % "test" withSources()

TaskKey[Unit]("verify-classpath-xml") <<= baseDirectory map { dir =>
  val home = System.getProperty("user.home")
  val classpath = XML.loadFile(dir / ".classpath")
  // lib entries with sources
  def verifySrcClasspathEntry(libPath: String, srcPath: String) =
    if (!(classpath.child contains <classpathentry kind="lib" path={ libPath } sourcepath={ srcPath } />))
      error("""Expected .classpath of project to contain <classpathentry kind="lib" path="%s" sourcepath="%s" />: %s""".format(libPath, srcPath, classpath))
  verifySrcClasspathEntry(home + "/.ivy2/cache/ch.qos.logback/logback-classic/jars/logback-classic-1.0.1.jar", home + "/.ivy2/cache/ch.qos.logback/logback-classic/srcs/logback-classic-1.0.1-sources.jar")
  verifySrcClasspathEntry(home + "/.ivy2/cache/biz.aQute/bndlib/jars/bndlib-1.50.0.jar", home + "/.ivy2/cache/biz.aQute/bndlib/srcs/bndlib-1.50.0-sources.jar")
  verifySrcClasspathEntry(home + "/.ivy2/cache/org.specs2/specs2_2.10/jars/specs2_2.10-2.1.1.jar", home + "/.ivy2/cache/org.specs2/specs2_2.10/srcs/specs2_2.10-2.1.1-sources.jar")
}
