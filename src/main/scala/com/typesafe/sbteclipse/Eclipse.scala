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

import EclipsePlugin.{ ClasspathEntry, EclipseExecutionEnvironment }
import java.io.FileWriter
import java.util.Properties
import sbt.{ Command, Configurations, File, IO, Keys, Project, ProjectRef, ResolvedProject, State, ThisBuild, richFile }
import sbt.CommandSupport.logger
import scala.collection.JavaConverters
import scala.xml.{ Elem, NodeSeq, PrettyPrinter }
import scalaz.{ Failure, Success }
import scalaz.Scalaz._

private object Eclipse {

  val SettingFormat = """-?([^:]*):?(.*)""".r

  def eclipseCommand(
    commandName: String,
    executionEnvironment: Option[EclipseExecutionEnvironment.Value],
    skipParents: Boolean,
    /*target: String,*/
    withSource: Boolean,
    classpathEntryCollector: PartialFunction[ClasspathEntry, ClasspathEntry]): Command =
    Command(commandName)(_ => parser)((state, args) =>
      action(executionEnvironment, skipParents, /*target,*/ withSource, classpathEntryCollector, args.toMap)(state)
    )

  def parser = {
    import EclipseOpts._
    (boolOpt(ExecutionEnvironment) | boolOpt(SkipParents) | boolOpt(WithSource)).*
  }

  def action(
    executionEnvironment: Option[EclipseExecutionEnvironment.Value],
    skipParents: Boolean,
    /*target: String,*/
    withSource: Boolean,
    classpathEntryCollector: PartialFunction[ClasspathEntry, ClasspathEntry],
    args: Map[String, Boolean])(
      implicit state: State) = {

    logger(state).info("About to create Eclipse project files for your project(s).")
    import EclipseOpts._
    val ee = args get ExecutionEnvironment getOrElse executionEnvironment
    val sp = args get SkipParents getOrElse skipParents
    val ws = args get WithSource getOrElse withSource

    contentsForAllProjects(sp, /*target,*/ classpathEntryCollector).fold(onFailure, onSuccess)
  }

  def contentsForAllProjects(
    skipParents: Boolean,
    /*target: String,*/
    classpathEntryCollector: PartialFunction[ClasspathEntry, ClasspathEntry])(
      implicit state: State) = {
    val contents = for {
      ref <- structure.allProjectRefs
      project <- Project.getProject(ref, structure) if project.aggregate.isEmpty || !skipParents
    } yield {
      (name(ref) |@|
        buildDirectory(ref) |@|
        baseDirectory(ref) |@|
        compileSrcDirectories(ref) |@|
        testSrcDirectories(ref) |@|
        scalacOptions(ref) |@|
        externalDependencies(ref) |@|
        projectDependencies(ref, project))(content( /*target,*/ classpathEntryCollector))
    }
    contents.sequence[ValidationNELS, Content]
  }

  def onFailure(errors: NELS)(implicit state: State) = {
    logger(state).error("Could not create Eclipse project files: %s" format (errors.list mkString ", "))
    state.fail
  }

  def onSuccess(contents: Seq[Content])(implicit state: State) = {
    if (contents.isEmpty)
      logger(state).warn("There was no project to create Eclipse project files for!")
    else {
      val names = contents map writeContent
      logger(state).info("Successfully created Eclipse project files for project(s): %s" format (names mkString ", "))
    }
    state
  }

  def content(
    /*target: String,*/
    classpathEntryCollector: PartialFunction[ClasspathEntry, ClasspathEntry])(
      name: String,
      buildDirectory: File,
      baseDirectory: File,
      compileSrcDirectories: (Seq[File], File),
      testSrcDirectories: (Seq[File], File),
      scalacOptions: Seq[(String, String)],
      externalDependencies: Seq[File],
      projectDependencies: Seq[String])(
        implicit state: State) =
    Content(
      name,
      baseDirectory,
      projectXml(name),
      classpath(
        classpathEntryCollector,
        buildDirectory,
        baseDirectory,
        compileSrcDirectories,
        testSrcDirectories,
        externalDependencies,
        projectDependencies
      ),
      scalacOptions)

  def projectXml(name: String) =
    <projectDescription>
      <name>{ name }</name>
      <buildSpec>
        <buildCommand>
          <name>org.scala-ide.sdt.core.scalabuilder</name>
        </buildCommand>
      </buildSpec>
      <natures>
        <nature>org.scala-ide.sdt.core.scalanature</nature>
        <nature>org.eclipse.jdt.core.javanature</nature>
      </natures>
    </projectDescription>

  def classpath(
    classpathEntryCollector: PartialFunction[ClasspathEntry, ClasspathEntry],
    buildDirectory: File,
    baseDirectory: File,
    compileSrcDirectories: (Seq[File], File),
    testSrcDirectories: (Seq[File], File),
    externalDependencies: Seq[File],
    projectDependencies: Seq[String])(
      implicit state: State) = {
    val entries =
      Seq(
        compileSrcDirectories._1 flatMap srcEntry(baseDirectory, compileSrcDirectories._2),
        testSrcDirectories._1 flatMap srcEntry(baseDirectory, testSrcDirectories._2),
        externalDependencies map libEntry(buildDirectory, true),
        projectDependencies map ClasspathEntry.Project,
        Seq("org.eclipse.jdt.launching.JRE_CONTAINER") map ClasspathEntry.Con, // TODO Optionally use execution env!
        Seq(output(baseDirectory, compileSrcDirectories._2)) map ClasspathEntry.Output
      ).flatten collect classpathEntryCollector map (_.toXml)
    <classpath>{ entries }</classpath>
  }

  def srcEntry(baseDirectory: File, classDirectory: File)(srcDirectory: File)(implicit state: State) =
    if (srcDirectory.exists()) {
      logger(state).debug("Creating src entry for directory '%s'." format srcDirectory)
      Some(ClasspathEntry.Src(relativize(baseDirectory, srcDirectory), output(baseDirectory, classDirectory)))
    } else {
      logger(state).debug("Skipping src entry for not-existing directory '%s'." format srcDirectory)
      None
    }

  def libEntry(buildDirectory: File, retrieveManaged: Boolean)(file: File)(implicit state: State) =
    ClasspathEntry.Lib(
      if (retrieveManaged)
        IO.relativize(buildDirectory, file) getOrElse file.getAbsolutePath
      else
        file.getAbsolutePath
    )

  // Getting and transforming settings and task results

  def name(ref: ProjectRef)(implicit state: State) =
    setting(Keys.name, ref)

  def buildDirectory(ref: ProjectRef)(implicit state: State) =
    setting(Keys.baseDirectory, ThisBuild)

  def baseDirectory(ref: ProjectRef)(implicit state: State) =
    setting(Keys.baseDirectory, ref)

  def compileSrcDirectories(ref: ProjectRef)(implicit state: State) =
    (setting(Keys.sourceDirectories, ref) |@|
      setting(Keys.resourceDirectories, ref) |@|
      setting(Keys.classDirectory, ref))(srcDirsToOutput)

  def testSrcDirectories(ref: ProjectRef)(implicit state: State) =
    (setting(Keys.sourceDirectories, ref, Configurations.Test) |@|
      setting(Keys.resourceDirectories, ref, Configurations.Test) |@|
      setting(Keys.classDirectory, ref, Configurations.Test))(srcDirsToOutput)

  def scalacOptions(ref: ProjectRef)(implicit state: State) =
    evaluateTask(Keys.scalacOptions, ref) map { options =>
      def values(value: String) =
        value split "," map (_.trim) filterNot (_ contains "org.scala-lang.plugins/continuations")
      options collect {
        case SettingFormat(key, value) if key == "Xplugin" && !values(value).isEmpty =>
          key -> (values(value) mkString ",")
        case SettingFormat(key, value) if key != "Xplugin" =>
          key -> (if (!value.isEmpty) value else "true")
      } match {
        case Nil => Nil
        case os => ("scala.compiler.useProjectSettings" -> "true") +: os
      }
    }

  def externalDependencies(ref: ProjectRef)(implicit state: State) =
    evaluateTask(Keys.externalDependencyClasspath, ref, Configurations.Test) map (_.files)

  def projectDependencies(ref: ProjectRef, project: ResolvedProject)(implicit state: State) = {
    val projectDependencies = project.dependencies map (dependency => setting(Keys.name, dependency.project))
    projectDependencies.distinct.sequence
  }

  // Writing to disk

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

  private def saveProperties(file: File, settings: Seq[(String, String)]): Unit = {
    file.getParentFile.mkdirs()
    val out = new FileWriter(file)
    val properties = new Properties
    for ((key, value) <- settings) properties.setProperty(key, value)
    try properties.store(out, "Generated by sbteclipse")
    finally if (out != null) out.close()
  }

  // Utilities

  def srcDirsToOutput(sourceDirectories: Seq[File], resourceDirectories: Seq[File], output: File) =
    (sourceDirectories ++ resourceDirectories).distinct -> output

  def relativize(baseDirectory: File, file: File) = IO.relativize(baseDirectory, file).get

  def output(baseDirectory: File, classDirectory: File) = relativize(baseDirectory, classDirectory)
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
  scalacOptions: Seq[(String, String)])
