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

import java.util.Properties
import sbt.{
  Configuration,
  Configurations,
  Extracted,
  EvaluateConfig,
  EvaluateTask,
  Inc,
  Incomplete,
  Project,
  ProjectRef,
  Reference,
  Result,
  TaskKey,
  SettingKey,
  State,
  Task,
  Value
}
import sbt.Load.BuildStructure
import sbt.complete.Parser
import scalaz.{ NonEmptyList, Validation }
import scalaz.Scalaz._

package object sbteclipse {

  def id[A](a: A): A = a

  def boolOpt(key: String): Parser[(String, Boolean)] = {
    import sbt.complete.DefaultParsers._
    (Space ~> key ~ ("=" ~> ("true" | "false"))) map { case (k, v) => k -> v.toBoolean }
  }

  def setting[A](
    key: SettingKey[A],
    reference: Reference,
    configuration: Configuration = Configurations.Default)(
      implicit state: State): ValidationNELS[A] = {
    key in (reference, configuration) get structure.data match {
      case Some(a) => a.success
      case None => "Missing setting '%s' for '%s'!".format(key.key, reference).failNel
    }
  }

  def evaluateTask[A](
    key: TaskKey[A],
    ref: ProjectRef,
    configuration: Configuration = Configurations.Compile)(
      implicit state: State): ValidationNELS[A] =
    EvaluateTask(structure, key in configuration, state, ref, EvaluateConfig(false)) match {
      case Some((_, Value(a))) => a.success
      case Some((_, Inc(inc))) => "Error evaluating task '%s': %s".format(key.key, Incomplete.show(inc.tpe)).failNel
      case None => "Missing task '%s' for '%s'!".format(key.key, ref.project).failNel
    }

  def extracted(implicit state: State): Extracted =
    Project.extract(state)

  def structure(implicit state: State): BuildStructure =
    extracted.structure

  type NELS = NonEmptyList[String]

  type ValidationNELS[A] = Validation[NELS, A]
}
