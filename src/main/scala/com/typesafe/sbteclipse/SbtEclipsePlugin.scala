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

import java.io.{ File, FileWriter, PrintWriter }
import sbt.{ Path => _, _ }
import sbt.complete.Parser
import sbt.complete.Parsers._
import scala.xml.{ Elem, NodeSeq, PrettyPrinter }
import scalaz.{ Failure, NonEmptyList, Success }
import scalaz.Scalaz._

object SbtEclipsePlugin extends Plugin {
  override def settings: Seq[Setting[_]] =
    Seq(Keys.commands += SbtEclipse.eclipseCommand)
}

private object SbtEclipse {

  val CreateSrc = "create-src"

  val SameTargets = "same-targets"

  val SkipParents = "skip-parents"

  val SkipRoot = "skip-root"

  val WithSources = "with-sources"

  val SettingFormat = """-?([^:]*):?(.*)""".r

  def eclipseCommand: Command =
    Command("eclipse")(_ => parser)((state, args) => action(args)(state))

  def parser: Parser[Seq[String]] =
    (Space ~> CreateSrc |
      Space ~> SameTargets |
      Space ~> SkipParents |
      Space ~> SkipRoot |
      Space ~> WithSources).*

  def action(args: Seq[String])(implicit state: State): State = {
    logInfo("About to create an Eclipse project for you.")
    logInfo("Please hang on, because it might be necessary to perform one or more updates and this might take some time ...")
    allEclipseFiles(args).sequence match {
      case Success(files) =>
        if (files.isEmpty)
          logWarn("Attention: There was no project to create Eclipse project files for! Maybe you used skip-root on a build without sub-projects.")
        else {
          for ((file, project, classpath, settings) <- files) {
            savePretty(project, file / ".project")
            savePretty(classpath, file / ".classpath")
            save(settings, file / ".settings" / "org.scala-ide.sdt.core.prefs")
          }
          logInfo("Successfully created Eclipse project files.")
        }
        state
      case Failure(errors) =>
        logError(errors.list mkString ", ")
        state.fail
    }
  }

  def allEclipseFiles(args: Seq[String])(implicit state: State): Seq[ValidationNELString[(File, Elem, Elem, Seq[String])]] =
    for {
      ref <- structure.allProjectRefs
      project <- Project.getProject(ref, structure) if isNotSkipped(args, ref, project)
    } yield {
      (projectName(ref) |@|
        scalaVersion(ref) |@|
        baseDirectory(ref) |@|
        compileDirectories(ref) |@|
        testDirectories(ref) |@|
        libraries(ref, args contains WithSources) |@|
        projectDependencies(ref, project) |@|
        settings(ref))(eclipseFiles(args contains CreateSrc, args contains SameTargets))
    }

  def isNotSkipped(args: Seq[String], ref: ProjectRef, project: ResolvedProject)(implicit state: State): Boolean = {
    val skipParents = args contains SkipParents
    val skipRoot = args contains SkipRoot
    skipParents && !isParentProject(project) ||
      skipRoot && !isRootProject(ref) ||
      !(skipParents || skipRoot)
  }

  def projectName(ref: ProjectRef)(implicit state: State): ValidationNELString[String] =
    setting(Keys.name, "Missing project name for %s!" format ref.project, ref)

  def scalaVersion(ref: ProjectRef)(implicit state: State): ValidationNELString[String] =
    setting(Keys.scalaVersion, "Missing Scala version for %s!" format ref.project, ref)

  def baseDirectory(ref: ProjectRef)(implicit state: State): ValidationNELString[File] =
    setting(Keys.baseDirectory, "Missing base directory for %s!" format ref.project, ref)

  def compileDirectories(ref: ProjectRef)(implicit state: State): ValidationNELString[Directories] =
    (setting(Keys.sourceDirectories, "Missing source directories for %s!" format ref.project, ref) |@|
      setting(Keys.resourceDirectories, "Missing resource directories for %s!" format ref.project, ref) |@|
      setting(Keys.classDirectory, "Missing class directory for %s!" format ref.project, ref))(Directories)

  def testDirectories(ref: ProjectRef)(implicit state: State): ValidationNELString[Directories] =
    (setting(Keys.sourceDirectories, "Missing test source directories for %s!" format ref.project, ref, Configurations.Test) |@|
      setting(Keys.resourceDirectories, "Missing test resource directories for %s!" format ref.project, ref, Configurations.Test) |@|
      setting(Keys.classDirectory, "Missing test class directory for %s!" format ref.project, ref, Configurations.Test))(Directories)

  def libraries(ref: ProjectRef, withSources: Boolean)(implicit state: State): ValidationNELString[Iterable[Library]] = {
    (libraryFiles(ref) |@| libraryBinaries(ref) |@| librarySources(ref, withSources)) { (files, binaries, sources) =>
      val binaryFilesToSourceFiles =
        for {
          (moduleId, binaryFile) <- binaries
          sourceFile <- sources get moduleId
        } yield binaryFile -> sourceFile
      files map { file => Library(file, binaryFilesToSourceFiles get file) }
    }
  }

  def libraryFiles(ref: ProjectRef)(implicit state: State): ValidationNELString[Seq[File]] =
    evaluateTask(Keys.externalDependencyClasspath in Configurations.Test, ref) match {
      case Some((_, Value(attributedLibs))) => // Hopefully ignoring the returned state is safe for externalDependencyClasspath!
        (attributedLibs.files collect {
          case file if !(file.getAbsolutePath contains "scala-library.jar") => file
        }).success
      case _ => ("Error running externalDependencyClasspath task for %s" format ref.project).failNel
    }

  def libraryBinaries(ref: ProjectRef)(implicit state: State): ValidationNELString[Map[ModuleID, File]] =
    modules(ref, Keys.update)

  def librarySources(ref: ProjectRef, withSources: Boolean)(implicit state: State): ValidationNELString[Map[ModuleID, File]] =
    if (withSources)
      modules(ref, Keys.updateClassifiers, (artifact, _) => artifact.classifier == Some("sources"))
    else
      Map.empty.success

  def modules(ref: ProjectRef, key: TaskKey[UpdateReport], p: (Artifact, File) => Boolean = (_, _) => true)(implicit state: State): ValidationNELString[Map[ModuleID, File]] =
    evaluateTask(key in Configurations.Test, ref) match {
      case Some((_, Value(updateReport))) => // Hopefully ignoring the returned state is safe for update and updateClassifiers!
        (for {
          configurationReport <- (updateReport configuration "test").toSeq
          moduleReport <- configurationReport.modules
          (artifact, file) <- moduleReport.artifacts if p(artifact, file)
        } yield moduleReport.module -> file).toMap.success
      case _ => ("Error running task %s for %s".format(key, ref.project)).failNel
    }

  def projectDependencies(ref: ProjectRef, project: ResolvedProject)(implicit state: State): ValidationNELString[Seq[String]] = {
    val projectDependencies = project.dependencies map { dependency =>
      setting(Keys.name, "Missing project name for %s!" format ref.project, dependency.project)
    }
    projectDependencies.sequence[ValidationNELString, String]
  }

  def settings(ref: ProjectRef)(implicit state: State): ValidationNELString[Seq[String]] =
    evaluateTask(Keys.scalacOptions in Configurations.Compile, ref) match {
      case Some((_, Value(Nil))) => Nil.success // Hopefully ignoring the returned state is safe for externalDependencyClasspath!
      case Some((_, Value(options))) => ("scala.compiler.useProjectSettings" +: options).success // Hopefully ignoring ...!
      case _ => ("Error running externalDependencyClasspath task for %s" format ref.project).failNel
    }

  def eclipseFiles(createSrc: Boolean, sameTargets: Boolean)(
    projectName: String,
    scalaVersion: String,
    baseDirectory: File,
    compileDirectories: Directories,
    testDirectories: Directories,
    libraries: Iterable[Library],
    projectDependencies: Seq[String],
    settings: Seq[String])(
      implicit state: State): (File, Elem, Elem, Seq[String]) = {
    (baseDirectory, projectXml(projectName), classpathXml(
      createSrc,
      sameTargets,
      baseDirectory,
      compileDirectories,
      testDirectories,
      libraries,
      projectDependencies),
      settings)
  }

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

  def classpathXml(createSrc: Boolean,
    sameTargets: Boolean,
    baseDirectory: File,
    compileDirectories: Directories,
    testDirectories: Directories,
    libraries: Iterable[Library],
    projectDependencies: Seq[String])(
      implicit state: State) = {
    def outputPath(file: File) = {
      val relative = IO.relativize(baseDirectory, file).get // TODO Is this safe?
      if (sameTargets) relative
      else IO.relativize(baseDirectory, new File(baseDirectory, "." + relative)).get // TODO Is this safe?
    }
    def srcEntries(directories: Seq[File], output: File) = directories flatMap { directory =>
      if (!directory.exists && createSrc) {
        logDebug("""Creating src directory "%s".""" format directory)
        directory.mkdirs()
      }
      if (directory.exists) {
        logDebug("""Creating src entry for directory "%s".""" format directory)
        val relative = IO.relativize(baseDirectory, directory).get // TODO Is this safe?
        val relativeOutput = outputPath(output)
        <classpathentry kind="src" path={ relative.toString } output={ relativeOutput.toString }/>
      } else {
        logDebug("""Skipping src entry for not-existing directory "%s".""" format directory)
        NodeSeq.Empty
      }
    }
    def libEntries = libraries flatMap {
      // Scala compiler (and library) are treated as special kind of dependencies in Scala IDE!
      // TODO Special test needed!!
      case Library(file, _) if file.getName == "scala-compiler.jar" =>
        logDebug("Creating lib entry for Scala compiler.")
        <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_COMPILER_CONTAINER"/>
      case Library(Path(binary), Some(Path(sources))) =>
        logDebug("""Creating lib entry with source attachment for dependency "%s".""" format binary)
        <classpathentry kind="lib" path={ binary } sourcepath={ sources }/>
      case Library(Path(binary), _) =>
        logDebug("""Creating lib entry for dependency "%s".""" format binary)
        <classpathentry kind="lib" path={ binary }/>
    }
    def projectDependencyEntries = projectDependencies.distinct flatMap { projectDependency =>
      logDebug("""Creating project dependency entry for "%s".""" format projectDependency)
      <classpathentry kind="src" path={ "/" + projectDependency } exported="true" combineaccessrules="false"/>
    }
    <classpath>{
      srcEntries(compileDirectories.sources, compileDirectories.clazz) ++
        srcEntries(compileDirectories.resources, compileDirectories.clazz) ++
        srcEntries(testDirectories.sources, testDirectories.clazz) ++
        srcEntries(testDirectories.resources, testDirectories.clazz) ++
        libEntries ++
        projectDependencyEntries ++
        <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>
        <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
        <classpathentry kind="output" path={ outputPath(compileDirectories.clazz) }/>
    }</classpath>
  }

  def savePretty(xml: Elem, file: File): Unit = {
    val out = new FileWriter(file)
    try out.write(new PrettyPrinter(999, 2) format xml)
    finally out.close()
  }

  def save(settings: Seq[String], file: File): Unit = {
    if (!settings.isEmpty) {
      file.getParentFile.mkdirs()
      val out = new PrintWriter(new FileWriter(file))
      try settings map settingToEclipseFormat foreach out.println finally out.close()
    }
  }

  def settingToEclipseFormat(setting: String): String = {
    val SettingFormat(key, value) = setting
    "%s=%s".format(key, if (!value.isEmpty) value else true)
  }
}

private object Path {

  def unapply(file: File): Option[String] =
    Some(file.getAbsolutePath)
}

private case class Directories(sources: Seq[File], resources: Seq[File], clazz: File)

private case class Library(binary: File, sources: Option[File] = None)
