package com.typesafe.sbteclipse.util

import util.control.Exception

case class ScalaVersion(era: Int, major: Int, minor: Int, qualifier: Option[String]) {
  def isScala210: Boolean = era == 2 && major == 10
}

object ScalaVersion {
  private val versionRegex = """(\d+)\.(\d+)\.(\d+)(-\S+)?""".r

  def parse(version: String): Option[ScalaVersion] = version match {
    case versionRegex(era, major, minor, qualifier) =>
      // if qualifier is not null, drop the leading "-"
      val qual = Option(qualifier).map(_.tail)
      Exception.failing(classOf[NumberFormatException]) {
        Some(ScalaVersion(era.toInt, major.toInt, minor.toInt, qual))
      }
    case _ => None
  }
}