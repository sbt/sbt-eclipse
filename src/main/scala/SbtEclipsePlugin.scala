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
import sbt.{ Command, Configuration, Configurations, IO, Keys, Plugin, Project, SettingKey, State }
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
          scalaVersion: String,
          name: String,
          sourceDirectories: Seq[File],
          resourceDirectories: Seq[File],
          testSourceDirectories: Seq[File],
          testResourceDirectories: Seq[File],
          baseDirectory: File) {
        XML.save(".project", projectXml(name))
        XML.save(
            ".classpath", 
            classpathXml(
                sourceDirectories, 
                resourceDirectories, 
                testSourceDirectories, 
                testResourceDirectories, 
                baseDirectory), 
            "UTF-8", 
            true)
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

      def classpathXml(
          compileSourceDirectories: Seq[File],
          compileResourceDirectories: Seq[File],
          testSourceDirectories: Seq[File],
          testResourceDirectories: Seq[File],
          baseDirectory: File) = {

        def srcEntries(directories: Seq[File]): NodeSeq =
          directories flatMap { directory =>
            if (directory.exists) {
              logger(state).debug("""Creating src entry for directory "%s".""".format(directory))
              val relative = IO.relativize(baseDirectory, directory).get // TODO Better handling!
              <classpathentry kind="src" path={ relative.toString } output="target/classes"/>
            } else {
              logger(state).debug("""Skipping src entry for non-existent directory "%s".""".format(directory))
              NodeSeq.Empty
            }
          }

        <classpath>{
        	srcEntries(compileSourceDirectories) ++
        	srcEntries(compileResourceDirectories) ++
        	srcEntries(testSourceDirectories) ++
        	srcEntries(testResourceDirectories) ++
        	<classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>
        	<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"/>
        	<classpathentry kind="output" path="target/classes"/>
        }</classpath>
      }

  		logger(state).debug("Trying to create an Eclipse project for you ...")
      (setting(Keys.scalaVersion, "Only for Scala 2.9!") |@|
          setting(Keys.name, "Missing name!") |@|
          setting(Keys.sourceDirectories, "Missing source directories!") |@|
          setting(Keys.resourceDirectories, "Missing resource directories!") |@|
          setting(Keys.sourceDirectories, "Missing test source directories!", Configurations.Test) |@|
          setting(Keys.resourceDirectories, "Missing test resource directories!", Configurations.Test) |@|
          setting(Keys.baseDirectory, "Missing base directory!")) {
        saveEclipseFiles
      } match {
        case Success(_) =>
          logger(state).info("Successfully created an Eclipse project for you. Have fun!")
        case Failure(errors) =>
          logger(state).error(errors.list mkString ", ")
      }
  		state
  	}
	}
}
