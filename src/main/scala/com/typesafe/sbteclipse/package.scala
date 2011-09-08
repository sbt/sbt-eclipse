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

package com.typesafe

import sbt._
import sbt.CommandSupport.logger
import scalaz.{ NonEmptyList, Validation }
import scalaz.Scalaz._

package object sbteclipse {

  def extracted(implicit state: State) = Project extract state

  def structure(implicit state: State) = extracted.structure

  def setting[A](key: SettingKey[A], 
      errorMessage: => String,
      projectReference: ProjectReference,
      configuration: Configuration = Configurations.Compile)(
      implicit state: State): Validation[NonEmptyList[String], A] = {
    key in (projectReference, configuration) get structure.data match {
      case Some(a) =>
        logDebug("Setting for key %s = %s".format(key.key, a))
        a.success
      case None => errorMessage.failNel
    }
  }

  def evaluateTask[A](taskKey: ScopedKey[Task[A]], ref: ProjectRef)(implicit state: State): Option[Result[A]] = 
    EvaluateTask.evaluateTask(structure, taskKey, state, ref, false, EvaluateTask.SystemProcessors)

  def isParentProject(project: ResolvedProject): Boolean = !project.aggregate.isEmpty

  def isRootProject(projectRef: ProjectRef)(implicit state: State): Boolean = 
    projectRef.project == (extracted rootProject projectRef.build)

  def logDebug(message: => String)(implicit state: State): Unit = logger(state).debug(message)

  def logInfo(message: => String)(implicit state: State): Unit = logger(state).info(message)

  def logWarn(message: => String)(implicit state: State): Unit = logger(state).warn(message)

  def logError(message: => String)(implicit state: State): Unit = logger(state).error(message)
}
