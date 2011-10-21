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

import java.io.{ File, FileWriter }
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

  val CreateSrc: String = "create-src"

  val SameTargets: String = "same-targets"

  val SkipParents: String = "skip-parents"

  val SkipRoot: String = "skip-root"

  val WithSources: String = "with-sources"

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
    eclipseFiles(args).sequence[ValidationNELString, String] match {
      case Success(scalaVersion) =>
        if (scalaVersion.isEmpty)
          logWarn("Attention: There was no project to create Eclipse project files for! Maybe you used skip-root on a build without sub-projects.")
        else
          logInfo("Successfully created Eclipse project files. Please select the appropriate Eclipse plugin for Scala %s!" format scalaVersion.head)
        state
      case Failure(errors) =>
        logError(errors.list mkString ", ")
        state.fail
    }
  }

  def eclipseFiles(args: Seq[String])(implicit state: State): Seq[ValidationNELString[String]] =
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
        projectDependencies(ref, project))(saveEclipseFiles(args contains CreateSrc, args contains SameTargets))
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
    (libraryBinaries(ref) |@| librarySources(ref, withSources)) { (binaries, sources) =>
      binaries map { case (moduleId, binaryFile) => Library(binaryFile, sources get moduleId) }
    }
  }

  def libraryBinaries(ref: ProjectRef)(implicit state: State): ValidationNELString[Map[ModuleID, File]] =
    modules(ref, Keys.update, (_, file) => !(file.getName endsWith "scala-library.jar"))

  def librarySources(ref: ProjectRef, withSources: Boolean)(implicit state: State): ValidationNELString[Map[ModuleID, File]] =
    if (withSources)
      modules(ref, Keys.updateClassifiers, (artifact, file) => !(file.getName endsWith "scala-library.jar") && artifact.classifier == Some("sources"))
    else
      Map.empty.success

  def modules(ref: ProjectRef, key: TaskKey[UpdateReport], p: (Artifact, File) => Boolean)(implicit state: State): ValidationNELString[Map[ModuleID, File]] =
    evaluateTask(key in Configurations.Test, ref) match {
      case Some(Value(updateReport)) =>
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

  def saveEclipseFiles(createSrc: Boolean,
    sameTargets: Boolean)(
      projectName: String,
      scalaVersion: String,
      baseDirectory: File,
      compileDirectories: Directories,
      testDirectories: Directories,
      libraries: Iterable[Library],
      projectDependencies: Seq[String])(
        implicit state: State): String = {
    def savePretty(xml: Elem, file: File): Unit = {
      val out = new FileWriter(file)
      out.write(new PrettyPrinter(999, 2) format xml)
      out.close()
    }
    savePretty(projectXml(projectName), baseDirectory / ".project")
    savePretty(classpathXml(createSrc,
      sameTargets,
      baseDirectory,
      compileDirectories,
      testDirectories,
      libraries,
      projectDependencies),
      baseDirectory / ".classpath")
    scalaVersion
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
}

private object Path {
  def unapply(file: File): Option[String] =
    Some(file.getAbsolutePath)
}

private case class Directories(sources: Seq[File], resources: Seq[File], clazz: File)

private case class Library(binary: File, sources: Option[File] = None)
