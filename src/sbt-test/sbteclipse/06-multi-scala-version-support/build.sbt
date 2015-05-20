import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties
import scala.collection.JavaConverters._

EclipseKeys.skipParents in ThisBuild := false

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

TaskKey[Unit]("verify-scala-settings-suba") <<= baseDirectory map { dir =>
  val settings = {
    val p = new Properties 
    p.load(new FileInputStream(dir / "suba/.settings/org.scala-ide.sdt.core.prefs"))
    p.asScala.toMap
  }
  val expected = Map(
    "scala.compiler.additionalParams" -> """-Xsource:2.10 -Ymacro-expand:none""",
    "scala.compiler.installation" -> "2.10",
    "scala.compiler.useProjectSettings" -> "true"
  )
  if (settings != expected) error("Expected settings to be '%s', but was '%s'!".format(expected, settings))
}

TaskKey[Unit]("verify-scala-settings-subb") <<= baseDirectory map { dir =>
  try {
    val settings = {
      val p = new Properties
      p.load(new FileInputStream(dir / "subb/.settings/org.scala-ide.sdt.core.prefs"))
      p.asScala.toMap
    }
    if (settings.nonEmpty) error("Expected settings to be empty, but was '%s'!".format(settings))
  } catch {
    // this is OK, for a Scala 2.11 project we don't need to set any special setting
    case e: FileNotFoundException => ()
  }
}
