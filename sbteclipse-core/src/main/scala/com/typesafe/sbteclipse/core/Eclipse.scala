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
  EclipseTransformerFactory,
  EclipseClasspathEntryTransformerFactory,
  EclipseRewriteRuleTransformerFactory,
  EclipseCreateSrc,
  EclipseProjectFlavor,
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
import scala.xml.{ Node, PrettyPrinter }
import scala.xml.transform.{ RewriteRule, RuleTransformer }
import scalaz.{ Failure, NonEmptyList, Success }
import scalaz.Scalaz._
import scalaz.effects._

private object Eclipse extends EclipseSDTConfig {
  val SettingFormat = """-([^:]*):?(.*)""".r

  val FileSepPattern = FileSep.replaceAll("""\\""", """\\\\""")

  val JreContainer = "org.eclipse.jdt.launching.JRE_CONTAINER"

  val StandardVmType = "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType"

  val ScalaBuilder = "org.scala-ide.sdt.core.scalabuilder"

  val ScalaNature = "org.scala-ide.sdt.core.scalanature"

  val JavaBuilder = "org.eclipse.jdt.core.javabuilder"

  val JavaNature = "org.eclipse.jdt.core.javanature"

  def eclipseCommand(commandName: String): Command =
    Command(commandName)(_ => parser)((state, args) => action(args.toMap, state))

  def parser: Parser[Seq[(String, Any)]] = {
    import EclipseOpts._
    (executionEnvironmentOpt | boolOpt(SkipParents) | boolOpt(WithSource) | boolOpt(GenNetBeans)).*
  }

  def executionEnvironmentOpt: Parser[(String, EclipseExecutionEnvironment.Value)] = {
    import EclipseExecutionEnvironment._
    import EclipseOpts._
    import sbt.complete.DefaultParsers._
    val (head :: tail) = valueSeq map (_.toString)
    val executionEnvironments = tail.foldLeft(head: Parser[String])(_ | _)
    (Space ~> ExecutionEnvironment ~ ("=" ~> executionEnvironments)) map { case (k, v) => k -> withName(v) }
  }

  def action(args: Map[String, Any], state: State): State = {
    state.log.info("About to create Eclipse project files for your project(s).")
    import EclipseOpts._
    handleProjects(
      (args get ExecutionEnvironment).asInstanceOf[Option[EclipseExecutionEnvironment.Value]],
      (args get SkipParents).asInstanceOf[Option[Boolean]] getOrElse skipParents(ThisBuild, state),
      (args get WithSource).asInstanceOf[Option[Boolean]],
      (args get GenNetBeans).asInstanceOf[Option[Boolean]] getOrElse false,
      state
    ).fold(onFailure(state), onSuccess(state))
  }

  def handleProjects(
    executionEnvironmentArg: Option[EclipseExecutionEnvironment.Value],
    skipParents: Boolean,
    withSourceArg: Option[Boolean],
    genNetBeans: Boolean,
    state: State): Validation[IO[Seq[String]]] = {
    val effects = for {
      ref <- structure(state).allProjectRefs
      project <- Project.getProject(ref, structure(state)) if !skip(ref, project, skipParents, state)
    } yield {
      val configs = configurations(ref, state)
      val applic = classpathEntryTransformerFactory(ref, state).createTransformer(ref, state) |@|
        (classpathTransformerFactories(ref, state) map (_.createTransformer(ref, state))).sequence[Validation, RewriteRule] |@|
        (projectTransformerFactories(ref, state) map (_.createTransformer(ref, state))).sequence[Validation, RewriteRule] |@|
        name(ref, state) |@|
        buildDirectory(state) |@|
        baseDirectory(ref, state) |@|
        mapConfigurations(configs, config => srcDirectories(ref, createSrc(ref, state)(config), eclipseOutput(ref, state)(config), state)(config)) |@|
        scalacOptions(ref, state) |@|
        mapConfigurations(configs, externalDependencies(ref, withSourceArg getOrElse withSource(ref, state), state)) |@|
        mapConfigurations(configs, projectDependencies(ref, project, state)) |@|
        projectAggregate(ref, project, state)
      applic(
        handleProject(
          jreContainer(executionEnvironmentArg orElse executionEnvironment(ref, state)),
          preTasks(ref, state),
          relativizeLibs(ref, state),
          builderAndNatures(projectFlavor(ref, state)),
          genNetBeans,
          state
        )
      )
    }
    effects.sequence[Validation, IO[String]] map (_.sequence)
  }

  def onFailure(state: State)(errors: NonEmptyList[String]): State = {
    state.log.error(
      "Could not create Eclipse project files:%s%s".format(NewLine, errors.list mkString NewLine)
    )
    state.fail
  }

  def onSuccess(state: State)(effects: IO[Seq[String]]): State = {
    val names = effects.unsafePerformIO
    if (names.isEmpty)
      state.log.warn("There was no project to create Eclipse project files for!")
    else
      state.log.info(
        "Successfully created Eclipse project files for project(s):%s%s".format(
          NewLine,
          names mkString NewLine
        )
      )
    state
  }

  def skip(ref: ProjectRef, project: ResolvedProject, skipParents: Boolean, state: State): Boolean =
    skip(ref, state) || (skipParents && !project.aggregate.isEmpty)

  def mapConfigurations[A](
    configurations: Seq[Configuration],
    f: Configuration => Validation[Seq[A]]): Validation[Seq[(Configuration, Seq[A])]] = {
    def scoped(c: Configuration): Validation[(Configuration, Seq[A])] = f(c) fold (e => e.fail, s => success(c -> s.distinct))
    (configurations map scoped).sequence
  }

  def handleProject(
    jreContainer: String,
    preTasks: Seq[(TaskKey[_], ProjectRef)],
    relativizeLibs: Boolean,
    builderAndNatures: (String, Seq[String]),
    genNetBeans: Boolean,
    state: State)(
      classpathEntryTransformer: Seq[EclipseClasspathEntry] => Seq[EclipseClasspathEntry],
      classpathTransformers: Seq[RewriteRule],
      projectTransformers: Seq[RewriteRule],
      name: String,
      buildDirectory: File,
      baseDirectory: File,
      srcDirectories: Seq[(Configuration, Seq[(File, File)])],
      scalacOptions: Seq[(String, String)],
      externalDependencies: Seq[(Configuration, Seq[Lib])],
      projectDependencies: Seq[(Configuration, Seq[Prj])],
      projectAggregate: Seq[Prj]): IO[String] = {
    for {
      _ <- executePreTasks(preTasks, state)
      n <- io(name)
      _ <- if (genNetBeans) io(()) else saveXml(baseDirectory / ".project", new RuleTransformer(projectTransformers: _*)(projectXml(name, builderAndNatures)))
      cp <- classpath(
        classpathEntryTransformer,
        name,
        buildDirectory,
        baseDirectory,
        relativizeLibs,
        srcDirectories,
        externalDependencies,
        projectDependencies,
        projectAggregate,
        jreContainer,
        genNetBeans,
        state
      )
      _ <- if (genNetBeans) saveXml(baseDirectory / ".classpath_nb", cp) else saveXml(baseDirectory / ".classpath", new RuleTransformer(classpathTransformers: _*)(cp))
      _ <- if (genNetBeans) io(()) else saveProperties(baseDirectory / ".settings" / "org.scala-ide.sdt.core.prefs", scalacOptions)
    } yield n
  }

  def executePreTasks(preTasks: Seq[(TaskKey[_], ProjectRef)], state: State): IO[Unit] =
    io(for ((preTask, ref) <- preTasks) evaluateTask(preTask, ref, state))

  def projectXml(name: String, builderAndNatures: (String, Seq[String])): Node =
    <projectDescription>
      <name>{ name }</name>
      <buildSpec>
        <buildCommand>
          <name>{ builderAndNatures._1 }</name>
        </buildCommand>
      </buildSpec>
      <natures>
        { builderAndNatures._2.map(n => <nature>{ n }</nature>) }
      </natures>
    </projectDescription>

  def classpath(
    classpathEntryTransformer: Seq[EclipseClasspathEntry] => Seq[EclipseClasspathEntry],
    name: String,
    buildDirectory: File,
    baseDirectory: File,
    relativizeLibs: Boolean,
    srcDirectories: Seq[(Configuration, Seq[(File, File)])],
    externalDependencies: Seq[(Configuration, Seq[Lib])],
    projectDependencies: Seq[(Configuration, Seq[Prj])],
    projectAggregate: Seq[Prj],
    jreContainer: String,
    genNetBeans: Boolean,
    state: State): IO[Node] = {
    val srcEntriesIoSeq =
      for ((config, dirs) <- srcDirectories; (dir, output) <- dirs) yield srcEntry(config, baseDirectory, dir, output, genNetBeans, state)
    for (srcEntries <- srcEntriesIoSeq.sequence) yield {
      val entries = srcEntries ++
        (externalDependencies map { case (config, libs) => libs map libEntry(config, buildDirectory, baseDirectory, relativizeLibs, state) }).flatten ++
        (projectDependencies map { case (config, prjs) => prjs map projectEntry(config, baseDirectory, state) }).flatten ++
        (if (genNetBeans) (projectAggregate map aggProjectEntry(baseDirectory, state)) else Seq()) ++
        (Seq(jreContainer) map EclipseClasspathEntry.Con) ++
        (Seq("bin") map EclipseClasspathEntry.Output)
      if (genNetBeans) <classpath name={ name }>{ classpathEntryTransformer(entries) map (_.toXmlNetBeans) }</classpath>
      else <classpath>{ classpathEntryTransformer(entries) map (_.toXml) }</classpath>
    }
  }

  def srcEntry(
    config: Configuration,
    baseDirectory: File,
    srcDirectory: File,
    classDirectory: File,
    genNetBeans: Boolean,
    state: State): IO[EclipseClasspathEntry.Src] =
    io {
      if (!srcDirectory.exists() && !genNetBeans) srcDirectory.mkdirs()
      EclipseClasspathEntry.Src(
        config.name,
        relativize(baseDirectory, srcDirectory),
        relativize(baseDirectory, classDirectory)
      )
    }

  def libEntry(
    config: Configuration,
    buildDirectory: File,
    baseDirectory: File,
    relativizeLibs: Boolean,
    state: State)(
      lib: Lib): EclipseClasspathEntry.Lib = {
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
    EclipseClasspathEntry.Lib(config.name, path(lib.binary), lib.source map path)
  }

  def projectEntry(
    config: Configuration,
    baseDirectory: File,
    state: State)(
      prj: Prj): EclipseClasspathEntry.Project = {
    EclipseClasspathEntry.Project(
      prj.name,
      prj.baseDirectory.getAbsolutePath,
      prj.classDirectory map (_.getAbsolutePath) getOrElse ""
    )
  }

  def aggProjectEntry(
    baseDirectory: File,
    state: State)(
      prj: Prj): EclipseClasspathEntry.AggProject = {
    EclipseClasspathEntry.AggProject(
      prj.name,
      prj.baseDirectory.getAbsolutePath
    )
  }

  def jreContainer(executionEnvironment: Option[EclipseExecutionEnvironment.Value]): String =
    executionEnvironment match {
      case Some(ee) => "%s/%s/%s".format(JreContainer, StandardVmType, ee)
      case None => JreContainer
    }

  def builderAndNatures(projectFlavor: EclipseProjectFlavor.Value) =
    if (projectFlavor == EclipseProjectFlavor.Scala)
      ScalaBuilder -> Seq(ScalaNature, JavaNature)
    else
      JavaBuilder -> Seq(JavaNature)

  // Getting and transforming mandatory settings and task results

  def name(ref: Reference, state: State): Validation[String] =
    if (setting(EclipseKeys.useProjectId in ref, state).fold(_ => false, id))
      setting(Keys.thisProject in ref, state) map (_.id)
    else
      setting(Keys.name in ref, state)

  def buildDirectory(state: State): Validation[File] =
    setting(Keys.baseDirectory in ThisBuild, state)

  def baseDirectory(ref: Reference, state: State): Validation[File] =
    setting(Keys.baseDirectory in ref, state)

  def target(ref: Reference, state: State): Validation[File] =
    setting(Keys.target in ref, state)

  def srcDirectories(
    ref: Reference,
    createSrc: EclipseCreateSrc.ValueSet,
    eclipseOutput: Option[String],
    state: State)(
      configuration: Configuration): Validation[Seq[(File, File)]] = {
    import EclipseCreateSrc._
    val classDirectory = eclipseOutput match {
      case Some(name) => baseDirectory(ref, state) map (new File(_, name))
      case None => setting(Keys.classDirectory in (ref, configuration), state)
    }
    def dirs(values: ValueSet, key: SettingKey[Seq[File]]) =
      if (values subsetOf createSrc)
        (setting(key in (ref, configuration), state) <**> classDirectory)((sds, cd) => sds map (_ -> cd))
      else
        success(Seq.empty)
    Seq(
      dirs(ValueSet(Unmanaged, Source), Keys.unmanagedSourceDirectories),
      dirs(ValueSet(Managed, Source), Keys.managedSourceDirectories),
      dirs(ValueSet(Unmanaged, Resource), Keys.unmanagedResourceDirectories),
      dirs(ValueSet(Managed, Resource), Keys.managedResourceDirectories)
    ) reduceLeft (_ >>*<< _)
  }

  def scalacOptions(ref: ProjectRef, state: State): Validation[Seq[(String, String)]] =
    evaluateTask(Keys.scalacOptions, ref, state) map (options =>
      if (options.isEmpty)
        Nil
      else {
        fromScalacToSDT(options) match {
          case Seq() => Seq()
          case options => ("scala.compiler.useProjectSettings" -> "true") +: options
        }
      }
    )

  def externalDependencies(
    ref: ProjectRef,
    withSource: Boolean,
    state: State)(
      configuration: Configuration): Validation[Seq[Lib]] = {
    def moduleToFile(key: TaskKey[UpdateReport], p: (Artifact, File) => Boolean = (_, _) => true) =
      evaluateTask(key in configuration, ref, state) map { updateReport =>
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
      files.files map (file => Lib(file)(binaryFilesToSourceFiles get file))
    }
    val externalDependencyClasspath =
      evaluateTask(Keys.externalDependencyClasspath in configuration, ref, state)
    val binaryModuleToFile = moduleToFile(Keys.update)
    val sourceModuleToFile =
      if (withSource)
        moduleToFile(Keys.updateClassifiers, (artifact, _) => artifact.classifier === Some("sources"))
      else
        Map[ModuleID, File]().success
    val externalDependencies =
      (externalDependencyClasspath |@| binaryModuleToFile |@| sourceModuleToFile)(libs)
    state.log.debug(
      "External dependencies for configuration '%s' and withSource '%s': %s".format(
        configuration,
        withSource,
        externalDependencies
      )
    )
    externalDependencies
  }

  def projectDependencies(
    ref: ProjectRef,
    project: ResolvedProject,
    state: State)(
      configuration: Configuration): Validation[Seq[Prj]] = {
    val projectDependencies: Seq[Validation[Prj]] = project.dependencies collect {
      case dependency if isInConfiguration(configuration, ref, dependency, state) =>
        val dependencyRef = dependency.project
        val name = setting(Keys.name in dependencyRef, state)
        val baseDir = setting(Keys.baseDirectory in dependencyRef, state)
        val classDir = setting(Keys.classDirectory in (dependencyRef, configuration), state) fold (f => f.fail, s => success(Option(s)))
        (name |@| baseDir |@| classDir)(Prj)
    }
    val projectDependenciesSeq = projectDependencies.sequence
    state.log.debug("Project dependencies for configuration '%s': %s".format(configuration, projectDependenciesSeq))
    projectDependenciesSeq
  }

  def projectAggregate(
    ref: ProjectRef,
    project: ResolvedProject,
    state: State): Validation[Seq[Prj]] = {
    val projects: Seq[Validation[Prj]] = project.aggregate collect {
      case prj =>
        val prjRef = prj.project
        val name = setting(Keys.name in prjRef, state)
        val baseDir = setting(Keys.baseDirectory in prjRef, state)
        (name |@| baseDir |@| success(None))(Prj)
    }
    val projectsSeq = projects.sequence
    state.log.debug("Project aggregate: %s".format(projectsSeq))
    projectsSeq
  }

  def isInConfiguration(
    configuration: Configuration,
    ref: ProjectRef,
    dependency: ClasspathDep[ProjectRef],
    state: State): Boolean = {
    val map = Classpaths.mapped(
      dependency.configuration,
      Configurations.names(Classpaths.getConfigurations(ref, structure(state).data)),
      Configurations.names(Classpaths.getConfigurations(dependency.project, structure(state).data)),
      "compile", "*->compile"
    )
    !map(configuration.name).isEmpty
  }

  // Getting and transforming optional settings and task results

  def executionEnvironment(ref: Reference, state: State): Option[EclipseExecutionEnvironment.Value] =
    setting(EclipseKeys.executionEnvironment in ref, state).fold(_ => None, id)

  def skipParents(ref: Reference, state: State): Boolean =
    setting(EclipseKeys.skipParents in ref, state).fold(_ => true, id)

  def withSource(ref: Reference, state: State): Boolean =
    setting(EclipseKeys.withSource in ref, state).fold(_ => false, id)

  def classpathEntryTransformerFactory(ref: Reference, state: State): EclipseTransformerFactory[Seq[EclipseClasspathEntry] => Seq[EclipseClasspathEntry]] =
    setting(EclipseKeys.classpathEntryTransformerFactory in ref, state).fold(
      _ => EclipseClasspathEntryTransformerFactory.Identity,
      id
    )

  def classpathTransformerFactories(ref: Reference, state: State): Seq[EclipseTransformerFactory[RewriteRule]] =
    setting(EclipseKeys.classpathTransformerFactories in ref, state).fold(
      _ => Seq(EclipseRewriteRuleTransformerFactory.ClasspathDefault),
      EclipseRewriteRuleTransformerFactory.ClasspathDefault +: _
    )

  def projectTransformerFactories(ref: Reference, state: State): Seq[EclipseTransformerFactory[RewriteRule]] =
    setting(EclipseKeys.projectTransformerFactories in ref, state).fold(
      _ => Seq(EclipseRewriteRuleTransformerFactory.Identity),
      id
    )

  def configurations(ref: Reference, state: State): Seq[Configuration] =
    setting(EclipseKeys.configurations in ref, state).fold(
      _ => Seq(Configurations.Compile, Configurations.Test),
      _.toSeq
    )

  def createSrc(ref: Reference, state: State)(configuration: Configuration): EclipseCreateSrc.ValueSet =
    setting(EclipseKeys.createSrc in (ref, configuration), state).fold(_ => EclipseCreateSrc.Default, id)

  def projectFlavor(ref: Reference, state: State) =
    setting(EclipseKeys.projectFlavor in ref, state).fold(_ => EclipseProjectFlavor.Scala, id)

  def eclipseOutput(ref: ProjectRef, state: State)(config: Configuration): Option[String] =
    setting(EclipseKeys.eclipseOutput in (ref, config), state).fold(_ => None, id)

  def preTasks(ref: ProjectRef, state: State): Seq[(TaskKey[_], ProjectRef)] =
    setting(EclipseKeys.preTasks in ref, state).fold(_ => Seq.empty, _.zipAll(Seq.empty, null, ref))

  def relativizeLibs(ref: ProjectRef, state: State): Boolean =
    setting(EclipseKeys.relativizeLibs in ref, state).fold(_ => true, id)

  def skip(ref: ProjectRef, state: State): Boolean =
    setting(EclipseKeys.skipProject in ref, state).fold(_ => false, id)

  // IO

  def saveXml(file: File, xml: Node): IO[Unit] =
    fileWriter(file).bracket(closeWriter)(writer => io(writer.write(new PrettyPrinter(999, 2) format xml)))

  def saveProperties(file: File, settings: Seq[(String, String)]): IO[Unit] =
    if (!settings.isEmpty) {
      val properties = new Properties
      for ((key, value) <- settings) properties.setProperty(key, value)
      fileWriterMkdirs(file).bracket(closeWriter)(writer =>
        io(properties.store(writer, "Generated by sbteclipse"))
      )
    } else
      io(())

  def fileWriter(file: File): IO[FileWriter] =
    io(new FileWriter(file))

  def fileWriterMkdirs(file: File): IO[FileWriter] =
    io {
      file.getParentFile.mkdirs()
      new FileWriter(file)
    }

  def closeWriter(writer: Writer): IO[Unit] =
    io(writer.close())

  // Utilities

  def relativize(baseDirectory: File, file: File): String =
    IO.relativize(baseDirectory, file).get
}

private case class Content(
  name: String,
  dir: File,
  project: Node,
  classpath: Node,
  scalacOptions: Seq[(String, String)])

private case class Lib(binary: File)(val source: Option[File])
private case class Prj(name: String, baseDirectory: File, classDirectory: Option[File])
