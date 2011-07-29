import sbt._
import sbt.Keys._

object Build extends Build {

  // Dependencies
  val specs2 = "org.specs2" %% "specs2" % "1.5" % "test"

  // Settings
  val commonSettings = Defaults.defaultSettings ++ Seq(
      organization := "localhost",
      scalaVersion := "2.9.0-1",
      libraryDependencies ++= Seq(specs2),
      shellPrompt := { "sbt (%s)> " format projectId(_) })
  val slf4sDependency = libraryDependencies += "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.6"

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
      settings = commonSettings :+ slf4sDependency,
      dependencies = Seq(sub1))
  lazy val sub12: Project = Project("sub12",
      file("sub1/sub12"),
      settings = commonSettings,
      dependencies = Seq(sub1, sub11))

  // Helpers
  def projectId(state: State) = extracted(state).currentProject.id
  def extracted(state: State) = Project extract state
}
