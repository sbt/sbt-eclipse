import java.io.{ File, FileInputStream }
import sbt._
import sbt.Keys._
import scala.xml.{ Elem, XML }

object Build extends Build {

  lazy val UserHome = System.getProperty("user.home")

  // Dependencies
  lazy val specs2 = "org.specs2" %% "specs2" % "1.6.1"
  lazy val specs2Test = "org.specs2" %% "specs2" % "1.6.1" % "test"
  lazy val slf4s = "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7"

  // Settings
  val commonSettings = Defaults.defaultSettings ++ Seq(
      organization := "localhost",
      scalaVersion := "2.9.1",
      libraryDependencies ++= Seq(specs2Test),
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      shellPrompt := { "sbt (%s)> " format projectId(_) })

  // Projects
  lazy val root: Project = Project("root",
      file("."),
      settings = commonSettings ++ Seq(
        verifyProjectFiles := verifyProjectFilesAction,
        verifyClasspathFiles := verifyClasspathFilesAction),
      aggregate = Seq(sub1))
  lazy val sub1: Project = Project("sub1", 
      file("sub1"),
      settings = commonSettings,
      dependencies = Seq(root),
      aggregate = Seq(sub11, sub12))
  lazy val sub11: Project = Project("sub11",
      file("sub1/sub11"),
      settings = commonSettings :+ (libraryDependencies += slf4s),
      dependencies = Seq(sub1))
  lazy val sub11Test = sub11 % "test->test"
  lazy val sub12: Project = Project("sub12",
      file("sub1/sub12"),
      settings = commonSettings :+ (libraryDependencies += specs2),
      dependencies = Seq(sub1, sub11, sub11Test))

  // Helpers
  def projectId(state: State) = extracted(state).currentProject.id
  def extracted(state: State) = Project extract state

  lazy val verifyProjectFiles = TaskKey[Unit]("verify-project-files")

  lazy val verifyClasspathFiles = TaskKey[Unit]("verify-classpath-files")

  def verifyProjectFilesAction: Unit = {
    val projectXml = XML.load(new FileInputStream(new File(root.base, ".project")))
    val name = (projectXml \ "name" map { _.text }).mkString
    assert(name == "root", "Expected name to be 'root', but was '%s'!" format name)
  }

  def verifyClasspathFilesAction: Unit = {
    def classpathXml(project: Project): Elem =
      XML.load(new FileInputStream(new File(project.base, ".classpath")))
    def assertContains(classpath: Elem, xml: Elem): Unit =
      assert((classpath \ "classpathentry") contains xml, "Missing %s!" format xml)
    def verifyCommonEntries(classpath: Elem) = {
      assertContains(classpath,
        <classpathentry output=".target/scala-2.9.1/classes" path="src/main/scala" kind="src"/>)
      assertContains(classpath,
        <classpathentry path={ UserHome + "/.ivy2/cache/org.specs2/specs2_2.9.1/jars/specs2_2.9.1-1.6.1.jar" } kind="lib"/>)
      assertContains(classpath,
        <classpathentry path={ UserHome + "/.ivy2/cache/org.specs2/specs2-scalaz-core_2.9.1/jars/specs2-scalaz-core_2.9.1-6.0.1.jar" } kind="lib"/>)
      assertContains(classpath,
        <classpathentry path="org.scala-ide.sdt.launching.SCALA_CONTAINER" kind="con"/>)
      assertContains(classpath,
        <classpathentry path="org.eclipse.jdt.launching.JRE_CONTAINER" kind="con"/>)
      assertContains(classpath,
        <classpathentry path=".target/scala-2.9.1/classes" kind="output"/>)
    }
    // root
    verifyCommonEntries(classpathXml(root))
    assertContains(classpathXml(root),
      <classpathentry path={ file(".").getCanonicalPath + "/lib/bndlib-1.43.0.jar" } kind="lib"/>)
    // sub1
    verifyCommonEntries(classpathXml(sub1))
    assertContains(classpathXml(sub1),
      <classpathentry exported="true" path="/root" kind="src" combineaccessrules="false"/>)
    // sub11
    verifyCommonEntries(classpathXml(sub11))
    assertContains(classpathXml(sub11),
      <classpathentry path={ UserHome + "/.ivy2/cache/org.slf4j/slf4j-api/jars/slf4j-api-1.6.1.jar" } kind="lib"/>)
    assertContains(classpathXml(sub11),
      <classpathentry exported="true" path="/sub1" kind="src" combineaccessrules="false"/>)
    // sub12
    verifyCommonEntries(classpathXml(sub12))
    assertContains(classpathXml(sub12),
      <classpathentry path={ UserHome + "/.ivy2/cache/org.slf4j/slf4j-api/jars/slf4j-api-1.6.1.jar" } kind="lib"/>)
    assertContains(classpathXml(sub12),
      <classpathentry exported="true" path="/sub1" kind="src" combineaccessrules="false"/>)
    assertContains(classpathXml(sub12),
      <classpathentry exported="true" path="/sub11" kind="src" combineaccessrules="false"/>)
  }
}
