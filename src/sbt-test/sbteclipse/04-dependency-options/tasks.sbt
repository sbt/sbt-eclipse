import scala.xml.XML
import sys.error

(ThisBuild / EclipseKeys.skipParents) := false
(ThisBuild / EclipseKeys.withSource) := false
(ThisBuild / EclipseKeys.withJavadoc) := false

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

def artifactHome = {
  val home = System.getProperty("user.home")
  val osName = sys.props.get("os.name")
  home + (osName match {
    case Some(os) if os.toLowerCase.contains("mac") => "/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2"
    case Some(os) if os.toLowerCase.contains("win") => "\\AppData\\Local\\Coursier\\cache\\v1\\https\\repo1.maven.org\\maven2"
    case _ => "/.cache/coursier/v1/https/repo1.maven.org/maven2"
  })
}

TaskKey[Unit]("verify-classpath-xml-suba") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / "suba" / ".classpath")
  // lib entries no sources or javadoc
  if ((classpath \ "classpathentry") exists { node => node.attribute("sourcepath").isDefined })
    error("""Expected .classpath of suba project to not contain the sourcepath for any dependencies: %s""" format classpath)
  if ((classpath \ "classpathentry" \ "attributes") exists { node => (node \ "@name").text == "javadoc_location"})
    error("""Expected .classpath of suba project to not contain the javadoc for any dependencies: %s""" format classpath)
  def verifyClasspathEntry(libPath: String) =
    if (!(classpath.child contains <classpathentry kind="lib" path={ libPath } />))
      error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path="%s" />: %s""".format(libPath, classpath))
  verifyClasspathEntry(artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0.jar")
  verifyClasspathEntry(artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4.jar")
}

TaskKey[Unit]("verify-classpath-xml-subb") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / "subb" / ".classpath")
  // lib entries sources no javadoc
  if ((classpath \ "classpathentry" \ "attributes") exists { node => (node \ "@name").text == "javadoc_location"})
    error("""Expected .classpath of subb project to not contain the javadoc for any dependencies: %s""" format classpath)
  def verifySrcClasspathEntry(libPath: String, srcPath: String) =
    if (!(classpath.child contains <classpathentry kind="lib" path={ libPath } sourcepath={ srcPath } />))
      error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="%s" sourcepath="%s" />: %s""".format(libPath, srcPath, classpath))
  verifySrcClasspathEntry(artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0.jar", artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0-sources.jar")
  verifySrcClasspathEntry(artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4.jar", artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4-sources.jar")
}

TaskKey[Unit]("verify-classpath-xml-subc") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / "subc" / ".classpath")
  // lib entries javadoc no sources
  if ((classpath \ "classpathentry") exists { node => node.attribute("sourcepath").isDefined })
    error("""Expected .classpath of subc project to not contain the sourcepath for any dependencies: %s""" format classpath)
  def verifyJavadocClasspathEntry(libPath: String, javadocPath: String) = {
    val javadocValue = "jar:file:" + javadocPath + "!/"
    val libEntry = classpath.child filter { node => (node \ "@path").text == libPath }
    if (!((libEntry \ "attributes" \ "attribute") exists { node => (node \ "@name").text == "javadoc_location" && (node \ "@value").text == javadocValue }))
      error("""Expected .classpath of subc project to contain <classpathentry kind="lib" path="%s"><attributes><attribute name="javadoc_location" value="%s"/></attributes>: %s""".format(libPath, javadocValue, classpath))
  }
  verifyJavadocClasspathEntry(artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0.jar", artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0-javadoc.jar")
  verifyJavadocClasspathEntry(artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4.jar", artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4-javadoc.jar")
}

TaskKey[Unit]("verify-classpath-xml-subd") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / "subd" / ".classpath")
  // lib entries sources and javadoc
  def verifySrcAndJavadocClasspathEntry(libPath: String, srcPath: String, javadocPath: String) = {
    val javadocValue = "jar:file:" + javadocPath + "!/"
    val libEntry = classpath.child filter { node => (node \ "@path").text == libPath && (node \ "@sourcepath").text == srcPath }
    if (!((libEntry \ "attributes" \ "attribute") exists { node => (node \ "@name").text == "javadoc_location" && (node \ "@value").text == javadocValue }))
      error("""Expected .classpath of subd project to contain <classpathentry kind="lib" path="%s" sourcepath="%s"><attributes><attribute name="javadoc_location" value="%s"/></attributes>: %s""".format(libPath, srcPath, javadocValue, classpath))
  }
  verifySrcAndJavadocClasspathEntry(artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0.jar", artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0-sources.jar", artifactHome + "/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0-javadoc.jar")
  verifySrcAndJavadocClasspathEntry(artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4.jar", artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4-sources.jar", artifactHome + "/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4-javadoc.jar")
}
