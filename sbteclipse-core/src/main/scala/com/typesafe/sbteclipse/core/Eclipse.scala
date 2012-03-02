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

package com.typesafe.sbteclipse.core

import EclipsePlugin.{
  EclipseClasspathEntry,
  EclipseClasspathEntryTransformerFactory,
  EclipseCreateSrc,
  EclipseExecutionEnvironment,
  EclipseKeys
}
import java.io.{ FileWriter, Writer }
import java.util.Properties
import sbt.{
  Attributed,
  Artifact,
  ClasspathDep,
  Classpaths,
  Command,
  Configuration,
  Configurations,
  File,
  IO,
  Keys,
  ModuleID,
  Project,
  ProjectRef,
  Reference,
  ResolvedProject,
  SettingKey,
  State,
  TaskKey,
  ThisBuild,
  UpdateReport,
  richFile
}
import sbt.complete.Parser
import scala.collection.JavaConverters
import scala.xml.{ Elem, NodeSeq, PrettyPrinter }
import scalaz.{ Failure, Success }
import scalaz.Scalaz._
import scalaz.effects._

private object Eclipse {

  val SettingFormat = """-([^:]*):?(.*)""".r

  val FileSep = System.getProperty("file.separator")

  val FileSepPattern = FileSep.replaceAll("""\\""", """\\\\""")

  val JreContainer = "org.eclipse.jdt.launching.JRE_CONTAINER"

  val StandardVmType = "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType"

  def eclipseCommand(commandName: String) =
    Command(commandName)(_ => parser)((state, args) => action(args.toMap)(state))

  def parser = {
    import EclipseOpts._
    (executionEnvironmentOpt | boolOpt(SkipParents) | boolOpt(WithSource)).*
  }

  def executionEnvironmentOpt: Parser[(String, EclipseExecutionEnvironment.Value)] = {
    import EclipseExecutionEnvironment._
    import EclipseOpts._
    import sbt.complete.DefaultParsers._
    val (head :: tail) = valueSeq map (_.toString)
    val executionEnvironments = tail.foldLeft(head: Parser[String])(_ | _)
    (Space ~> ExecutionEnvironment ~ ("=" ~> executionEnvironments)) map { case (k, v) => k -> withName(v) }
  }

  def action(args: Map[String, Any])(implicit state: State) = {
    state.log.info("About to create Eclipse project files for your project(s).")
    import EclipseOpts._
    effects(
      (args get ExecutionEnvironment).asInstanceOf[Option[EclipseExecutionEnvironment.Value]],
      (args get SkipParents).asInstanceOf[Option[Boolean]] getOrElse skipParents(ThisBuild),
      (args get WithSource).asInstanceOf[Option[Boolean]] // TODO Move to project level!
    ).fold(onFailure, onSuccess)
  }

  def effects(
    executionEnvironmentArg: Option[EclipseExecutionEnvironment.Value],
    skipParents: Boolean,
    withSourceArg: Option[Boolean])(
      implicit state: State) = {
    val effects = for {
      ref <- structure.allProjectRefs
      project <- Project.getProject(ref, structure) if project.aggregate.isEmpty || !skipParents
    } yield {
      val configs = configurations(ref)
      val applic = classpathEntryTransformerFactory(ref).createTransformer(ref, state) |@|
        name(ref) |@|
        buildDirectory |@|
        baseDirectory(ref) |@|
        mapConfigs(configs, srcDirectories(ref, createSrc(ref), eclipseOutput(ref))) |@|
        scalacOptions(ref) |@|
        mapConfigs(configs, externalDependencies(ref, withSourceArg getOrElse withSource(ref))) |@|
        mapConfigs(configs, projectDependencies(ref, project))
      applic(
        effect(
          jreContainer(executionEnvironmentArg orElse executionEnvironment(ref)),
          preTasks(ref),
          relativizeLibs(ref)
        )
      )
    }
    effects.sequence[ValidationNELS, IO[String]] map (_.sequence)
  }

  def onFailure(errors: NELS)(implicit state: State) = {
    state.log.error("Could not create Eclipse project files: %s" format (errors.list mkString ", "))
    state.fail
  }

  def onSuccess(effects: IO[Seq[String]])(implicit state: State) = {
    val names = effects.unsafePerformIO
    if (names.isEmpty)
      state.log.warn("There was no project to create Eclipse project files for!")
    else
      state.log.info("Successfully created Eclipse project files for project(s): %s" format (names mkString ", "))
    state
  }

  def mapConfigs[A](configurations: Seq[Configuration], f: Configuration => ValidationNELS[Seq[A]]) =
    (configurations map f).sequence map (_.flatten.distinct)

  def effect(
    jreContainer: String,
    preTasks: Seq[(TaskKey[_], ProjectRef)],
    relativizeLibs: Boolean)(
      classpathEntryTransformer: Seq[EclipseClasspathEntry] => Seq[EclipseClasspathEntry],
      name: String,
      buildDirectory: File,
      baseDirectory: File,
      srcDirectories: Seq[(File, File)],
      scalacOptions: Seq[(String, String)],
      externalDependencies: Seq[Lib],
      projectDependencies: Seq[String])(
        implicit state: State) = {
    for {
      _ <- executePreTasks(preTasks)
      n <- io(name)
      _ <- saveXml(baseDirectory / ".project", projectXml(name))
      cp <- classpath(
        classpathEntryTransformer,
        buildDirectory,
        baseDirectory,
        relativizeLibs,
        srcDirectories,
        externalDependencies,
        projectDependencies,
        jreContainer
      )
      _ <- saveXml(baseDirectory / ".classpath", cp)
      _ <- saveProperties(baseDirectory / ".settings" / "org.scala-ide.sdt.core.prefs", scalacOptions)
    } yield n
  }

  def executePreTasks(preTasks: Seq[(TaskKey[_], ProjectRef)])(implicit state: State) =
    io(for ((preTask, ref) <- preTasks) evaluateTask(preTask, ref)(state))

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
    classpathEntryTransformer: Seq[EclipseClasspathEntry] => Seq[EclipseClasspathEntry],
    buildDirectory: File,
    baseDirectory: File,
    relativizeLibs: Boolean,
    srcDirectories: Seq[(File, File)],
    externalDependencies: Seq[Lib],
    projectDependencies: Seq[String],
    jreContainer: String)(
      implicit state: State) = {
    val srcEntriesIoSeq = for ((dir, output) <- srcDirectories) yield srcEntry(baseDirectory, dir, output)
    for (srcEntries <- srcEntriesIoSeq.sequence) yield {
      val entries = srcEntries ++
        (externalDependencies map libEntry(buildDirectory, baseDirectory, relativizeLibs)) ++
        (projectDependencies map EclipseClasspathEntry.Project) ++
        (Seq(jreContainer) map EclipseClasspathEntry.Con) ++
        (Seq("bin") map EclipseClasspathEntry.Output)
      <classpath>{ classpathEntryTransformer(entries) map (_.toXml) }</classpath>
    }
  }

  def srcEntry(baseDirectory: File, srcDirectory: File, classDirectory: File)(implicit state: State) =
    io {
      if (!srcDirectory.exists()) srcDirectory.mkdirs()
      EclipseClasspathEntry.Src(
        relativize(baseDirectory, srcDirectory),
        relativize(baseDirectory, classDirectory)
      )
    }

  def libEntry(
    buildDirectory: File,
    baseDirectory: File,
    relativizeLibs: Boolean)(
      lib: Lib)(
        implicit state: State) = {
    def path(file: File) = {
      val relativizedBase =
        if (buildDirectory === baseDirectory) Some(".") else IO.relativize(buildDirectory, baseDirectory)
      val relativizedFile = IO.relativize(buildDirectory, file)
      val relativized = (relativizedBase |@| relativizedFile)((base, file) =>
        "%s%s%s".format(
          base split FileSepPattern map (part => if (part != ".") ".." else part) mkString FileSep,
          FileSep,
          file
        )
      )
      if (relativizeLibs) relativized getOrElse file.getAbsolutePath else file.getAbsolutePath
    }
    EclipseClasspathEntry.Lib(path(lib.binary), lib.source map path)
  }

  def jreContainer(executionEnvironment: Option[EclipseExecutionEnvironment.Value]) =
    executionEnvironment match {
      case Some(ee) => "%s/%s/%s".format(JreContainer, StandardVmType, ee)
      case None => JreContainer
    }

  // Getting and transforming mandatory settings and task results

  def name(ref: Reference)(implicit state: State) =
    setting(Keys.name in ref)

  def buildDirectory(implicit state: State) =
    setting(Keys.baseDirectory in ThisBuild)

  def baseDirectory(ref: Reference)(implicit state: State) =
    setting(Keys.baseDirectory in ref)

  def target(ref: Reference)(implicit state: State) =
    setting(Keys.target in ref)

  def srcDirectories(
    ref: Reference,
    createSrc: EclipseCreateSrc.ValueSet,
    eclipseOutput: Option[String])(
      configuration: Configuration)(
        implicit state: State) = {
    import EclipseCreateSrc._
    val classDirectory = eclipseOutput match {
      case Some(name) => baseDirectory(ref) map (new File(_, name))
      case None => setting(Keys.classDirectory in (ref, configuration))
    }
    def dirs(values: ValueSet, key: SettingKey[Seq[File]]) =
      if (values subsetOf createSrc)
        (setting(key in (ref, configuration)) <**> classDirectory)((sds, cd) => sds map (_ -> cd))
      else
        "".failNel
    Seq(
      dirs(ValueSet(Unmanaged, Source), Keys.unmanagedSourceDirectories),
      dirs(ValueSet(Managed, Source), Keys.managedSourceDirectories),
      dirs(ValueSet(Unmanaged, Resource), Keys.unmanagedResourceDirectories),
      dirs(ValueSet(Managed, Resource), Keys.managedResourceDirectories)
    ).reduceLeft(_ >>*<< _)
  }

  def scalacOptions(ref: ProjectRef)(implicit state: State) =
    evaluateTask(Keys.scalacOptions, ref) map (options =>
      if (options.isEmpty) Nil
      else {
        def pluginValues(value: String) =
          value split "," map (_.trim) filterNot (_ contains "org.scala-lang.plugins/continuations")
        options.zipAll(options.tail, "-", "-") collect {
          case (SettingFormat("Xplugin", value), _) if !pluginValues(value).isEmpty =>
            "Xplugin" -> (pluginValues(value) mkString ",")
          case (SettingFormat(key, value), next) if next startsWith "-" =>
            key -> (if (!value.isEmpty) value else "true")
          case (SettingFormat(key, _), next) =>
            key -> next
        } match {
          case Nil => Nil
          case options => ("scala.compiler.useProjectSettings" -> "true") +: options
        }
      }
    )

  def externalDependencies(
    ref: ProjectRef,
    withSource: Boolean)(
      configuration: Configuration)(
        implicit state: State) = {
    def moduleToFile(key: TaskKey[UpdateReport], p: (Artifact, File) => Boolean = (_, _) => true) =
      evaluateTask(key in configuration, ref) map { updateReport =>
        val moduleToFile =
          for {
            configurationReport <- (updateReport configuration configuration.name).toSeq
            moduleReport <- configurationReport.modules
            (artifact, file) <- moduleReport.artifacts if p(artifact, file)
          } yield moduleReport.module -> file
        moduleToFile.toMap
      }
    def libs(files: Seq[Attributed[File]], binaries: Map[ModuleID, File], sources: Map[ModuleID, File]) = {
      val binaryFilesToSourceFiles =
        for {
          (moduleId, binaryFile) <- binaries
          sourceFile <- sources get moduleId
        } yield binaryFile -> sourceFile
      val libs = files.files map { file => Lib(file)(binaryFilesToSourceFiles get file) }
      libs
    }
    val externalDependencyClasspath = evaluateTask(Keys.externalDependencyClasspath in configuration, ref)
    val binaryModuleToFile = moduleToFile(Keys.update)
    val sourceModuleToFile =
      if (withSource)
        moduleToFile(Keys.updateClassifiers, (artifact, _) => artifact.classifier === Some("sources"))
      else
        Map[ModuleID, File]().success
    val externalDependencies = (externalDependencyClasspath |@| binaryModuleToFile |@| sourceModuleToFile)(libs)
    state.log.debug("External dependencies for configuration '%s' and withSource '%s': %s".format(configuration, withSource, externalDependencies))
    externalDependencies
  }

  def projectDependencies(
    ref: ProjectRef,
    project: ResolvedProject)(
      configuration: Configuration)(
        implicit state: State) = {
    val projectDependencies = project.dependencies collect {
      case dependency if isInConfiguration(configuration, ref, dependency) =>
        setting(Keys.name in dependency.project)
    }
    val projectDependenciesSeq = projectDependencies.sequence
    state.log.debug("Project dependencies for configuration '%s': %s".format(configuration, projectDependenciesSeq))
    projectDependenciesSeq
  }

  def isInConfiguration(configuration: Configuration, ref: ProjectRef, dependency: ClasspathDep[ProjectRef])(
    implicit state: State) = {
    val map =
      Classpaths.mapped(
        dependency.configuration,
        Configurations.names(Classpaths.getConfigurations(ref, structure.data)),
        Configurations.names(Classpaths.getConfigurations(dependency.project, structure.data)),
        "compile", "*->compile"
      )
    !map(configuration.name).isEmpty
  }

  // Getting and transforming optional settings and task results

  def executionEnvironment(ref: Reference)(implicit state: State) =
    setting(EclipseKeys.executionEnvironment in ref).fold(_ => None, id)

  def skipParents(ref: Reference)(implicit state: State) =
    setting(EclipseKeys.skipParents in ref).fold(_ => true, id)

  def withSource(ref: Reference)(implicit state: State) =
    setting(EclipseKeys.withSource in ref).fold(_ => false, id)

  def classpathEntryTransformerFactory(ref: Reference)(implicit state: State) =
    setting(EclipseKeys.classpathEntryTransformerFactory in ref).fold(_ => EclipseClasspathEntryTransformerFactory.Default, id)

  def configurations(ref: Reference)(implicit state: State) =
    setting(EclipseKeys.configurations in ref).fold(
      _ => Seq(Configurations.Compile, Configurations.Test),
      _.toSeq
    )

  def createSrc(ref: Reference)(implicit state: State) =
    setting(EclipseKeys.createSrc in ref).fold(_ => EclipseCreateSrc.Default, id)

  def eclipseOutput(ref: ProjectRef)(implicit state: State) =
    setting(EclipseKeys.eclipseOutput in ref).fold(_ => None, id)

  def preTasks(ref: ProjectRef)(implicit state: State) =
    setting(EclipseKeys.preTasks in ref).fold(_ => Seq.empty, _.zipAll(Seq.empty, null, ref))

  def relativizeLibs(ref: ProjectRef)(implicit state: State) =
    setting(EclipseKeys.relativizeLibs in ref).fold(_ => true, id)

  // IO

  def saveXml(file: File, xml: Elem) =
    fileWriter(file).bracket(closeWriter)(writer => io(writer.write(new PrettyPrinter(999, 2) format xml)))

  def saveProperties(file: File, settings: Seq[(String, String)]) =
    if (!settings.isEmpty) {
      val properties = new Properties
      for ((key, value) <- settings) properties.setProperty(key, value)
      fileWriterMkdirs(file).bracket(closeWriter)(writer =>
        io(properties.store(writer, "Generated by sbteclipse"))
      )
    } else
      io(())

  def fileWriter(file: File) = io(new FileWriter(file))

  def fileWriterMkdirs(file: File) = io {
    file.getParentFile.mkdirs()
    new FileWriter(file)
  }

  def closeWriter(writer: Writer) = io(writer.close())

  // Utilities

  def relativize(baseDirectory: File, file: File) = IO.relativize(baseDirectory, file).get
}

private case class Content(
  name: String,
  dir: File,
  project: Elem,
  classpath: Elem,
  scalacOptions: Seq[(String, String)])

private case class Lib(binary: File)(val source: Option[File])
