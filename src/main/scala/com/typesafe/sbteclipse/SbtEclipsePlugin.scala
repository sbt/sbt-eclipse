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
import sbt.complete.Parsers._
import scala.xml.{ Elem, NodeSeq, PrettyPrinter }
import scalaz.{ Failure, NonEmptyList, Success }
import scalaz.Scalaz._

object SbtEclipsePlugin extends Plugin {
  import SbtEclipse._

  override def settings = Seq(Keys.commands += eclipseCommand)
}

object SbtEclipse {

  private[sbteclipse] val CreateSrc = "create-src"

  private[sbteclipse] val SameTargets = "same-targets"

  private[sbteclipse] val SkipParents = "skip-parents"

  private[sbteclipse] val SkipRoot = "skip-root"

  private[sbteclipse] val WithSources = "with-sources"

  private[sbteclipse] val Parser = (Space ~> CreateSrc |
    Space ~> SameTargets |
    Space ~> SkipParents |
    Space ~> SkipRoot |
    Space ~> WithSources).*

  private[sbteclipse] def eclipseCommand = Command("eclipse")(_ => Parser) { (state, args) =>
    implicit val implicitState = state
    logInfo("About to create an Eclipse project for you.")
    logInfo("Please hang on, because it might be necessary to perform one or more updates and this might take some time ...")
    val (skipParents, skipRoot) = (args contains SkipParents, args contains SkipRoot)
    val results = for {
      ref <- structure.allProjectRefs
      project <- Project.getProject(ref, structure) // TODO Is it safe to assume that getProject will always return Some?
      if skipParents && !isParentProject(project) || skipRoot && !isRootProject(ref) || !(skipParents || skipRoot)
    } yield {
      implicit val implicitRef = ref
      (projectName |@| scalaVersion |@| baseDirectory |@| compileDirectories |@| testDirectories |@|
        libraries(args contains WithSources) |@| projectDependencies(project)) {
          saveEclipseFiles(args contains CreateSrc, args contains SameTargets)
        }
    }
    results.sequence[ValidationNELString, String] match {
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

  private[sbteclipse] def projectName(implicit ref: ProjectRef, state: State) =
    setting(Keys.name, "Missing project name for %s!" format ref.project, ref)

  private[sbteclipse] def scalaVersion(implicit ref: ProjectRef, state: State) =
    setting(Keys.scalaVersion, "Missing Scala version for %s!" format ref.project, ref)

  private[sbteclipse] def baseDirectory(implicit ref: ProjectRef, state: State) =
    setting(Keys.baseDirectory, "Missing base directory for %s!" format ref.project, ref)

  private[sbteclipse] def compileDirectories(implicit ref: ProjectRef, state: State) =
    (setting(Keys.sourceDirectories, "Missing source directories for %s!" format ref.project, ref) |@|
      setting(Keys.resourceDirectories, "Missing resource directories for %s!" format ref.project, ref) |@|
      setting(Keys.classDirectory, "Missing class directory for %s!" format ref.project, ref)) { Directories }

  private[sbteclipse] def testDirectories(implicit ref: ProjectRef, state: State) =
    (setting(Keys.sourceDirectories, "Missing test source directories for %s!" format ref.project, ref, Configurations.Test) |@|
      setting(Keys.resourceDirectories, "Missing test resource directories for %s!" format ref.project, ref, Configurations.Test) |@|
      setting(Keys.classDirectory, "Missing test class directory for %s!" format ref.project, ref, Configurations.Test)) { Directories }

  private[sbteclipse] def libraries(withSources: Boolean)(implicit ref: ProjectRef, state: State) = {
    val classpathLibraries = evaluateTask(Keys.externalDependencyClasspath in Configurations.Test, ref) match {
      case Some(Value(attributedLibs)) =>
        (attributedLibs.files collect {
          case file @ Path(path) if !(path endsWith "scala-library.jar") => file
        }).success
      case _ => ("Error running externalDependencyClasspath task for %s" format ref.project).failNel
    }
    val (binaries, sources) =
      if (!withSources)
        Map[ModuleID, File]().success[NonEmptyList[String]] -> Map[ModuleID, File]().success[NonEmptyList[String]]
      else {
        val binaries = evaluateTask(Keys.update in Configurations.Test, ref) match {
          case Some(Value(updateReport)) =>
            (for {
              configurationReport <- (updateReport configuration "test").toSeq
              moduleReport <- configurationReport.modules
              (_, file) <- moduleReport.artifacts
            } yield moduleReport.module -> file).toMap.success
          case _ => ("Error running update task for %s" format ref.project).failNel
        }
        val sources = evaluateTask(Keys.updateClassifiers in Configurations.Test, ref) match {
          case Some(Value(updateReport)) =>
            (for {
              configurationReport <- (updateReport configuration "test").toSeq
              moduleReport <- configurationReport.modules
              (artifact, file) <- moduleReport.artifacts if artifact.classifier == Some("sources")
            } yield moduleReport.module -> file).toMap.success
          case _ => ("Error running updateClassifiers task for %s" format ref.project).failNel
        }
        binaries -> sources
      }
    (classpathLibraries |@| binaries |@| sources) { (ls, bs, ss) =>
      val bsToSs = bs flatMap {
        case (moduleId, binaryFile) =>
          ss get moduleId map { sourceFile => binaryFile -> sourceFile }
      }
      ls map { l => Library(l, bsToSs get l) }
    }
  }

  private[sbteclipse] def projectDependencies(project: ResolvedProject)(implicit ref: ProjectRef, state: State) = {
    val projectDependencies = project.dependencies map { dependency =>
      setting(Keys.name, "Missing project name for %s!" format ref.project, dependency.project)
    }
    projectDependencies.sequence[ValidationNELString, String]
  }

  private[sbteclipse] def saveEclipseFiles(createSrc: Boolean,
    sameTargets: Boolean)(
      projectName: String,
      scalaVersion: String,
      baseDirectory: File,
      compileDirectories: Directories,
      testDirectories: Directories,
      libraries: Seq[Library],
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

  private[sbteclipse] def projectXml(name: String) =
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
    libraries: Seq[Library],
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

object Path {
  def unapply(file: File): Option[String] = Some(file.getAbsolutePath)
}

case class Directories(sources: Seq[File], resources: Seq[File], clazz: File)

case class Library(binary: File, sources: Option[File] = None)
