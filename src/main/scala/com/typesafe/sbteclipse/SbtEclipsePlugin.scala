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
import sbt.{ Path => _,  _ }
import sbt.complete.Parsers._
import scala.xml.{ Elem, NodeSeq, PrettyPrinter }
import scalaz.{ Failure, NonEmptyList, Success, Validation }
import scalaz.Scalaz._

object SbtEclipsePlugin extends Plugin {

  override def settings = Seq(Keys.commands += eclipseCommand)

  private val (createSrc, sameTargets, skipRoot, withSources) = ("create-src", "same-targets", "skip-root", "with-sources")

  private val args = (Space ~> createSrc | 
      Space ~> sameTargets | 
      Space ~> skipRoot | 
      Space ~> withSources).*

  private val eclipseCommand = Command("eclipse")(_ => args) { (state, args) =>
    implicit val implicitState = state

    logDebug("Trying to create an Eclipse project for you ...")

    (for (ref <- structure.allProjectRefs if (!(args contains skipRoot) || !isRootProject(ref))) yield {

      val projectName = setting(Keys.name, "Missing project name for %s!" format ref.project, ref)
      val scalaVersion = setting(Keys.scalaVersion, "Missing Scala version for %s!" format ref.project, ref)
      val baseDirectory = setting(Keys.baseDirectory, "Missing base directory for %s!" format ref.project, ref)
      val compileDirectories = (setting(Keys.unmanagedSourceDirectories, "Missing unmanaged source directories for %s!" format ref.project, ref) |@|
          setting(Keys.unmanagedResourceDirectories, "Missing unmanaged resource directories for %s!" format ref.project, ref) |@|
          setting(Keys.classDirectory, "Missing class directory for %s!" format ref.project, ref)) {
        Directories
      }
      val testDirectories = (setting(Keys.unmanagedSourceDirectories, "Missing unmanaged test source directories for %s!" format ref.project, ref, Configurations.Test) |@|
          setting(Keys.unmanagedResourceDirectories, "Missing unmanaged test resource directories for %s!" format ref.project, ref, Configurations.Test) |@|
          setting(Keys.classDirectory, "Missing test class directory for %s!" format ref.project, ref, Configurations.Test)) {
        Directories
      }
      val libraries = {
        val classpathLibraries = evaluateTask(Keys.externalDependencyClasspath in Configurations.Test, ref) match {
          case Some(Value(attributedLibs)) => 
            (attributedLibs.files collect {
              case file @ Path(path) if !(path endsWith "scala-library.jar") => file
            }).success
          case _ => ("Error running externalDependencyClasspath task for %s" format ref.project).failNel
        }
        val (binaries, sources) =
          if (!(args contains withSources))
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
          val bsToSs = bs flatMap { case (moduleId, binaryFile) =>
            ss get moduleId map { sourceFile => binaryFile -> sourceFile }
          }
          ls map { l => Library(l, bsToSs get l) }
        }
      }
      val projectDependencies = (Project.getProject(ref, structure) match {
        case None => Seq(("Cannot resolve project for reference %s!" format ref.project).failNel)
        case Some(project) => project.dependencies map { dependency =>
          setting(Keys.name, "Missing project name for %s!" format ref.project, dependency.project)
        }
      }).sequence[({type A[B]=Validation[NonEmptyList[String], B]})#A, String]
      (projectName |@| 
          scalaVersion |@| 
          baseDirectory |@| 
          compileDirectories |@| 
          testDirectories |@| 
          libraries |@| 
          projectDependencies) {
        saveEclipseFiles(args contains createSrc, args contains sameTargets)
      }
    }).sequence[({type A[B]=Validation[NonEmptyList[String], B]})#A, String] match {
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

  private def saveEclipseFiles(
      createSrc: Boolean, sameTargets: Boolean)(
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

  private def projectXml(name: String) =
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


  def classpathXml(
      createSrc: Boolean,
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

    def srcEntries(directories: Seq[File], output: File) =
      directories flatMap { directory =>
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

    def libEntries =
      libraries flatMap {
        case Library(Path(binary), Some(Path(sources))) =>
          logDebug("""Creating lib entry with source attachment for dependency "%s".""" format binary)
          <classpathentry kind="lib" path={ binary } sourcepath={ sources }/>
        case Library(Path(binary), _) =>
          logDebug("""Creating lib entry for dependency "%s".""" format binary)
          <classpathentry kind="lib" path={ binary }/>
      }

    def projectDependencyEntries = projectDependencies.distinct flatMap { projectDependency =>
      logDebug("""Creating project dependency entry for "%s".""" format projectDependency)
      <classpathentry kind="src" path={"/" + projectDependency } exported="true" combineaccessrules="false"/>
    }

    <classpath>{
      srcEntries(compileDirectories.sources, compileDirectories.clazz) ++
      srcEntries(compileDirectories.resources, compileDirectories.clazz) ++
      srcEntries(testDirectories.sources, testDirectories.clazz) ++
      srcEntries(testDirectories.resources, testDirectories.clazz) ++
      libEntries ++
      projectDependencyEntries ++
      <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>
      <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"/>
      <classpathentry kind="output" path={ outputPath(compileDirectories.clazz) }/>
    }</classpath>
  }

}

object Path {
  def unapply(file: File): Option[String] = Some(file.getAbsolutePath)
}

case class Directories(sources: Seq[File], resources: Seq[File], clazz: File)

case class Library(binary: File, sources: Option[File] = None)
