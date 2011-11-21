import sbt._
import sbt.Keys._

object Build extends Build {

  // Dependencies
  val specs2 = "org.specs2" %% "specs2" % "1.5"
  val specs2Test = "org.specs2" %% "specs2" % "1.5" % "test"
  val slf4s = "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7"

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
      settings = commonSettings,
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
}
