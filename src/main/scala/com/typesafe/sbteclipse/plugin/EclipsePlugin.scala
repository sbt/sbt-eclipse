/*
 * Copyright 2011 Typesafe Inc.
 *
 * This work is based on the original contribution of WeigleWilczek.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  override def globalSettings: Seq[Setting[_]] = EclipseCorePlugin.globalEclipseSettings
  // Alias for existing things.
  val EclipseKeys = EclipseCorePlugin.EclipseKeys
  val EclipseProjectFlavor = EclipseCorePlugin.EclipseProjectFlavor
  val EclipseCreateSrc = EclipseCorePlugin.EclipseCreateSrc
  val EclipseExecutionEnvironment = EclipseCorePlugin.EclipseExecutionEnvironment
  val EclipseClasspathEntry = EclipseCorePlugin.EclipseClasspathEntry
  val DefaultTransforms = EclipseCorePlugin.DefaultTransforms
  def transformNode(parentName: String, transform: Seq[Node] => Seq[Node]) = EclipseCorePlugin.transformNode(parentName, transform)
}
