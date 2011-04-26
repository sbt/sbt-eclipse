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

  override lazy val settings =
    Seq(Keys.commands += eclipseCommand)

  private lazy val eclipseCommand = {

    Command.command("eclipse") { state =>

      def setting[A](
          key: SettingKey[A], 
          errorMessage: => String,
          configuration: Configuration = Configurations.Compile): Validation[NonEmptyList[String], A] = {
        val extracted = Project.extract(state)
        key in (extracted.currentRef, configuration) get extracted.structure.data match {
          case Some(a) => a.success
          case None => errorMessage.failNel
        }
      }

      def saveEclipseFiles(
          projectName: String,
          scalaVersion: String,
          baseDirectory: File,
          compileDirectories: Directories,
          testDirectories: Directories,
          compileLibraries: Seq[File]) {

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

              def srcEntries(directories: Seq[File], output: File): NodeSeq =
                directories flatMap { directory =>
                  if (directory.exists) {
                    logger(state).debug("""Creating src entry for directory "%s".""".format(directory))
                    val relative = IO.relativize(baseDirectory, directory).get
                    val relativeOutput = IO.relativize(baseDirectory, output).get
                    <classpathentry kind="src" path={ relative.toString } output={ relativeOutput.toString } />
                  } else {
                    logger(state).debug("""Skipping src entry for non-existent directory "%s".""".format(directory))
                    NodeSeq.Empty
                  }
                }

             def libEntries(libs: Seq[File]): NodeSeq =
               libs collect {
                 case Path(path) if !(path endsWith "scala-library.jar") => path
               } flatMap { path =>
                 logger(state).debug("""Creating lib entry for dependency "%s".""".format(path))
                 <classpathentry kind="lib" path={ path } />
               }

              <classpath>{
                srcEntries(compileSourceDirectories, classDirectory) ++
                srcEntries(compileResourceDirectories, classDirectory) ++
                srcEntries(testSourceDirectories, testClassDirectory) ++
                srcEntries(testResourceDirectories, testClassDirectory) ++
                libEntries(compileLibraries) ++
                <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>
                <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"/>
                <classpathentry kind="output" path="target/classes"/>
              }</classpath>
            }

        XML.save(".project", projectXml(projectName))
        XML.save(
            ".classpath", 
            classpathXml(
                compileDirectories.sources, 
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

      val projectName = setting(Keys.name, "Missing project name!")
      val scalaVersion =
        setting(Keys.scalaVersion, "Missing Scala version!") match {
          case f @ Failure(_) => f
          case Success(s) if (s startsWith "2.9") => s.success
          case _ => "Only for Scala 2.9!".failNel
        }
      val baseDirectory = setting(Keys.baseDirectory, "Missing base directory!")
      val compileDirectories =
        (setting(Keys.sourceDirectories, "Missing source directories!") |@|
            setting(Keys.resourceDirectories, "Missing resource directories!") |@|
            setting(Keys.classDirectory, "Missing class directory!")) {
          Directories
        }
      val testDirectories =
        (setting(Keys.sourceDirectories, "Missing test source directories!", Configurations.Test) |@|
            setting(Keys.resourceDirectories, "Missing test resource directories!", Configurations.Test) |@|
            setting(Keys.classDirectory, "Missing test class directory!", Configurations.Test)) {
          Directories
        }
      val compileLibraries =
        Project.evaluateTask(Keys.externalDependencyClasspath in Configurations.Compile, state) match {
          case Some(Value(attributedLibs)) => (attributedLibs map { _.data }).success
          case Some(Inc(_)) => "Error determining compile libraries: %s".failNel
          case None => "Missing compile libraries!".failNel
        }

      (projectName |@| scalaVersion |@| baseDirectory |@| compileDirectories |@| testDirectories |@| compileLibraries) {
        saveEclipseFiles
      } match {
        case Success(_) =>
          logger(state).info("Successfully created an Eclipse project for you. Have fun!")
          state
        case Failure(errors) =>
          logger(state).error(errors.list mkString ", ")
          state.fail
      }
    }
  }

  private case class Directories(sources: Seq[File], resources: Seq[File], clazz: File)

  private object Path {
    def unapply(file: File): Option[String] =
      Some(file.getAbsolutePath)
  }
}
