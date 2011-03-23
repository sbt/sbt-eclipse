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

object SbtEclipsePlugin extends Plugin {

  override lazy val settings =
    Seq(Keys.commands += eclipseCommand)

  private lazy val eclipseCommand = {
    import Configurations._
    import Keys._

    Command.command("eclipse") { state =>
      def setting[A](key: SettingKey[A], configuration: Configuration = Configurations.Compile) = {
        val extracted = Project.extract(state)
        key in (extracted.currentRef, configuration) get extracted.structure.data
      }

  		logger(state).debug("Trying to create an Eclipse project for you ...")

      if (!(setting(scalaVersion) getOrElse "UNKNOWN" startsWith "2.9")) {
        logger(state).error("Invalid Scala version! Fixed to 2.9 because of the Eclipse plugin.")
        state
      } else {
        XML.save(".project", projectXml(setting(name) getOrElse "default"), "UTF-8", true)
        XML.save(
            ".classpath", 
            classpathXml(
                setting(sourceDirectories, Compile) getOrElse Nil, 
                setting(resourceDirectories, Compile) getOrElse Nil,
                setting(sourceDirectories, Test) getOrElse Nil, 
                setting(resourceDirectories, Test) getOrElse Nil,
                setting(baseDirectory) getOrElse new File("."), // TODO Remove hack!
                state), 
            "UTF-8", 
            true)

        logger(state).info("Successfully created an Eclipse project for you. Have fun!")
    		state
      }
  	}
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

  private def classpathXml(
      compileSourceDirectories: Seq[File],
      compileResourceDirectories: Seq[File],
      testSourceDirectories: Seq[File],
      testResourceDirectories: Seq[File],
      baseDirectory: File,
      state: State) = {

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
}
