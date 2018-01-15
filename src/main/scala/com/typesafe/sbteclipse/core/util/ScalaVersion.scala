package com.typesafe.sbteclipse.core.util

import util.control.Exception

private[core] trait ScalaVersion {
  def settingsFrom(currentSettings: Map[String, String]): Map[String, String]
}

private[core] case object NoScalaVersion extends ScalaVersion {
  def settingsFrom(currentSettings: Map[String, String]): Map[String, String] = currentSettings
}

private[core] object ScalaVersion {
  private val installationRegex = """(\d+)\.(\d+)""".r
  private val versionRegex = """(\d+)\.(\d+)\.(\d+)(-\S+)?""".r

  def parse(installationVersion: String, projectVersion: String): ScalaVersion = (installationVersion, projectVersion) match {
    case (installationRegex(eraDefault, majorDefault), versionRegex(era, major, minor, qualifier)) =>
      // if qualifier exists (i.e., is not null), drop the leading '-'
      val qual = Option(qualifier).map(_.tail)
      Exception.failAsValue(classOf[NumberFormatException])(NoScalaVersion) {
        FullScalaVersion(eraDefault.toInt, majorDefault.toInt, era.toInt, major.toInt, minor.toInt, qual)
      }
    case _ => NoScalaVersion
  }

  private[core] case class FullScalaVersion(eraDefault: Int, majorDefault: Int, era: Int, major: Int, minor: Int, qualifier: Option[String]) extends ScalaVersion {
    private def isLowerVersionThanInstallation: Boolean = era < eraDefault || (era == eraDefault && major < majorDefault)

    def settingsFrom(currentSettings: Map[String, String]): Map[String, String] = {
      // If `version` is not the `workspace installation`, returns the `settings` with the required additional parameters for enabling a lower Scala version support in Scala IDE 4.0+.
      // Otherwise, returns `settings` unchanged.
      if (isLowerVersionThanInstallation) {
        val key = "scala.compiler.additionalParams"
        val installation = s"$era.$major"
        val newValue = (currentSettings.getOrElse(key, "") + s" -Xsource:$installation -Ymacro-expand:none").trim()
        currentSettings + (key -> newValue) + ("scala.compiler.installation" -> s"$installation")
      } else currentSettings
    }
  }
}