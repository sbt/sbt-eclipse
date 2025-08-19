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
  EclipseCreateSrc,
  EclipseProjectFlavor,
  EclipseExecutionEnvironment,
  EclipseJDTMode,
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
  EvaluateTask,
  File,
  Inc,
  Incomplete,
  Keys,
  ModuleID,
  ModuleReport,
  Project,
  ProjectRef,
  Reference,
  ResolvedProject,
  SettingKey,
  State,
  TaskKey,
  ThisBuild,
  UpdateReport,
  Value
}
import sbt.fileToRichFile
import sbt.internal.BuildStructure
import sbt.complete.Parser
import scala.xml.{ Node, PrettyPrinter }
import scala.xml.transform.{ RewriteRule, RuleTransformer }
import scalaz.{ Equal, NonEmptyList }
import scalaz.Scalaz._
import scalaz.effect.IO
import com.typesafe.sbteclipse.core.util.ScalaVersion
import java.io.FileReader
import java.io.Reader

private object Eclipse extends EclipseSDTConfig {
  val SettingFormat = """-([^:]*):?(.*)""".r

  val FileSepPattern = FileSep.replaceAll("""\\""", """\\\\""")

  val JreContainer = "org.eclipse.jdt.launching.JRE_CONTAINER"

  val StandardVmType = "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType"

  val ScalaBuilder = "org.scala-ide.sdt.core.scalabuilder"

  val ScalaNature = "org.scala-ide.sdt.core.scalanature"

  val JavaBuilder = "org.eclipse.jdt.core.javabuilder"

  val JavaNature = "org.eclipse.jdt.core.javanature"

  val JreContainerVersionSelector = """.*/.*/.*-([0-9.]+)""".r

  def eclipseCommand(commandName: String): Command =
    Command(commandName)(_ => parser)((state, args) => action(args.toMap, state))

  def parser: Parser[Seq[(String, Any)]] = {
    import EclipseOpts._
    (executionEnvironmentOpt | jdtModeOpt |
      boolOpt(SkipParents) | boolOpt(WithSource) | boolOpt(WithJavadoc) |
      boolOpt(WithBundledScalaContainers)).*
  }

  def executionEnvironmentOpt: Parser[(String, EclipseExecutionEnvironment.Value)] = {
    import EclipseExecutionEnvironment._
    import EclipseOpts._
    import sbt.complete.DefaultParsers._
    val (head :: tail) = valueSeq map (_.toString)
    val executionEnvironments = tail.foldLeft(head: Parser[String])(_ | _)
    (Space ~> ExecutionEnvironment ~ ("=" ~> executionEnvironments)) map { case (k, v) => k -> withName(v) }
  }

  def jdtModeOpt: Parser[(String, EclipseJDTMode.Value)] = {
    import EclipseJDTMode._
    import EclipseOpts._
    import sbt.complete.DefaultParsers._
    val (head :: tail) = valueSeq map (_.toString)
    val jdtModes = tail.foldLeft(head: Parser[String])(_ | _)
    (Space ~> JDTMode ~ ("=" ~> jdtModes)) map { case (k, v) => k -> withName(v) }
  }

  def action(args: Map[String, Any], state: State): State = {
    state.log.info("About to create Eclipse project files for your project(s).")
    import EclipseOpts._
    handleProjects(
      (args get ExecutionEnvironment).asInstanceOf[Option[EclipseExecutionEnvironment.Value]],
      (args get JDTMode).asInstanceOf[Option[EclipseJDTMode.Value]],
      (args get SkipParents).asInstanceOf[Option[Boolean]] getOrElse skipParents(ThisBuild, state),
      (args get WithSource).asInstanceOf[Option[Boolean]],
      (args get WithJavadoc).asInstanceOf[Option[Boolean]],
      state).fold(onFailure(state), onSuccess(state))
  }

  def handleProjects(
    executionEnvironmentArg: Option[EclipseExecutionEnvironment.Value],
    jdtModeArg: Option[EclipseJDTMode.Value],
    skipParents: Boolean,
    withSourceArg: Option[Boolean],
    withJavadocArg: Option[Boolean],
    state: State): Validation[IO[Seq[String]]] = {
    val effects = for {
      ref <- structure(state).allProjectRefs
      project <- Project.getProject(ref, structure(state)) if !skip(ref, project, skipParents, state)
    } yield {
      executePreTasks(preTasks(ref, state), state) // has to be called before externalDependencies() so all folders and classes exist already (in case we have a compile task in preTasks)
      val configs = configurations(ref, state)
      val source = withSourceArg getOrElse withSource(ref, state)
      val javadoc = withJavadocArg getOrElse withJavadoc(ref, state)
      val applic =
        (classpathTransformerFactories(ref, state).toList map (_.createTransformer(ref, state))).sequence[Validation, RewriteRule] |@|
          (projectTransformerFactories(ref, state).toList map (_.createTransformer(ref, state))).sequence[Validation, RewriteRule] |@|
          name(ref, state) |@|
          buildDirectory(state) |@|
          baseDirectory(ref, state) |@|
          mapConfigurations(configs, config => srcDirectories(ref, createSrc(ref, state)(config), eclipseOutput(ref, state)(config), state)(config)) |@|
          scalacOptions(ref, state) |@|
          compileOrder(ref, state) |@|
          mapConfigurations(removeExtendedConfigurations(configs), externalDependencies(ref, source, javadoc, state)) |@|
          mapConfigurations(configs, projectDependencies(ref, project, state))
      applic(
        handleProject(
          jreContainer(executionEnvironmentArg orElse executionEnvironment(ref, state)),
          jdtModeArg getOrElse jdtMode(ref, state),
          relativizeLibs(ref, state),
          builderAndNatures(projectFlavor(ref, state)),
          state))
    }
    effects.toList.sequence[Validation, IO[String]].map((list: List[IO[String]]) => list.toStream.sequence.map(_.toList))
  }

  def removeExtendedConfigurations(configurations: Seq[Configuration]): Seq[Configuration] = {
    def findExtended(configurations: Seq[Configuration], acc: Seq[Configuration] = Nil): Seq[Configuration] = {
      val extended = configurations flatMap (_.extendsConfigs)
      if (extended.isEmpty)
        acc
      else
        findExtended(extended, extended ++ acc)
    }
    configurations filterNot findExtended(configurations).contains
  }

  def onFailure(state: State)(errors: NonEmptyList[String]): State = {
    state.log.error(
      s"Could not create Eclipse project files:${NewLine}${List(errors.list).mkString(NewLine)}")
    state
  }

  def onSuccess(state: State)(effects: IO[Seq[String]]): State = {
    val names = effects.unsafePerformIO()
    if (names.isEmpty)
      state.log.warn("There was no project to create Eclipse project files for!")
    else
      state.log.info(
        s"Successfully created Eclipse project files for project(s):${NewLine}${names mkString NewLine}")
    state
  }

  def skip(ref: ProjectRef, project: ResolvedProject, skipParents: Boolean, state: State): Boolean =
    skip(ref, state) || (skipParents && !project.aggregate.isEmpty)

  def mapConfigurations[A](
    configurations: Seq[Configuration],
    f: Configuration => Validation[Seq[A]]): Validation[Seq[A]] =
    (configurations map f).toList.sequence map (_.flatten.distinct)

  def handleProject(
    jreContainer: String,
    jdtMode: EclipseJDTMode.Value,
    relativizeLibs: Boolean,
    builderAndNatures: (String, Seq[String]),
    state: State)(
    classpathTransformers: Seq[RewriteRule],
    projectTransformers: Seq[RewriteRule],
    name: String,
    buildDirectory: File,
    baseDirectory: File,
    srcDirectories: Seq[(File, Option[File])],
    scalacOptions: Seq[(String, String)],
    compileOrder: Option[String],
    externalDependencies: Seq[Lib],
    projectDependencies: Seq[String]): IO[String] = {
    for {
      n <- io(name)
      dirs <- splitSrcDirectories(srcDirectories, baseDirectory)
      // Note - Io does not have filter... hence this ugly
      localSrcDirectories = dirs._1
      linkedSrcDirectories = dirs._2
      cp <- classpath(
        buildDirectory,
        baseDirectory,
        relativizeLibs,
        localSrcDirectories,
        linkedSrcDirectories,
        externalDependencies,
        projectDependencies,
        jreContainer,
        state)
      _ <- saveXml(baseDirectory / ".project", new RuleTransformer(projectTransformers: _*)(projectXml(name, builderAndNatures, linkedSrcDirectories)))
      _ <- saveXml(baseDirectory / ".classpath", new RuleTransformer(classpathTransformers: _*)(cp))
      _ <- saveProperties(baseDirectory / ".settings" / "org.eclipse.core.resources.prefs", Seq(("encoding/<project>" -> "UTF-8")))
      _ <- saveProperties(baseDirectory / ".settings" / "org.scala-ide.sdt.core.prefs", scalacOptions ++: compileOrder.map { order => Seq(("compileorder" -> order)) }.getOrElse(Nil))
      _ <- handleJDTSettings(jdtMode, baseDirectory, jreContainer)
    } yield n
  }

  def executePreTasks(preTasks: Seq[(TaskKey[?], ProjectRef)], state: State): Unit =
    for ((preTask, ref) <- preTasks) evaluateTask(preTask, ref, state)

  def projectXml(name: String, builderAndNatures: (String, Seq[String]), linkedSrcDirectories: Seq[(File, Option[String], File, Option[String])]): Node = {
    val sourceLinks = linkedSrcDirectories.flatMap {
      case (location1, name1, location2, name2) =>
        name1.map(n => Seq((location1, n))).getOrElse(Seq.empty[(File, String)]) ++
          name2.map(n => Seq((location2, n))).getOrElse(Seq.empty[(File, String)])
    }

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
      <linkedResources>
        {
          sourceLinks map {
            case (location, name) =>
              <link>
                <name>{ name.replaceAll("^[A-Z]:", "") }</name>
                <type>2</type>
                <location>{ location.getCanonicalPath.replaceAll("\\\\", "/") }</location>
              </link>
          }
        }
      </linkedResources>
    </projectDescription>
  }

  def createLinkName(file: File, baseDirectory: File): String = {
    val name = file.getCanonicalPath
    // just put '-' in place of bad characters for the name... (for now).
    // in the future we should limit the size via relativizing magikz.
    name.replaceAll("[\\s\\\\/]+", "-")
  }

  def splitSrcDirectories(srcDirectories: Seq[(File, Option[File])], baseDirectory: File): IO[(Seq[(File, Option[File])], Seq[(File, Option[String], File, Option[String])])] = io {
    val (local, linked) =
      srcDirectories partition {
        case (dir, Some(classpath)) => relativizeOpt(baseDirectory, dir).isDefined && relativizeOpt(baseDirectory, classpath).isDefined
        case (dir, None) => relativizeOpt(baseDirectory, dir).isDefined
      }
    //Now, create link names...

    val links =
      for {
        (file, classDirectory) <- linked
        fileName = if (relativizeOpt(baseDirectory, file).isDefined) None else Some(createLinkName(file, baseDirectory))
        classDirectoryName = if (relativizeOpt(baseDirectory, classDirectory.getOrElse(file)).isDefined) None else Some(createLinkName(classDirectory.getOrElse(file), baseDirectory))
      } yield (file, fileName, classDirectory.getOrElse(file), classDirectoryName)

    (local, links)
  }

  def classpath(
    buildDirectory: File,
    baseDirectory: File,
    relativizeLibs: Boolean,
    srcDirectories: Seq[(File, Option[File])],
    srcDirectoryLinks: Seq[(File, Option[String], File, Option[String])],
    externalDependencies: Seq[Lib],
    projectDependencies: Seq[String],
    jreContainer: String,
    state: State): IO[Node] = {
    val srcEntriesIoSeq =
      for {
        (dir, output) <- srcDirectories
        excludes = srcExcludes(srcDirectories, dir)
        if dir.exists()
      } yield srcEntry(baseDirectory, dir, output, excludes, state)
    val srcLinkEntriesIoSeq =
      for {
        (dir, dirName, output, outputName) <- srcDirectoryLinks
        if dir.exists()
      } yield srcLink(baseDirectory, dir, dirName, Some(output), outputName, state)
    for (
      srcEntries <- srcEntriesIoSeq.toList.sequence;
      linkEntries <- srcLinkEntriesIoSeq.toList.sequence
    ) yield {
      def classDirectory = srcEntries.flatMap { _.output }.distinct match {
        case Nil =>
          "bin"
        case dir :: _ =>
          dir
      }
      val entries = srcEntries ++ linkEntries ++
        (projectDependencies map EclipseClasspathEntry.Project) ++
        (externalDependencies map libEntry(buildDirectory, baseDirectory, relativizeLibs, state)) ++
        (Seq(jreContainer) map EclipseClasspathEntry.Con) ++
        (Seq(classDirectory) map EclipseClasspathEntry.Output)
      <classpath>{ entries map (_.toXml) }</classpath>
    }
  }

  def srcExcludes(srcDirectories: Seq[(File, Option[File])], srcDirectory: File): Seq[String] = {
    val separator = java.io.File.separator
    val srcDirectoryPath = srcDirectory.getCanonicalPath + separator
    val srcDirectoryPaths = for ((dir, output) <- srcDirectories) yield dir.getCanonicalPath
    val subFolders = srcDirectoryPaths filter { dirPath =>
      val subFolder = dirPath.startsWith(srcDirectoryPath)
      subFolder && dirPath != srcDirectoryPath
    }
    subFolders map (_.substring(srcDirectoryPath.length) + separator)
  }

  def srcLink(
    baseDirectory: File,
    pathDir: File,
    pathName: Option[String],
    output: Option[File],
    outputName: Option[String],
    state: State): IO[EclipseClasspathEntry.Src] =
    io {
      EclipseClasspathEntry.Src(
        pathName.getOrElse(relativize(baseDirectory, pathDir)),
        outputName match {
          case Some(outputName) => Some(outputName)
          case None => output.map(relativize(baseDirectory, _))
        })
    }

  def srcEntry(
    baseDirectory: File,
    srcDirectory: File,
    classDirectory: Option[File],
    excludes: Seq[String],
    state: State): IO[EclipseClasspathEntry.Src] =
    io {
      EclipseClasspathEntry.Src(
        relativize(baseDirectory, srcDirectory),
        classDirectory.map(relativize(baseDirectory, _)),
        excludes)
    }

  def libEntry(
    buildDirectory: File,
    baseDirectory: File,
    relativizeLibs: Boolean,
    state: State)(
    lib: Lib): EclipseClasspathEntry.Lib = {
    def path(file: File) = {
      val relativizedBase =
        if (buildDirectory === baseDirectory) Some(".") else sbt.IO.relativize(buildDirectory, baseDirectory)
      val relativizedFile = sbt.IO.relativize(buildDirectory, file)
      val relativized = (relativizedBase |@| relativizedFile)((base, file) =>
        s"${base split FileSepPattern map (part => if (part != ".") ".." else part) mkString FileSep}${FileSep}${file}")
      if (relativizeLibs) relativized getOrElse file.getAbsolutePath else file.getAbsolutePath
    }
    EclipseClasspathEntry.Lib(path(lib.binary), lib.source map path, lib.javadoc map path)
  }

  def jreContainer(executionEnvironment: Option[EclipseExecutionEnvironment.Value]): String =
    executionEnvironment match {
      case Some(ee) => s"${JreContainer}/${StandardVmType}/${ee}"
      case None => JreContainer
    }

  def jreContainerToJdtCompilerSettings(jreContainer: String): Seq[(String, String)] = {
    jreContainer match {
      case JreContainerVersionSelector(version) =>
        Seq(
          "org.eclipse.jdt.core.compiler.codegen.targetPlatform" -> version,
          "org.eclipse.jdt.core.compiler.compliance" -> version,
          "org.eclipse.jdt.core.compiler.source" -> version
        )
      case _ =>
        Nil
    }
  }

  def handleJDTSettings(
      mode: EclipseJDTMode.Value,
      baseDirectory: File,
      jreContainer: String
  ): IO[Unit] = {
    val jdtPrefs = baseDirectory / ".settings" / "org.eclipse.jdt.core.prefs"

    mode match {
      case EclipseJDTMode.Ignore =>
        io(())
      case EclipseJDTMode.Remove =>
        fileExists(jdtPrefs).flatMap {
          case false =>
            io(())
          case true =>
            io {
              jdtPrefs.delete()
            }
        }
      case EclipseJDTMode.Update =>
        updateProperties(jdtPrefs, jreContainerToJdtCompilerSettings(jreContainer))
      case EclipseJDTMode.Overwrite =>
        saveProperties(jdtPrefs, jreContainerToJdtCompilerSettings(jreContainer))
    }
  }

  def builderAndNatures(projectFlavor: EclipseProjectFlavor.Value) =
    if (projectFlavor.id == EclipseProjectFlavor.ScalaIDE.id)
      ScalaBuilder -> Seq(ScalaNature, JavaNature)
    else
      JavaBuilder -> Seq(JavaNature)

  // Getting and transforming mandatory settings and task results

  def name(ref: Reference, state: State): Validation[String] =
    if (setting((ref / EclipseKeys.useProjectId), state))
      settingValidation((ref / Keys.thisProject), state) map (_.id)
    else
      settingValidation((ref / Keys.name), state)

  def buildDirectory(state: State): Validation[File] =
    settingValidation((ThisBuild / Keys.baseDirectory), state)

  def baseDirectory(ref: Reference, state: State): Validation[File] =
    settingValidation((ref / Keys.baseDirectory), state)

  def target(ref: Reference, state: State): Validation[File] =
    settingValidation((ref / Keys.target), state)

  def srcDirectories(
    ref: Reference,
    createSrc: EclipseCreateSrc.ValueSet,
    eclipseOutput: Option[String],
    state: State)(
    configuration: Configuration): Validation[Seq[(File, Option[File])]] = {
    import EclipseCreateSrc._
    val classDirectory: Validation[Option[File]] = eclipseOutput match {
      case Some(name) => baseDirectory(ref, state) map { dir => Some(new File(dir, name)) }
      case None => None.success
    }

    def dirs(values: ValueSet, key: SettingKey[Seq[File]]): Validation[List[(sbt.File, Option[java.io.File])]] =
      if (values subsetOf createSrc)
        (settingValidation((ref / configuration / key), state) |@| classDirectory)((sds, cd) => sds.toList map (_ -> cd))
      else
        scalaz.Validation.success(Nil)
    List(
      dirs(ValueSet(), Keys.unmanagedSourceDirectories),
      dirs(ValueSet(), Keys.unmanagedResourceDirectories),
      dirs(ValueSet(ManagedSrc), Keys.managedSourceDirectories),
      dirs(ValueSet(ManagedResources), Keys.managedResourceDirectories)) reduceLeft (_ +++ _)
  }

  def scalacOptions(ref: ProjectRef, state: State): Validation[Seq[(String, String)]] = {
    // Here we have to look at scalacOptions *for compilation*, vs. the ones used for testing.
    // We have to pick one set, and this should be the most complete set.
    (evaluateTask((sbt.Compile / Keys.scalacOptions), ref, state) |@| settingValidation((ref / Keys.scalaVersion), state)) { (options, version) =>
      val ideSettings = fromScalacToSDT(options)
      ScalaVersion.parse(version).settingsFrom(ideSettings.toMap).toSeq
    } map { options => if (options.nonEmpty) ("scala.compiler.useProjectSettings" -> "true") +: options else options }
  }

  def compileOrder(ref: ProjectRef, state: State): Validation[Option[String]] =
    settingValidation((ref / Keys.compileOrder), state).map(order =>
      if (order == xsbti.compile.CompileOrder.Mixed) None
      else Some(order.toString))

  def externalDependencies(
    ref: ProjectRef,
    withSource: Boolean,
    withJavadoc: Boolean,
    state: State)(
    configuration: Configuration): Validation[Seq[Lib]] = {
    def evalTask[A](key: TaskKey[A]) = evaluateTask((configuration / key), ref, state)
    def moduleReports(key: TaskKey[UpdateReport]) =
      evalTask(key) map { updateReport =>
        for {
          configurationReport <- (updateReport configuration configuration).toSeq
          moduleReports <- configurationReport.modules
        } yield moduleReports
      }
    def moduleToFile(moduleReports: Validation[Seq[ModuleReport]], p: (Artifact, File) => Boolean = (artifact, _) => artifact.classifier === None) = {
      moduleReports map (moduleReports => {
        val moduleToFile =
          for {
            moduleReport <- moduleReports
            (artifact, file) <- moduleReport.artifacts if p(artifact, file)
          } yield moduleReport.module -> file
        moduleToFile.toMap
      })
    }
    def moduleFileToArtifactFile(binaries: Map[ModuleID, File], sources: Map[ModuleID, File], javadocs: Map[ModuleID, File]) =
      for ((moduleId, binaryFile) <- binaries)
        yield binaryFile -> Lib(binaryFile)(sources get moduleId)(javadocs get moduleId)
    def libs(files: Seq[Attributed[File]], moduleFiles: Map[File, Lib]): Seq[Lib] = {
      var result: Seq[Lib] = files.files map { file => moduleFiles.get(file).getOrElse(Lib(file)(None)(None)) }
      if (createSrc(ref, state)(configuration).contains(EclipseCreateSrc.ManagedClasses)) {
        result = result ++ managedClassDirectories(ref, state)(configuration).filter(_.exists).map(Lib(_)(None)(None))
      }
      result
    }
    val externalDependencyClasspath: Validation[sbt.Keys.Classpath] = evalTask(Keys.externalDependencyClasspath)
    val moduleFiles: Validation[Map[File, Lib]] = {
      lazy val classifierModuleReports = moduleReports(Keys.updateClassifiers)

      def classifierModuleToFile(classifier: Option[String]) = {
        if (classifier.isDefined)
          moduleToFile(classifierModuleReports, (artifact, _) => artifact.classifier === classifier)
        else
          Map[ModuleID, File]().success
      }

      val sources = if (withSource) Some("sources") else None
      val javadoc = if (withJavadoc) Some("javadoc") else None

      sources |+| javadoc match {
        case Some(_) => {
          val binaryModuleToFile = moduleToFile(moduleReports(Keys.update))

          (binaryModuleToFile |@| classifierModuleToFile(sources) |@| classifierModuleToFile(javadoc))(moduleFileToArtifactFile)
        }
        case None => Map[File, Lib]().success
      }
    }
    val externalDependencies = (externalDependencyClasspath |@| moduleFiles)(libs)
    state.log.debug(
      s"External dependencies for configuration '${configuration}' and withSource '${withSource}' and withJavadoc '${withJavadoc}': ${externalDependencies}")
    externalDependencies
  }

  def projectDependencies(
    ref: ProjectRef,
    project: ResolvedProject,
    state: State)(
    configuration: Configuration): Validation[Seq[String]] = {
    val projectDependencies = project.dependencies collect {
      case dependency if isInConfiguration(configuration, ref, dependency, state) =>
        settingValidation((dependency.project / Keys.name), state)
    }
    val projectDependenciesSeq = projectDependencies.toList.sequence
    state.log.debug(s"Project dependencies for configuration '${configuration}': ${projectDependenciesSeq}")
    projectDependenciesSeq
  }

  def isInConfiguration(
    configuration: Configuration,
    ref: ProjectRef,
    dependency: ClasspathDep[ProjectRef],
    state: State): Boolean = {
    val map = Classpaths.mapped(
      dependency.configuration,
      Configurations.names(Classpaths.getConfigurations(ref, structure(state).data).toVector),
      Configurations.names(Classpaths.getConfigurations(dependency.project, structure(state).data).toVector),
      "compile", "*->compile")
    !map(configuration.name).isEmpty
  }

  // Getting and transforming optional settings and task results

  def executionEnvironment(ref: Reference, state: State): Option[EclipseExecutionEnvironment.Value] =
    setting((ref / EclipseKeys.executionEnvironment), state)

  def jdtMode(ref: Reference, state: State): EclipseJDTMode.Value =
    setting((ref / EclipseKeys.jdtMode), state)

  def skipParents(ref: Reference, state: State): Boolean =
    setting((ref / EclipseKeys.skipParents), state)

  def withSource(ref: Reference, state: State): Boolean =
    setting((ref / EclipseKeys.withSource), state)

  def withJavadoc(ref: Reference, state: State): Boolean =
    setting((ref / EclipseKeys.withJavadoc), state)

  def withBundledScalaContainers(ref: Reference, state: State): Boolean =
    setting((ref / EclipseKeys.withBundledScalaContainers), state)

  def classpathTransformerFactories(ref: Reference, state: State): Seq[EclipseTransformerFactory[RewriteRule]] =
    setting((ref / EclipseKeys.classpathTransformerFactories), state)

  def projectTransformerFactories(ref: Reference, state: State): Seq[EclipseTransformerFactory[RewriteRule]] =
    setting((ref / EclipseKeys.projectTransformerFactories), state)

  def configurations(ref: Reference, state: State): Seq[Configuration] =
    setting((ref / EclipseKeys.configurations), state).toSeq

  def createSrc(ref: Reference, state: State)(configuration: Configuration): EclipseCreateSrc.ValueSet =
    setting((ref / configuration / EclipseKeys.createSrc), state)

  def managedClassDirectories(ref: Reference, state: State)(configuration: Configuration): Seq[sbt.File] =
    setting((ref / configuration / EclipseKeys.managedClassDirectories), state)

  def projectFlavor(ref: Reference, state: State) =
    setting((ref / EclipseKeys.projectFlavor), state)

  def eclipseOutput(ref: ProjectRef, state: State)(config: Configuration): Option[String] =
    setting((ref / config / EclipseKeys.eclipseOutput), state)

  def preTasks(ref: ProjectRef, state: State): Seq[(TaskKey[?], ProjectRef)] =
    setting((ref / EclipseKeys.preTasks), state).zipAll(Seq.empty, null, ref)

  def relativizeLibs(ref: ProjectRef, state: State): Boolean =
    setting((ref / EclipseKeys.relativizeLibs), state)

  def skip(ref: ProjectRef, state: State): Boolean =
    setting((ref / EclipseKeys.skipProject), state)

  // IO

  def saveXml(file: File, xml: Node): IO[Unit] =
    fileWriter(file).bracket(closeWriter)(writer => io(writer.write(new PrettyPrinter(999, 2) format xml)))

  def saveProperties(file: File, settings: Seq[(String, String)]): IO[Unit] =
    if (!settings.isEmpty) {
      val properties = new Properties
      for ((key, value) <- settings) properties.setProperty(key, value)
      fileWriterMkdirs(file).bracket(closeWriter)(writer =>
        io(properties.store(writer, "Generated by sbteclipse")))
    } else
      io(())

  def updateProperties(file: File, settings: Seq[(String, String)]): IO[Unit] =
    if (!settings.isEmpty) {
      fileExists(file).flatMap {
        case false =>
          saveProperties(file, settings)
        case true =>
          fileReader(file)
            .bracket(closeReader) { reader =>
              io {
                val properties = new Properties
                properties.load(reader)
                properties
              }
            }
            .flatMap { properties =>
              // only write if updates were made
              val write = (for {
                (key, value) <- settings
                update = {
                  if (properties.getProperty(key) != value) {
                    properties.setProperty(key, value)
                    true
                  } else
                    false
                }
              } yield update).fold(false)(_ || _)

              if (write) {
                fileWriter(file).bracket(closeWriter) { writer =>
                  io(properties.store(writer, "Updated by sbteclipse"))
                }
              } else {
                io(())
              }
            }
      }
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

  def fileExists(file: File): IO[Boolean] =
    io(file.exists())

  def fileReader(file: File): IO[FileReader] =
    io(new FileReader(file))

  def closeReader(reader: Reader): IO[Unit] =
    io(reader.close())

  private def io[T](t: => T): IO[T] = scalaz.effect.IO(t)

  // Utilities

  // Note: Relativize doesn't take into account "..", so we need to normalize *first* (yippie), then check for relativize.
  // Also - Instead of failure we should generate a "link".
  def relativize(baseDirectory: File, file: File): String =
    relativizeOpt(baseDirectory, file).get

  def relativizeOpt(baseDirectory: File, file: File): Option[String] =
    sbt.IO.relativize(baseDirectory, normalize(file))

  def normalize(file: File): File =
    new File(normalizeName(file.getAbsolutePath))

  def normalizeName(filename: String): String = {
    if (filename contains "..") {
      val parts = (filename split "[\\/]+").toList
      def fix(parts: List[String], result: String): String = parts match {
        case Nil => result
        case a :: ".." :: rest => fix(rest, result)
        case a :: rest if result.isEmpty => fix(rest, a)
        case a :: rest => fix(rest, result + java.io.File.separator + a)
      }
      fix(parts, "")
    } else filename
  }

  implicit val fileEqual: Equal[File] = new Equal[File] {
    def equal(file1: File, file2: File): Boolean = file1 == file2
  }

  def id[A](a: A): A = a

  def boolOpt(key: String): Parser[(String, Boolean)] = {
    import sbt.complete.DefaultParsers._
    (Space ~> key ~ ("=" ~> ("true" | "false"))) map { case (k, v) => k -> v.toBoolean }
  }

  /**
   * @param key the fully qualified key
   */
  def settingValidation[A](key: SettingKey[A], state: State): Validation[A] =
    key.get(structure(state).data) match {
      case Some(a) => a.success
      case None => s"Undefined setting '${key.key}'!".failureNel
    }

  /**
   * @param key the fully qualified key
   */
  def setting[A](key: SettingKey[A], state: State): A = key.get(structure(state).data).getOrElse {
    throw new IllegalStateException(s"Undefined setting '${key.key} in ${key.scope}'!")
  }

  def evaluateTask[A](key: TaskKey[A], ref: ProjectRef, state: State): Validation[A] = {
    val taskConfig = EvaluateTask.extractedTaskConfig(Project.extract(state), structure(state), state)
    EvaluateTask(structure(state), key, state, ref, taskConfig) match {
      case Some((_, Value(a))) => a.success
      case Some((_, Inc(inc))) => s"Error evaluating task '${key.key}': ${Incomplete.show(inc.tpe)}".failureNel
      case None => s"Undefined task '${key.key}' for '${ref.project}'!".failureNel
    }
  }

  def structure(state: State): BuildStructure = Project.extract(state).structure

}

private case class Content(
  name: String,
  dir: File,
  project: Node,
  classpath: Node,
  scalacOptions: Seq[(String, String)])

private case class Lib(binary: File)(val source: Option[File])(val javadoc: Option[File])
