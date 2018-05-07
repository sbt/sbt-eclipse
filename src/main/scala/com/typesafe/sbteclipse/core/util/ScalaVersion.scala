package com.typesafe.sbteclipse.core.util

import util.control.Exception

private[core] trait ScalaVersion {
  def settingsFrom(currentSettings: Map[String, String]): Map[String, String]
}

private[core] case object NoScalaVersion extends ScalaVersion {
  def settingsFrom(currentSettings: Map[String, String]): Map[String, String] = currentSettings
}

private[core] object ScalaVersion {
  private val versionRegex = """(\d+)\.(\d+)\.(\d+)(-\S+)?""".r

  def parse(version: String): ScalaVersion = version match {
    case versionRegex(era, major, minor, qualifier) =>
      // if qualifier exists (i.e., is not null), drop the leading '-'
      val qual = Option(qualifier).map(_.tail)
      Exception.failAsValue(classOf[NumberFormatException])(NoScalaVersion) {
        FullScalaVersion(era.toInt, major.toInt, minor.toInt, qual)
      }
    case _ => NoScalaVersion
  }

  private[core] case class FullScalaVersion(era: Int, major: Int, minor: Int, qualifier: Option[String]) extends ScalaVersion {
    private def isScala210: Boolean = era == 2 && major == 10

    def settingsFrom(currentSettings: Map[String, String]): Map[String, String] = {
      // If `version` is Scala 2.10, returns the `settings` with the required additional parameters for enabling the Scala 2.10 support in Scala IDE 4.0+.
      // Otherwise, returns `settings` unchanged.
      if (isScala210) {
        val key = "scala.compiler.additionalParams"
        val newValue = (currentSettings.getOrElse(key, "") + " -Xsource:2.10 -Ymacro-expand:none").trim()
        currentSettings + (key -> newValue) + ("scala.compiler.installation" -> "2.10")
      } else currentSettings
    }
  }
}