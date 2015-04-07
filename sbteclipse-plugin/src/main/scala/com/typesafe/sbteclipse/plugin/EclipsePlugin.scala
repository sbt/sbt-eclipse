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

import com.typesafe.sbteclipse.core.{ EclipsePlugin => CoreEclipsePlugin }
import sbt.{ AutoPlugin, Setting }

object EclipsePlugin extends AutoPlugin with CoreEclipsePlugin {

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  val autoImport = CoreEclipsePlugin

  override def projectSettings: Seq[Setting[_]] = CoreEclipsePlugin.eclipseSettings
  override def buildSettings: Seq[Setting[_]] = CoreEclipsePlugin.buildEclipseSettings

}
