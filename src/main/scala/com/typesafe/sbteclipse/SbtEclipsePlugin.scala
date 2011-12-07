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

package com.typesafe.sbteclipse

import sbt.{ Command, File, Plugin, Setting, SettingKey, State }
import sbt.Keys.{ baseDirectory, commands }
import sbt.CommandSupport.logger

object EclipsePlugin extends Plugin {

  override def settings: Seq[Setting[_]] = {
    import EclipseKeys._
    Seq(
      commandName := "eclipse",
      executionEnvironment := None,
      target <<= baseDirectory(new File(_, ".target")),
      skipParents := true,
      withSource := false,
      commands <+= (commandName, executionEnvironment, skipParents, target, withSource)(eclipseCommand)
    )
  }

  protected def eclipseCommand(
    commandName: String,
    executionEnvironment: Option[EclipseExecutionEnvironment.Value],
    skipParents: Boolean,
    target: File,
    withSource: Boolean): Command =
    Command(commandName)(_ => parser)((state, args) =>
      action(executionEnvironment, skipParents, target, withSource, args.toMap)(state)
    )

  private def parser = {
    import EclipseOpts._
    (boolOpt(ExecutionEnvironment) | boolOpt(SkipParents) | boolOpt(WithSource)).*
  }

  private def action(
    executionEnvironment: Option[EclipseExecutionEnvironment.Value],
    skipParents: Boolean,
    target: File,
    withSource: Boolean,
    args: Map[String, Boolean])(implicit state: State) = {

    logger(state).info("About to create Eclipse project files for your project(s).")
    import EclipseOpts._
    val ee = args get ExecutionEnvironment getOrElse executionEnvironment
    val sp = args get SkipParents getOrElse skipParents
    val ws = args get WithSource getOrElse withSource

    state
  }

  object EclipseKeys {
    import EclipseOpts._

    val commandName: SettingKey[String] =
      SettingKey[String](
        prefix("command-name"),
        "The name of the command.")

    val executionEnvironment: SettingKey[Option[EclipseExecutionEnvironment.Value]] =
      SettingKey[Option[EclipseExecutionEnvironment.Value]](
        prefix(ExecutionEnvironment),
        "The optional Eclipse execution environment.")

    val skipParents: SettingKey[Boolean] =
      SettingKey[Boolean](
        prefix(SkipParents),
        "Skip creating Eclipse files for parent project?")

    val target: SettingKey[File] =
      SettingKey[File](
        prefix("target"),
        "The target directory for Eclipse.")

    val withSource: SettingKey[Boolean] =
      SettingKey[Boolean](
        prefix(WithSource),
        "Download and link sources for library dependencies?")

    private def prefix(key: String) = "eclipse-" + key
  }

  object EclipseExecutionEnvironment extends Enumeration {

    val JavaSE17 = Value("JavaSE-1.7")

    val JavaSE16 = Value("JavaSE-1.6")

    val J2SE15 = Value("J2SE-1.5")

    val J2SE14 = Value("J2SE-1.4")

    val J2SE13 = Value("J2SE-1.3")

    val J2SE12 = Value("J2SE-1.2")

    val JRE11 = Value("JRE-1.1")
  }

  private object EclipseOpts {

    val ExecutionEnvironment = "execution-environment"

    val SkipParents = "skip-parents"

    val WithSource = "with-source"
  }
}
