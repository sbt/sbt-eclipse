import scala.xml.XML

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

autoScalaLibrary := false

TaskKey[Unit]("verify-project-xml") <<= baseDirectory map { dir =>
  val projectDescription = XML.loadFile(dir / ".project")
  // verifier method
  def verify[A](name: String, expected: A, actual: A) =
    if (actual != expected) error("Expected .project to contain %s '%s', but was '%s'!".format(name, expected, actual))
  def verifyNot[A](name: String, expected: A, actual: => A) = {
    try if (actual != expected) error("Expected .project to contain %s '%s', but was '%s'!".format(name, expected, actual))
    catch {
      case _: RuntimeException => () // this is expected and means the test was OK
    }
  }
  // java project nature
  verify("buildCommand", "org.eclipse.jdt.core.javabuilder", (projectDescription \ "buildSpec" \ "buildCommand" \ "name").text)
  verify("natures", "org.eclipse.jdt.core.javanature", (projectDescription \ "natures" \ "nature").text)
  // no scala nature for pure java project
  verifyNot("buildCommand", "org.scala-ide.sdt.core.scalabuilder", (projectDescription \ "buildSpec" \ "buildCommand" \ "name").text)
  verifyNot("natures", "org.scala-ide.sdt.core.scalanature", (projectDescription \ "natures" \ "nature").text)
}