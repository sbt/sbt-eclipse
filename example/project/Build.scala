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

  // Projects
  lazy val root  = Project("root",  file("."),          settings = commonSettings)
  lazy val sub1  = Project("sub1",  file("sub1"),       settings = commonSettings) dependsOn(root)
  lazy val sub11 = Project("sub11", file("sub1/sub11"), settings = commonSettings) dependsOn(sub1)
  lazy val sub12 = Project("sub12", file("sub1/sub12"), settings = commonSettings) dependsOn(sub1, sub11)

  // Helpers
  def projectId(state: State) = extracted(state).currentProject.id
  def extracted(state: State) = Project extract state
}
