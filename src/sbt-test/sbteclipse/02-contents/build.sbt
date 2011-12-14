import java.io.FileInputStream
import java.util.Properties
import scala.collection.JavaConverters._
import scala.xml.XML

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

TaskKey[Unit]("verify-project-xml") <<= baseDirectory map { dir =>
  val projectDescription = XML.loadFile(new File(dir, ".project"))
  val name = (projectDescription \ "name").text
  if (name != "sbteclipse-test")
    error("Expected .project to contain name '%s', but was '%s'!".format("sbteclipse-test", name))
}

TaskKey[Unit]("verify-settings") <<= baseDirectory map { dir =>
  val settings = {
    val p = new Properties 
    p.load(new FileInputStream(new File(dir, "sub/subb/.settings/org.scala-ide.sdt.core.prefs")))
    p.asScala.toMap
  }
  val expected = Map(
    "scala.compiler.useProjectSettings" -> "true", 
    "unchecked" -> "true", 
    "deprecation" -> "true"
  ) 
  if (settings != expected) error("Expected settings to be '%s', but was '%s'!".format(expected, settings))
}
