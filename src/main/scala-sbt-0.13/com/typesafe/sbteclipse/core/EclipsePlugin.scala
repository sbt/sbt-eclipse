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
  Keys,
  ProjectRef,
  ResolvedProject,
  Setting,
  SettingKey,
  State,
  TaskKey,
  Def
}
import sbt.Keys.{ baseDirectory, commands }
import scala.util.control.Exception
import scala.xml.{ Attribute, Elem, MetaData, Node, NodeSeq, Null, Text }
import scala.xml.transform.RewriteRule
import scala.language.implicitConversions

object EclipsePlugin {

  /** These settings are injected into individual projects. */
  def eclipseSettings: Seq[Setting[_]] = {
    import EclipseKeys._
    Seq(
      commandName := "eclipse",
      commands += { Eclipse.eclipseCommand(commandName.value) },
      managedClassDirectories := Seq((classesManaged in sbt.Compile).value, (classesManaged in sbt.Test).value),
      preTasks := Seq(),
      skipProject := false,
      withBundledScalaContainers := projectFlavor.value.id == EclipseProjectFlavor.ScalaIDE.id,
      classpathTransformerFactories := defaultClasspathTransformerFactories(withBundledScalaContainers.value),
      projectTransformerFactories := Seq(EclipseRewriteRuleTransformerFactory.Identity),
      configurations := Set(Configurations.Compile, Configurations.Test)) ++ copyManagedSettings(sbt.Compile) ++ copyManagedSettings(sbt.Test)
  }

  def defaultClasspathTransformerFactories(withBundledScalaContainers: Boolean) = {
    if (withBundledScalaContainers)
      Seq(EclipseRewriteRuleTransformerFactory.ClasspathDefault)
    else
      Seq(EclipseRewriteRuleTransformerFactory.Identity)
  }

  /** These settings are injected into the "ThisBuild" scope of sbt, i.e. global acrosss projects. */
  def buildEclipseSettings: Seq[Setting[_]] = {
    import EclipseKeys._
    Seq(
      skipParents := true,
      // Typically, this will be overridden for each project by the project level default of false. However, if a
      // project disables the EclipsePlugin, the project level default won't be set, and so it will fall back to this
      // build level setting, which means the project will be skipped.
      skipProject := true)
  }

  def globalEclipseSettings: Seq[Setting[_]] = {
    import EclipseKeys._
    Seq(
      executionEnvironment := None,
      useProjectId := false,
      withSource := true,
      withJavadoc := true,
      projectFlavor := EclipseProjectFlavor.ScalaIDE,
      createSrc := EclipseCreateSrc.Default,
      eclipseOutput := None,
      relativizeLibs := true)
  }

  def copyManagedSettings(scope: Configuration): Seq[Setting[_]] =
    Seq(
      EclipseKeys.classesManaged in scope := {
        import sbt._
        val classes = (Keys.classDirectory in scope).value
        classes.getParentFile / (classes.getName + "_managed")
      },
      EclipseKeys.generateClassesManaged in scope := EclipseKeys.createSrc.value contains EclipseCreateSrc.ManagedClasses,
      Keys.compile in scope := copyManagedClasses(scope).value)

  // Depends on compile and will ensure all classes being generated from source files in the
  // source_managed space are copied into a class_managed folder.
  // This feature was added for Play Framework. Users wanted to be able to use Play Java without installing Scala IDE.
  // Play Java generates some Scala files (e.g. via the routes compiler and Twirl compiler) and Eclipse doesn't know how
  // to handle these if you put them in a source folder without Scala IDE installed. The workaround added here is to not
  // add the scala managed sources to the classpath, but rather to add the compiled classes to the classpath instead.
  def copyManagedClasses(scope: Configuration) =
    Def.task {
      import sbt._
      val analysis = (Keys.compile in scope).value
      if ((EclipseKeys.generateClassesManaged in scope).value) {
        val classes = (Keys.classDirectory in scope).value
        val srcManaged = (Keys.managedSourceDirectories in scope).value

        // Copy managed classes - only needed in Compile scope
        // This is done to ease integration with Eclipse, but it's doubtful as to how effective it is.
        val managedClassesDirectory = (EclipseKeys.classesManaged in scope).value
        val managedClasses = ((srcManaged ** "*.scala").get ++ (srcManaged ** "*.java").get).map { managedSourceFile =>
          analysis.relations.products(managedSourceFile)
        }.flatten pair rebase(classes, managedClassesDirectory)
        // Copy modified class files
        val managedSet = IO.copy(managedClasses)
        // Remove deleted class files
        (managedClassesDirectory ** "*.class").get.filterNot(managedSet.contains(_)).foreach(_.delete())
      }
      analysis
    }

  object EclipseKeys {
    import EclipseOpts._

    val executionEnvironment: SettingKey[Option[EclipseExecutionEnvironment.Value]] = SettingKey(
      prefix(ExecutionEnvironment),
      "The optional Eclipse execution environment.")

    val skipParents: SettingKey[Boolean] = SettingKey(
      prefix(SkipParents),
      "Skip creating Eclipse files for parent project?")

    val withSource: SettingKey[Boolean] = SettingKey(
      prefix(WithSource),
      "Download and link sources for library dependencies?")

    val withJavadoc: SettingKey[Boolean] = SettingKey(
      prefix(WithJavadoc),
      "Download and link javadoc for library dependencies?")

    val withBundledScalaContainers: SettingKey[Boolean] = SettingKey(
      prefix(WithBundledScalaContainers),
      "Let the generated project use the bundled Scala library of the ScalaIDE plugin")

    val useProjectId: SettingKey[Boolean] = SettingKey(
      prefix(UseProjectId),
      "Use the sbt project id as the Eclipse project name?")

    val classpathTransformerFactories: SettingKey[Seq[EclipseTransformerFactory[RewriteRule]]] = SettingKey(
      prefix("classpathTransformerFactory"),
      "Factories for a rewrite rule for the .classpath file.")

    val projectTransformerFactories: SettingKey[Seq[EclipseTransformerFactory[RewriteRule]]] = SettingKey(
      prefix("projectTransformerFactory"),
      "Factories for a rewrite rule for the .project file.")

    val commandName: SettingKey[String] = SettingKey(
      prefix("command-name"),
      "The name of the command.")

    val configurations: SettingKey[Set[Configuration]] = SettingKey(
      prefix("configurations"),
      "The configurations to take into account.")

    val createSrc: SettingKey[EclipseCreateSrc.ValueSet] = SettingKey(
      prefix("create-src"),
      "The source kinds to be included.")

    val projectFlavor: SettingKey[EclipseProjectFlavor.Value] = SettingKey(
      prefix("project-flavor"),
      "The flavor of project (Scala or Java) to build.")

    val eclipseOutput: SettingKey[Option[String]] = SettingKey(
      prefix("eclipse-output"),
      "The optional output for Eclipse.")

    val preTasks: SettingKey[Seq[TaskKey[_]]] = SettingKey(
      prefix("pre-tasks"),
      "The tasks to be evaluated prior to creating the Eclipse project definition.")

    val relativizeLibs: SettingKey[Boolean] = SettingKey(
      prefix("relativize-libs"),
      "Relativize the paths to the libraries?")

    val skipProject: SettingKey[Boolean] = SettingKey(
      prefix("skipProject"),
      "Skip creating Eclipse files for a given project?")

    lazy val classesManaged: SettingKey[File] = SettingKey(
      prefix("classes-managed"),
      "location where managed class files are copied after compile")

    lazy val managedClassDirectories: SettingKey[Seq[File]] = SettingKey(
      prefix("managed-class-dirs"),
      "locations where managed class files are copied after compile")

    lazy val generateClassesManaged: SettingKey[Boolean] = SettingKey(
      prefix("generate-classes-managed"),
      "If true we generate a managed classes.")

    private def prefix(key: String) = "eclipse-" + key
  }

  object EclipseExecutionEnvironment extends Enumeration {

    val JavaSE18 = Value("JavaSE-1.8")

    val JavaSE17 = Value("JavaSE-1.7")

    val JavaSE16 = Value("JavaSE-1.6")

    val J2SE15 = Value("J2SE-1.5")

    val J2SE14 = Value("J2SE-1.4")

    val J2SE13 = Value("J2SE-1.3")

    val J2SE12 = Value("J2SE-1.2")

    val JRE11 = Value("JRE-1.1")

    val valueSeq: Seq[Value] = JavaSE18 :: JavaSE17 :: JavaSE16 :: J2SE15 :: J2SE14 :: J2SE13 :: J2SE12 :: JRE11 :: Nil
  }

  sealed trait EclipseClasspathEntry {
    def toXml: Node
  }

  object EclipseClasspathEntry {

    case class Src(path: String, output: Option[String], excludes: Seq[String] = Nil) extends EclipseClasspathEntry {
      override def toXml = {
        val classpathentry = output.foldLeft(<classpathentry kind="src" path={ path }/>)((xml, sp) =>
          xml % Attribute("output", Text(sp), Null))

        val excluding = excludes.reduceOption(_ + "|" + _)
        excluding.foldLeft(classpathentry)((xml, excluding) =>
          xml % Attribute("excluding", Text(excluding), Null))
      }
    }

    case class Lib(path: String, sourcePath: Option[String] = None, javadocPath: Option[String] = None) extends EclipseClasspathEntry {
      override def toXml = {
        val classpathentry = sourcePath.foldLeft(<classpathentry kind="lib" path={ path }/>)((xml, sp) =>
          xml % Attribute("sourcepath", Text(sp), Null))

        javadocPath.foldLeft(classpathentry)((xml, jp) =>
          xml.copy(child = <attributes><attribute name="javadoc_location" value={ "jar:file:" + jp + "!/" }/></attributes>))
      }
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

    implicit def eclipseClasspathEntryToNode[T <: EclipseClasspathEntry](t: T): scala.xml.Node = t.toXml

  }

  object EclipseCreateSrc extends Enumeration {

    @deprecated("Always enabled", "4.0.0")
    val Unmanaged = Value

    @deprecated("Use ManagedSrc, ManagedResources, and ManagedClasses", "4.0.0")
    val Managed = Value

    @deprecated("Always enabled", "4.0.0")
    val Source = Value

    @deprecated("Always enabled", "4.0.0")
    val Resource = Value

    val ManagedSrc = Value

    val ManagedResources = Value

    val ManagedClasses = Value

    val Default = ValueSet(ManagedSrc, ManagedResources)

    @deprecated("Does nothing. Uses default values", "4.0.0")
    val All = Default
  }

  object EclipseProjectFlavor extends Enumeration {

    val ScalaIDE = Value

    @deprecated("Use ScalaIDE", "4.0.0")
    val Scala = ScalaIDE

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
          Elem(pf, CpEntry, container(ScalaContainer), scope, child.isEmpty)
        case Elem(pf, CpEntry, attrs, scope, child @ _*) if isScalaReflect(attrs) =>
          NodeSeq.Empty
        case Elem(pf, CpEntry, attrs, scope, child @ _*) if isScalaCompiler(attrs) =>
          Elem(pf, CpEntry, container(ScalaCompilerContainer), scope, child.isEmpty)
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
          Elem(pf, el, attrs, scope, children.isEmpty, newChildren: _*)
        }
        case other => other
      }
    }

    // Return a new transformer object
    override def createTransformer(ref: ProjectRef, state: State): Validation[RewriteRule] =
      Rule.success
  }
}
