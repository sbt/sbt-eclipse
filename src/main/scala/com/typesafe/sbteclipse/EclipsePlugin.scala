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

import sbt.{ File, Plugin, Setting, SettingKey }
import sbt.Keys.{ baseDirectory, commands }
import scala.xml.Elem

object EclipsePlugin extends Plugin {

  override def settings: Seq[Setting[_]] = {
    import EclipseKeys._
    Seq(
      executionEnvironment := None,
      skipParents := true,
      withSource := false,
      commandName := "eclipse",
      classpathEntryCollector := eclipseDefaultClasspathEntryCollector,
      commands <+= (
        executionEnvironment,
        skipParents,
        withSource,
        commandName,
        classpathEntryCollector
      )(Eclipse.eclipseCommand)
    )
  }

  def eclipseDefaultClasspathEntryCollector: PartialFunction[ClasspathEntry, ClasspathEntry] = {
    case ClasspathEntry.Lib(path, _) if path contains "scala-library.jar" =>
      ClasspathEntry.Con("org.scala-ide.sdt.launching.SCALA_CONTAINER")
    case ClasspathEntry.Lib(path, _) if path contains "scala-compiler.jar" =>
      ClasspathEntry.Con("org.scala-ide.sdt.launching.SCALA_COMPILER_CONTAINER")
    case entry =>
      entry
  }

  object EclipseKeys {
    import EclipseOpts._

    val executionEnvironment: SettingKey[Option[EclipseExecutionEnvironment.Value]] =
      SettingKey[Option[EclipseExecutionEnvironment.Value]](
        prefix(ExecutionEnvironment),
        "The optional Eclipse execution environment.")

    val skipParents: SettingKey[Boolean] =
      SettingKey[Boolean](
        prefix(SkipParents),
        "Skip creating Eclipse files for parent project?")

    val withSource: SettingKey[Boolean] =
      SettingKey[Boolean](
        prefix(WithSource),
        "Download and link sources for library dependencies?")

    val commandName: SettingKey[String] =
      SettingKey[String](
        prefix("command-name"),
        "The name of the command.")

    val classpathEntryCollector: SettingKey[PartialFunction[ClasspathEntry, ClasspathEntry]] =
      SettingKey[PartialFunction[ClasspathEntry, ClasspathEntry]](
        prefix("classpathEntryCollector"),
        "Determines how classpath entries are filtered and transformed before written into XML.")

    private def prefix(key: String) = "eclipse-" + key
  }

  object EclipseExecutionEnvironment extends Enumeration {

    val JavaSE17 = Value("JavaSE-1.7")

    val JavaSE16 = Value("JavaSE-1.6")

    val J2SE15 = Value("J2SE-1.5")

    val J2SE14 = Value("J2SE-1.4")

    val J2SE13 = Value("J2SE-1.3")

    val J2SE12 = Value("J2SE-1.2")

    val JRE11 = Value("JRE-1.1")
  }

  sealed trait ClasspathEntry {
    def toXml: Elem
  }

  object ClasspathEntry {

    case class Src(path: String, output: String) extends ClasspathEntry {
      override def toXml = <classpathentry kind="src" path={ path } output={ output }/>
    }

    case class Lib(path: String, source: Option[String] = None) extends ClasspathEntry {
      override def toXml = <classpathentry kind="lib" path={ path }/>
    }

    case class Project(name: String) extends ClasspathEntry {
      override def toXml = <classpathentry kind="src" path={ "/" + name } exported="true" combineaccessrules="false"/>
    }

    case class Con(path: String) extends ClasspathEntry {
      override def toXml = <classpathentry kind="con" path={ path }/>
    }

    case class Output(path: String) extends ClasspathEntry {
      override def toXml = <classpathentry kind="output" path={ path }/>
    }
  }
}
