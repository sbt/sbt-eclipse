package com.typesafe.sbteclipse.plugin

import sbt.{Setting, AutoPlugin}

import com.typesafe.sbteclipse.core.{EclipsePlugin=>EclipseCorePlugin}

import scala.xml.Node

/** Migrates eclipse to be an autoplugin. */
object EclipsePlugin extends AutoPlugin {
  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements
  // Auto import semantics users expect today.
  val autoImport: EclipseCorePlugin.type = EclipseCorePlugin
  override def projectSettings: Seq[Setting[_]] = EclipseCorePlugin.eclipseSettings
  override def buildSettings: Seq[Setting[_]] = EclipseCorePlugin.buildEclipseSettings
  // Alias for existing things.
  val EclipseKeys = EclipseCorePlugin.EclipseKeys
  val EclipseProjectFlavor = EclipseCorePlugin.EclipseProjectFlavor
  val EclipseCreateSrc = EclipseCorePlugin.EclipseCreateSrc
  val EclipseExecutionEnvironment = EclipseCorePlugin.EclipseExecutionEnvironment
  val EclipseClasspathEntry = EclipseCorePlugin.EclipseClasspathEntry
  val DefaultTransforms = EclipseCorePlugin.DefaultTransforms
  def transformNode(parentName: String, transform: Seq[Node] => Seq[Node]) = EclipseCorePlugin.transformNode(parentName, transform)
}
