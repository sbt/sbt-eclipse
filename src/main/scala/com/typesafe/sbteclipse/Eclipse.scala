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

import EclipsePlugin.EclipseExecutionEnvironment
import java.io.FileWriter
import java.util.Properties
import sbt.{ Command, Configurations, File, Keys, Project, ProjectRef, State, richFile }
import sbt.CommandSupport.logger
import scala.collection.JavaConverters
import scala.xml.{ Elem, PrettyPrinter }
import scalaz.{ Failure, Success }
import scalaz.Scalaz._

private object Eclipse {

  val SettingFormat = """-?([^:]*):?(.*)""".r

  def eclipseCommand(
    commandName: String,
    executionEnvironment: Option[EclipseExecutionEnvironment.Value],
    skipParents: Boolean,
    target: File,
    withSource: Boolean): Command =
    Command(commandName)(_ => parser)((state, args) =>
      action(executionEnvironment, skipParents, target, withSource, args.toMap)(state)
    )

  def parser = {
    import EclipseOpts._
    (boolOpt(ExecutionEnvironment) | boolOpt(SkipParents) | boolOpt(WithSource)).*
  }

  def action(
    executionEnvironment: Option[EclipseExecutionEnvironment.Value],
    skipParents: Boolean,
    target: File,
    withSource: Boolean,
    args: Map[String, Boolean])(
      implicit state: State) = {

    logger(state).info("About to create Eclipse project files for your project(s).")
    import EclipseOpts._
    val ee = args get ExecutionEnvironment getOrElse executionEnvironment
    val sp = args get SkipParents getOrElse skipParents
    val ws = args get WithSource getOrElse withSource

    contentsForAllProjects(sp).fold(onFailure, onSuccess)
  }

  def onFailure(errors: NELS)(implicit state: State) = {
    logger(state).error(errors.list mkString ", ")
    state.fail
  }

  def onSuccess(contents: Seq[Content])(implicit state: State) = {
    if (contents.isEmpty)
      logger(state).warn("There was no project to create Eclipse project files for!")
    else {
      val names = contents map writeContent
      logger(state).info("Successfully created Eclipse project files for %s" format (names mkString ", "))
    }
    state
  }

  def contentsForAllProjects(skipParents: Boolean)(implicit state: State) = {
    val contents = for {
      ref <- structure.allProjectRefs
      project <- Project.getProject(ref, structure) if project.aggregate.isEmpty || !skipParents
    } yield {
      (name(ref) |@| baseDirectory(ref) |@| scalacOptions(ref))((name, baseDirectory, scalacOptions) =>
        Content(name, baseDirectory, <project/>, <classpath/>, scalacOptions map settingToPair toMap)
      )
    }
    contents.sequence[ValidationNELS, Content]
  }

  def name(ref: ProjectRef)(implicit state: State) =
    setting(Keys.name, ref)

  def baseDirectory(ref: ProjectRef)(implicit state: State) =
    setting(Keys.baseDirectory, ref)

  def scalacOptions(ref: ProjectRef)(implicit state: State) =
    evaluateTask(Keys.scalacOptions, ref) map (options =>
      if (options.isEmpty) options
      else ("scala.compiler.useProjectSettings" +: options)
    )

  def writeContent(contents: Content): String = {
    saveXml(contents.dir / ".project", contents.project)
    saveXml(contents.dir / ".classpath", contents.classpath)
    if (!contents.scalacOptions.isEmpty)
      saveProperties(contents.dir / ".settings" / "org.scala-ide.sdt.core.prefs", contents.scalacOptions)
    contents.name
  }

  def saveXml(file: File, xml: Elem) = {
    val out = new FileWriter(file)
    try out.write(new PrettyPrinter(999, 2) format xml)
    finally if (out != null) out.close()
  }

  private def saveProperties(file: File, properties: Map[String, String]): Unit = {
    file.getParentFile.mkdirs()
    val out = new FileWriter(file)
    try properties.store(out, null)
    finally if (out != null) out.close()
  }

  def settingToPair(setting: String) = {
    val SettingFormat(key, value) = setting
    key -> (if (!value.isEmpty) value else "true")
  }
}

private object EclipseOpts {

  val ExecutionEnvironment = "execution-environment"

  val SkipParents = "skip-parents"

  val WithSource = "with-source"
}

private case class Content(
  name: String,
  dir: File,
  project: Elem,
  classpath: Elem,
  scalacOptions: Map[String, String])
