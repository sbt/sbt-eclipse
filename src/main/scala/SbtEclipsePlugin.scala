/*
 * Copyright 2011 Weigle Wilczek GmbH
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

package com.weiglewilczek.sbteclipse

import java.io.File
import sbt._
import sbt.CommandSupport.logger
import scala.xml.{ NodeSeq, XML }
import scalaz.{ Failure, NonEmptyList, Success, Validation }
import scalaz.Scalaz._

object SbtEclipsePlugin extends Plugin {

  override lazy val settings = Seq(Keys.commands += eclipseCommand)

  private lazy val eclipseCommand = Command.command("eclipse") { state =>

    val structure = Project.extract(state).structure

    def setting[A](
        key: SettingKey[A], 
        errorMessage: => String,
        configuration: Configuration = Configurations.Compile)(
        implicit projectRef: ProjectReference): Validation[NonEmptyList[String], A] = {
      key in (projectRef, configuration) get structure.data match {
        case Some(a) =>
          logger(state).debug("Setting for key %s = %s".format(key.key, a))
          a.success
        case None => errorMessage.failNel
      }
    }

    def saveEclipseFiles(
        projectName: String,
        scalaVersion: String,
        baseDirectory: File,
        compileDirectories: Directories,
        testDirectories: Directories,
        libraries: Seq[File],
        projectDependencies: Seq[String]) {

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

      def classpathXml(
          compileSourceDirectories: Seq[File],
          compileResourceDirectories: Seq[File],
          classDirectory: File,
          testSourceDirectories: Seq[File],
          testResourceDirectories: Seq[File],
          testClassDirectory: File,
          baseDirectory: File) = {

        def srcEntries(directories: Seq[File], output: File) =
          directories flatMap { directory =>
            if (directory.exists) {
              logger(state).debug("""Creating src entry for directory "%s".""" format directory)
              val relative = IO.relativize(baseDirectory, directory).get
              val relativeOutput = IO.relativize(baseDirectory, output).get
              <classpathentry kind="src" path={ relative.toString } output={ relativeOutput.toString }/>
            } else {
              logger(state).debug("""Skipping src entry for non-existent directory "%s".""" format directory)
              NodeSeq.Empty
            }
          }

        def libEntries =
          libraries collect {
            case PathExtractor(path) if !(path endsWith "scala-library.jar") => path
          } flatMap { path =>
            logger(state).debug("""Creating lib entry for dependency "%s".""" format path)
            <classpathentry kind="lib" path={ path }/>
          }

        def projectDependencyEntries = projectDependencies flatMap { projectDependency =>
          logger(state).debug("""Creating project dependency entry for "%s".""" format projectDependency)
          <classpathentry kind="src" path={"/" + projectDependency } combineaccessrules="false"/>
        }

        <classpath>{
          srcEntries(compileSourceDirectories, classDirectory) ++
          srcEntries(compileResourceDirectories, classDirectory) ++
          srcEntries(testSourceDirectories, testClassDirectory) ++
          srcEntries(testResourceDirectories, testClassDirectory) ++
          libEntries ++
          projectDependencyEntries ++
          <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>
          <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"/>
          <classpathentry kind="output" path="target/classes"/>
        }</classpath>
      }

      XML.save((baseDirectory / ".project").getAbsolutePath , projectXml(projectName))
      XML.save((baseDirectory / ".classpath").getAbsolutePath, 
          classpathXml(compileDirectories.sources,
              compileDirectories.resources,
              compileDirectories.clazz,
              testDirectories.sources,
              testDirectories.resources,
              testDirectories.clazz,
              baseDirectory),
          "UTF-8",
          true)
    }

    logger(state).debug("Trying to create an Eclipse project for you ...")

    (structure.allProjectRefs map { ref: ProjectRef =>
      implicit val implicitRef = ref

      val projectName = setting(Keys.name, "Missing project name for %s!" format ref.project)
      val scalaVersion = setting(Keys.scalaVersion, "Missing Scala version for %s!" format ref.project) match {
        case f @ Failure(_) => f
        case Success(s) if (s startsWith "2.9") => s.success
        case _ => "Only for Scala 2.9!".failNel
      }
      val baseDirectory = setting(Keys.baseDirectory, "Missing base directory for %s!" format ref.project)
      val compileDirectories = (setting(Keys.unmanagedSourceDirectories, "Missing unmanaged source directories for %s!" format ref.project) |@|
          setting(Keys.unmanagedResourceDirectories, "Missing unmanaged resource directories for %s!" format ref.project) |@|
          setting(Keys.classDirectory, "Missing class directory for %s!" format ref.project)) {
        Directories
      }
      val testDirectories = (setting(Keys.unmanagedSourceDirectories, "Missing unmanaged test source directories for %s!" format ref.project, Configurations.Test) |@|
          setting(Keys.unmanagedResourceDirectories, "Missing unmanaged test resource directories for %s!" format ref.project, Configurations.Test) |@|
          setting(Keys.classDirectory, "Missing test class directory for %s!" format ref.project, Configurations.Test)) {
        Directories
      }
      val libraries = EvaluateTask.evaluateTask(structure,
          Keys.externalDependencyClasspath in Configurations.Test, // Configurations.Test contains items from Configurations.Compile!
          state,
          ref,
          false,
          EvaluateTask.SystemProcessors) match {
        case Some(Value(attributedLibs)) => (attributedLibs map { _.data }).success
        case Some(Inc(_)) => ("Error determining compile libraries for %s" format ref.project).failNel
        case None => ("Missing compile libraries for %s!" format ref.project).failNel
      }
      val projectDependencies = (Project.getProject(ref, structure) match {
        case None => Seq(("Cannot resolve project for reference %s!" format ref.project).failNel)
        case Some(project) => project.dependencies map { dependency =>
          setting(Keys.name, "Missing project name for %s!" format ref.project)(dependency.project)
        }
      }).sequence[({type L[A]=Validation[NonEmptyList[String], A]})#L, String]

      (projectName |@| 
          scalaVersion |@| 
          baseDirectory |@| 
          compileDirectories |@| 
          testDirectories |@| 
          libraries |@| 
          projectDependencies) {
        saveEclipseFiles
      }
    }).sequence[({type L[A]=Validation[NonEmptyList[String], A]})#L, Unit] match {
      case Success(_) =>
        logger(state).info("Successfully created one or more Eclipse projects for you. Have fun!")
        state
      case Failure(errors) =>
        logger(state).error(errors.list mkString ", ")
        state.fail
    }
  }

  private case class Directories(sources: Seq[File], resources: Seq[File], clazz: File)

  private object PathExtractor {
    def unapply(file: File): Option[String] = Some(file.getAbsolutePath)
  }
}
