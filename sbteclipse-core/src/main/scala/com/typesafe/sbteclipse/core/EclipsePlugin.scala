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

import sbt.{
  Configuration,
  Configurations,
  File,
  Plugin,
  ProjectRef,
  ResolvedProject,
  Setting,
  SettingKey,
  State,
  TaskKey
}
import sbt.Keys.{ baseDirectory, commands }
import scala.util.control.Exception
import scala.xml.{ Attribute, Elem, MetaData, Node, NodeSeq, Null, Text }
import scala.xml.transform.RewriteRule

object EclipsePlugin extends EclipsePlugin

trait EclipsePlugin {

  def eclipseSettings: Seq[Setting[_]] = {
    import EclipseKeys._
    Seq(
      commandName := "eclipse",
      commands <+= (commandName)(Eclipse.eclipseCommand)
    )
  }

  object EclipseKeys {
    import EclipseOpts._

    val executionEnvironment: SettingKey[Option[EclipseExecutionEnvironment.Value]] = SettingKey(
      prefix(ExecutionEnvironment),
      "The optional Eclipse execution environment."
    )

    val skipParents: SettingKey[Boolean] = SettingKey(
      prefix(SkipParents),
      "Skip creating Eclipse files for parent project?"
    )

    val withSource: SettingKey[Boolean] = SettingKey(
      prefix(WithSource),
      "Download and link sources for library dependencies?"
    )

    val useProjectId: SettingKey[Boolean] = SettingKey(
      prefix(UseProjectId),
      "Use the sbt project id as the Eclipse project name?"
    )

    @deprecated("Use classpathTransformerFactories instead!", "2.1.0")
    val classpathEntryTransformerFactory: SettingKey[EclipseTransformerFactory[Seq[EclipseClasspathEntry] => Seq[EclipseClasspathEntry]]] = SettingKey(
      prefix("classpathEntryTransformerFactory"),
      "Creates a transformer for classpath entries."
    )

    val classpathTransformerFactories: SettingKey[Seq[EclipseTransformerFactory[RewriteRule]]] = SettingKey(
      prefix("classpathTransformerFactory"),
      "Factories for a rewrite rule for the .classpath file."
    )

    val projectTransformerFactories: SettingKey[Seq[EclipseTransformerFactory[RewriteRule]]] = SettingKey(
      prefix("projectTransformerFactory"),
      "Factories for a rewrite rule for the .project file."
    )

    val commandName: SettingKey[String] = SettingKey(
      prefix("command-name"),
      "The name of the command."
    )

    val configurations: SettingKey[Set[Configuration]] = SettingKey(
      prefix("configurations"),
      "The configurations to take into account."
    )

    val createSrc: SettingKey[EclipseCreateSrc.ValueSet] = SettingKey(
      prefix("create-src"),
      "The source kinds to be included."
    )

    val projectFlavor: SettingKey[EclipseProjectFlavor.Value] = SettingKey(
      prefix("project-flavor"),
      "The flavor of project (Scala or Java) to build."
    )

    val eclipseOutput: SettingKey[Option[String]] = SettingKey(
      prefix("eclipse-output"),
      "The optional output for Eclipse."
    )

    val preTasks: SettingKey[Seq[TaskKey[_]]] = SettingKey(
      prefix("pre-tasks"),
      "The tasks to be evaluated prior to creating the Eclipse project definition."
    )

    val relativizeLibs: SettingKey[Boolean] = SettingKey(
      prefix("relativize-libs"),
      "Relativize the paths to the libraries?"
    )

    val skipProject: SettingKey[Boolean] = SettingKey(
      prefix("skipProject"),
      "Skip creating Eclipse files for a given project?"
    )

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

    val valueSeq: Seq[Value] = JavaSE17 :: JavaSE16 :: J2SE15 :: J2SE14 :: J2SE13 :: J2SE12 :: JRE11 :: Nil
  }

  sealed trait EclipseClasspathEntry {
    def toXml: Node
  }

  object EclipseClasspathEntry {

    case class Src(path: String, output: Option[String]) extends EclipseClasspathEntry {
      override def toXml =
        output.foldLeft(<classpathentry kind="src" path={ path }/>)((xml, sp) =>
          xml % Attribute("output", Text(sp), Null))
    }

    case class Lib(path: String, sourcePath: Option[String] = None) extends EclipseClasspathEntry {
      override def toXml =
        sourcePath.foldLeft(<classpathentry kind="lib" path={ path }/>)((xml, sp) =>
          xml % Attribute("sourcepath", Text(sp), Null)
        )
    }

    case class Project(name: String) extends EclipseClasspathEntry {
      override def toXml =
        <classpathentry kind="src" path={ "/" + name } exported="true" combineaccessrules="false"/>
    }

    case class Con(path: String) extends EclipseClasspathEntry {
      override def toXml = <classpathentry kind="con" path={ path }/>
    }

    case class Output(path: String) extends EclipseClasspathEntry {
      override def toXml = <classpathentry kind="output" path={ path }/>
    }

    implicit def eclipseClasspathEntryToNode[T <: EclipseClasspathEntry](t: T) = t.toXml

  }

  object EclipseCreateSrc extends Enumeration {

    val Unmanaged = Value

    val Managed = Value

    val Source = Value

    val Resource = Value

    val Default = ValueSet(Unmanaged, Source)

    val All = ValueSet(Unmanaged, Managed, Source, Resource)
  }

  object EclipseProjectFlavor extends Enumeration {

    val Scala = Value

    val Java = Value
  }

  trait EclipseTransformerFactory[A] {
    def createTransformer(ref: ProjectRef, state: State): Validation[A]
  }

  object EclipseClasspathEntryTransformerFactory {

    object Identity extends EclipseTransformerFactory[Seq[EclipseClasspathEntry] => Seq[EclipseClasspathEntry]] {
      import scalaz.Scalaz._
      override def createTransformer(
        ref: ProjectRef,
        state: State): Validation[Seq[EclipseClasspathEntry] => Seq[EclipseClasspathEntry]] = {
        val transformer = (entries: Seq[EclipseClasspathEntry]) => entries
        transformer.success
      }
    }
  }

  object EclipseRewriteRuleTransformerFactory {

    object IdentityRewriteRule extends RewriteRule {
      override def transform(node: Node): Node = node
    }

    object ClasspathDefaultRule extends RewriteRule {

      private val CpEntry = "classpathentry"

      private val ScalaContainer = "org.scala-ide.sdt.launching.SCALA_CONTAINER"

      private val ScalaCompilerContainer = "org.scala-ide.sdt.launching.SCALA_COMPILER_CONTAINER"

      override def transform(node: Node): Seq[Node] = node match {
        case Elem(pf, CpEntry, attrs, scope, child @ _*) if isScalaLibrary(attrs) =>
          Elem(pf, CpEntry, container(ScalaContainer), scope, child: _*)
        case Elem(pf, CpEntry, attrs, scope, child @ _*) if isScalaReflect(attrs) =>
          NodeSeq.Empty
        case Elem(pf, CpEntry, attrs, scope, child @ _*) if isScalaCompiler(attrs) =>
          Elem(pf, CpEntry, container(ScalaCompilerContainer), scope, child: _*)
        case other =>
          other
      }

      private def container(name: String) =
        Attribute("kind", Text("con"), Attribute("path", Text(name), Null))

      private def isScalaLibrary(metaData: MetaData) =
        metaData("kind") == Text("lib") &&
          (Option(metaData("path").text) map (_ contains "scala-library") getOrElse false)

      private def isScalaReflect(metaData: MetaData) =
        metaData("kind") == Text("lib") &&
          (Option(metaData("path").text) map (_ contains "scala-reflect") getOrElse false)

      private def isScalaCompiler(metaData: MetaData) =
        metaData("kind") == Text("lib") &&
          (Option(metaData("path").text) map (_ contains "scala-compiler") getOrElse false)
    }

    object Identity extends EclipseTransformerFactory[RewriteRule] {
      import scalaz.Scalaz._
      override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] =
        IdentityRewriteRule.success
    }

    object ClasspathDefault extends EclipseTransformerFactory[RewriteRule] {
      import scalaz.Scalaz._
      override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] =
        ClasspathDefaultRule.success
    }
  }

  // Represents the transformation type
  object DefaultTransforms {
    case class Append(v: Node*) extends (Seq[Node] => Seq[Node]) {
      def apply(children: Seq[Node]) = children ++ v
    }
    case class Prepend(v: Node*) extends (Seq[Node] => Seq[Node]) {
      def apply(children: Seq[Node]) = v ++ children
    }
    case class Remove(v: Node*) extends (Seq[Node] => Seq[Node]) {
      def apply(children: Seq[Node]) = children.diff(v)
    }
    case class ReplaceWith(v: Node*) extends (Seq[Node] => Seq[Node]) {
      def apply(children: Seq[Node]) = v
    }
    case class InsertBefore(pred: Node => Boolean, v: Node*) extends (Seq[Node] => Seq[Node]) {
      def apply(children: Seq[Node]) = {
        val (before, after) = children.span(pred)
        before ++ v ++ after
      }
    }
  }

  def transformNode(parentName: String, transform: Seq[Node] => Seq[Node]) =
    new ChildTransformer(parentName, transform)

  case class ChildTransformer(
      parentName: String,
      transformation: Seq[Node] => Seq[Node]) extends EclipseTransformerFactory[RewriteRule] {

    import scalaz.Scalaz._

    /**
     * Rewrite rule that searches for a certain parent node and
     * applies a transformation to its children
     */
    object Rule extends RewriteRule {
      override def transform(node: Node): Seq[Node] = node match {
        case Elem(pf, el, attrs, scope, children @ _*) if (el == parentName) => {
          val newChildren = transformation(children)
          Elem(pf, el, attrs, scope, newChildren: _*)
        }
        case other => other
      }
    }

    // Return a new transformer object
    override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] =
      Rule.success
  }
}
