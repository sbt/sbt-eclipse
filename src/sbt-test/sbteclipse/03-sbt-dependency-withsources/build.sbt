import scala.sys.error
import scala.xml.XML

EclipseKeys.withJavadoc := false

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.0.1",
  "biz.aQute.bnd" % "biz.aQute.bndlib" % "3.4.0" withSources(),
  "org.specs2" % "specs2-core_2.12" % "3.9.4" % "test" withSources()
)

def artifactHome = {
  val home = System.getProperty("user.home")
  val osName = sys.props.get("os.name")
  home + (osName match {
    case Some(os) if os.toLowerCase.contains("mac") => "/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2"
    case Some(os) if os.toLowerCase.contains("win") => "\\AppData\\Local\\Coursier\\cache\\v1\\https\\repo1.maven.org\\maven2"
    case _ => "/.cache/coursier/v1/https/repo1.maven.org/maven2"
  })
}

TaskKey[Unit]("verify-classpath-xml") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / ".classpath")

  // lib entries with sources
  def verifySrcClasspathEntry(libPath: String, srcPath: String) =
    if (!(classpath.child contains <classpathentry kind="lib" path={libPath} sourcepath={srcPath}/>))
      error("""Expected .classpath of project to contain <classpathentry kind="lib" path="%s" sourcepath="%s" />: %s""".format(libPath, srcPath, classpath))

  verifySrcClasspathEntry(artifactHome + "/ch/qos/logback/logback-classic/1.0.1/logback-classic-1.0.1.jar", artifactHome + "/ch/qos/logback/logback-classic/1.0.1/logback-classic-1.0.1-sources.jar")
  verifySrcClasspathEntry(artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0.jar", artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0-sources.jar")
  verifySrcClasspathEntry(artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4.jar", artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4-sources.jar")
}
