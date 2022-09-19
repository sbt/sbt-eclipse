import scala.xml.XML
import sbtprotobuf.ProtobufPlugin
import sys.error

organization := "com.github.sbt"

name := "sbteclipse-test"

version := "1.2.3"

libraryDependencies += "com.google.protobuf" % "protobuf-java" % "3.3.1"

enablePlugins(ProtobufPlugin)
seq(ProtobufPlugin.protobufSettings: _*)

seq(Twirl.settings: _*)

TaskKey[Unit]("verify-valid") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / ".classpath")
  val srcManaged = """^target/scala-([0-9.]+)/src_managed/main$""".r
  val managedSrcRoot = (classpath \ "classpathentry") find (node => node.attributes("path").exists(_.text.matches(srcManaged.toString)))
  if (managedSrcRoot.isEmpty)
    error("""Expected .classpath to contain an entry for src_managed/main""")
  val excludes = managedSrcRoot.flatMap(_.attribute("excluding")).map(_.text).map(_.split("""\|"""))
  if (excludes.isEmpty)
    error("""Expected classpathentry src_managed/main to contain an excluding attribute""")
  val separator = java.io.File.separator
  val protobufFolder = "compiled_protobuf"
  val twirlFolder = "generated-twirl-sources"
  Seq(protobufFolder, twirlFolder) foreach { folder =>
    if (!excludes.exists(_.contains(folder + separator)))
      error("""Expected classpathentry src_managed/main to exclude %s""" format folder)
  }
}
