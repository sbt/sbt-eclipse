import sbt._
import sbt.Keys._
import java.io.FileInputStream
import java.util.Properties
import scala.collection.JavaConverters._
import scala.xml.XML
import sys.error

TaskKey[Unit]("verify-project-xml") := {
  println("running xml test")
  val dir = baseDirectory.value
  val projectDescription = XML.loadFile(dir / ".project")
  verify("name", "sbteclipse-test",  (projectDescription \ "name").text)
  verify("buildCommand", "org.scala-ide.sdt.core.scalabuilder", (projectDescription \ "buildSpec" \ "buildCommand" \ "name").text)
  verify("natures", Set("org.scala-ide.sdt.core.scalanature", "org.eclipse.jdt.core.javanature"), (projectDescription \ "natures" \ "nature").map(_.text).toSet)

  def verify[A](name: String, expected: A, actual: A) = if (actual != expected) {
    error("Expected .project to contain %s '%s', but was '%s'!".format(name, expected, actual))
  }
}

TaskKey[Unit]("verify-project-xml-java") := {
  val dir = baseDirectory.value
  val projectDescription = XML.loadFile(dir / "java" / ".project")
  verify("name", "java",  (projectDescription \ "name").text)
  verify("buildCommand", "org.eclipse.jdt.core.javabuilder", (projectDescription \ "buildSpec" \ "buildCommand" \ "name").text)
  verify("natures", "org.eclipse.jdt.core.javanature", (projectDescription \ "natures" \ "nature").text)

  def verify[A](name: String, expected: A, actual: A) = if (actual != expected) {
    error("Expected .project to contain %s '%s', but was '%s'!".format(name, expected, actual))
  }
}

TaskKey[Unit]("verify-project-xml-scala") := {
  val dir = baseDirectory.value
  val projectDescription = XML.loadFile(dir / "scala" / ".project")
  val classpath = XML.loadFile(dir / "scala" / ".classpath")
  if (!(classpath.child contains <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>)) error(
    """Expected .classpath of scala project to contain <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>: %s""" format classpath
  )
  verify("name", "scala",  (projectDescription \ "name").text)
  verify("buildCommand", "org.scala-ide.sdt.core.scalabuilder", (projectDescription \ "buildSpec" \ "buildCommand" \ "name").text)
  verify("natures", Set("org.scala-ide.sdt.core.scalanature", "org.eclipse.jdt.core.javanature"), (projectDescription \ "natures" \ "nature").map(_.text).toSet)

  def verify[A](name: String, expected: A, actual: A) = if (actual != expected) {
    error("Expected .project to contain %s '%s', but was '%s'!".format(name, expected, actual))
  }
}

TaskKey[Unit]("verify-project-xml-subd") := {
  val dir = baseDirectory.value
  val projectDescription = XML.loadFile(dir / "sub" / "subd" / ".project")
  val name = (projectDescription \ "name").text
  if (name != "subd-id") error(
    "Expected .project to contain name '%s', but was '%s'!".format("subd-id", name)
  )
}

TaskKey[Unit]("verify-project-xml-sube") := {
  val dir = baseDirectory.value
  val projectDescription = XML.loadFile(dir / "sub" / "sube" / ".project")
  val name = (projectDescription \ "name").text
  if (name != "sube") error(
    "Expected .project to contain name '%s', but was '%s'!".format("sube", name)
  )
}

TaskKey[Unit]("verify-classpath-xml-root") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of root project not to contain duplicate entries: %s" format classpath)
  // src entries
  if (!(classpath.child contains <classpathentry kind="src" path="src/main/scala" />))
    error("""Expected .classpath of root project to contain <classpathentry kind="src" path="src/main/scala" /> """)
  if (!(classpath.child contains <classpathentry kind="src" path="src/main/java" />))
    error("""Expected .classpath of root project to contain <classpathentry kind="src" path="src/main/java" /> """)
  if (!(classpath.child contains <classpathentry kind="src" path="src/test/scala" />))
    error("""Expected .classpath of root project to contain <classpathentry kind="src" path="src/test/scala" /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/java") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/test/java" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/main/resources") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/main/resources" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/resources") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/test/resources" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.12/src_managed/main") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="...src_managed/main" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.12/src_managed/test") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="...src_managed/test" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.12/resource_managed/main") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="...resource_managed/main" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.12/resource_managed/test") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="...resource_managed/test" output="..." /> """)
  // lib entries without sources
  // Broken: https://github.com/sbt/sbt/issues/5078
  //if (!(classpath.child contains <classpathentry kind="lib" path="./lib_managed/jars/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0.jar" />))
  //  error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="./lib_managed/jars/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0.jar" />: %s""" format classpath)
  // other entries
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "scala-library.jar")
    error("""Not expected .classpath of root project to contain <classpathentry path="...scala-library.jar" ... /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "scala-compiler.jar")
    error("""Not expected .classpath of root project to contain <classpathentry path="...scala-compiler.jar" ... /> """)
  if (!(classpath.child contains <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/> """)
  if (!(classpath.child contains <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_COMPILER_CONTAINER"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_COMPILER_CONTAINER"/> """)
  if (!(classpath.child contains <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/> """)
  if (!(classpath.child contains <classpathentry kind="output" path="bin"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="output" path="bin"/> """)
}

TaskKey[Unit]("verify-classpath-xml-sub") := {
  val dir = baseDirectory.value
  val home = System.getProperty("user.home")
  val classpath = XML.loadFile(dir / "sub" / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of sub project not to contain duplicate entries: %s" format classpath)
  // lib entries with sources
  // Broken: https://github.com/sbt/sbt/issues/5078
  //if (!(classpath.child contains <classpathentry kind="lib" path="../lib_managed/jars/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0.jar" sourcepath="../lib_managed/srcs/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0-sources.jar" />))
  //  error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="../lib_managed/jars/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0.jar" sourcepath="../lib_managed/srcs/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0-sources.jar" />: %s""" format classpath)
  // other entries
  if (!(classpath.child contains <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"/>: %s""" format classpath)
}

TaskKey[Unit]("verify-classpath-xml-suba") := {
  val dir = baseDirectory.value
  val home = System.getProperty("user.home")
  val classpath = XML.loadFile(dir / "sub" / "suba" / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of suba project not to contain duplicate entries: %s" format classpath)
  // src entries
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.12/resource_managed/main") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="...resource_managed/main" output="..." />: %s """ format classpath)
  if ((classpath.child contains <classpathentry kind="src" path="target/scala-2.12/resource_managed/test" />))
    error("""Not expected .classpath of suba project to contain <classpathentry kind="src" path="target/scala-2.12/resource_managed/test" />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.12/src_managed/main") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="...src_managed/main" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.12/src_managed/test") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="...src_managed/test" output="..." />: %s """ format classpath)
  if (!(classpath.child contains <classpathentry kind="src" path="src/main/scala" />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="..." path="src/main/scala" />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/main/java") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/main/java" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/main/resources") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/main/resources" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/scala") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/test/scala" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/java") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/test/java" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/resources") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/test/resources" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/it/scala") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/it/scala" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/it/java") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/it/java" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/it/resources") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/it/resources" output="..." />: %s """ format classpath)
  // lib entries with sources
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.0.1/logback-classic-1.0.1.jar" } sourcepath={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.0.1/logback-classic-1.0.1-sources.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.0.1/logback-classic-1.0.1.jar" } sourcepath={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.0.1/logback-classic-1.0.1-sources.jar" } />: %s""" format classpath)
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0.jar" } sourcepath={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0-sources.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0.jar" } sourcepath={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/biz/aQute/bnd/biz.aQute.bndlib/3.4.0/biz.aQute.bndlib-3.4.0-sources.jar" } />: %s""" format classpath)
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4.jar" } sourcepath={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4-sources.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4.jar" } sourcepath={ home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/org/specs2/specs2-core_2.12/3.9.4/specs2-core_2.12-3.9.4-sources.jar" } />: %s""" format classpath)
}

TaskKey[Unit]("verify-classpath-xml-subb") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / "sub" / "subb" / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of subb project not to contain duplicate entries: %s" format classpath)
    // src entries
  if (!(classpath.child contains <classpathentry kind="src" path="src/it/scala" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="src" path="src/it/scala" />: %s""" format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/scala") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/test/scala" output="..." /> """)
  // lib entries without sources
  // Broken: https://github.com/sbt/sbt/issues/5078
  //if (!(classpath.child contains <classpathentry kind="lib" path="../../lib_managed/jars/ch.qos.logback/logback-classic/logback-classic-1.0.1.jar" />))
  //  error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="../../lib_managed/jars/ch.qos.logback/logback-classic/logback-classic-1.0.1.jar" />: %s""" format classpath)
  //if (!(classpath.child contains <classpathentry kind="lib" path="../../lib_managed/jars/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0.jar" />))
  //  error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="../../lib_managed/jars/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0.jar" />: %s""" format classpath)
  //if (!(classpath.child contains <classpathentry kind="lib" path="../../lib_managed/jars/junit/junit/junit-4.7.jar" />))
  //  error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="../../lib_managed/jars/junit/junit/junit-4.7.jar" />: %s""" format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "specs2-core_2.12") 
    error("""Not expected .classpath of subb project to contain <classpathentry kind="..." path="...specs2-core_2.12..." output="..." /> """)
  // project dependencies
  if (!(classpath.child contains <classpathentry kind="src" path="/suba" exported="true" combineaccessrules="false" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="src" path="/suba" exported="true" combineaccessrules="false" />: %s""" format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "/subc")
    error("""Not expected .classpath of subb project to contain <classpathentry kind="..." path="...subc..." output="..." /> """)
}

TaskKey[Unit]("verify-classpath-xml-subc") := {
  val dir = baseDirectory.value
  val classpath = XML.loadFile(dir / "sub" / "subc" / ".classpath")
  val project = XML.loadFile(dir / "sub" / "subc" / ".project")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of subc project not to contain duplicate entries: %s" format classpath)
  // src entries
  if (!(classpath.child contains <classpathentry kind="src" path="src/main/scala" output=".target" />))
    error("""Expected .classpath of subc project to contain <classpathentry kind="src" path="src/main/scala" output=".target" /> """)
  // lib entries with absolute paths
  // Broken: https://github.com/sbt/sbt/issues/5078
  //if (!(classpath.child contains <classpathentry kind="lib" path={ "%s/lib_managed/jars/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0.jar".format(dir.getCanonicalPath) } />))
  //  error("""Expected .classpath of subc project to contain <classpathentry kind="lib" path="%s/lib_managed/jars/biz.aQute.bnd/biz.aQute.bndlib/biz.aQute.bndlib-3.4.0.jar" />: %s""".format(dir.getCanonicalPath, classpath))
  // classpath transformer
  if (!(classpath.child contains <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>))
    error("""Expected .classpath of subc project to contain <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/> """)
  if (!(classpath.child contains <classpathentry kind="lib" path="libs/my.jar"/>))
    error("""Expected .classpath of subc project to contain <classpathentry kind="lib" path="libs/my.jar"/>!""")
  if (!(classpath.child contains <classpathentry kind="output" path=".target" />))
    error("""Expected .classpath of subc project to contain <classpathentry kind="output" path=".target" /> """)
  if (!(project.child contains <foo bar="baz"/>))
    error("""Expected .project of subc project to contain <foo bar="baz"/>!""")
}

TaskKey[Unit]("verify-java-settings") := {
  val dir = baseDirectory.value
  val settings = {
    val p = new Properties 
    p.load(new FileInputStream(dir / "sub/subb/.settings/org.eclipse.core.resources.prefs"))
    p.asScala.toMap
  }
  val expected = Map(
    "encoding/<project>" -> "UTF-8"
  )
  if (settings != expected) error("Expected settings to be '%s', but was '%s'!".format(expected, settings))
}

TaskKey[Unit]("verify-scala-settings") := {
  val dir = baseDirectory.value
  val settings = {
    val p = new Properties
    p.load(new FileInputStream(dir / "sub/subb/.settings/org.scala-ide.sdt.core.prefs"))
    p.asScala.toMap
  }
  val defaultExpected = Map(
    "scala.compiler.useProjectSettings" -> "true",
    "unchecked" -> "true",
    "verbose" -> "true",
    "scala.compiler.additionalParams" -> "-Xprompt",
    "deprecation" -> "true",
    "Xelide-below" -> "1000"
  )
  val currentSbtVersion = (pluginCrossBuild / sbtVersion).value
  val expected = CrossVersion.partialVersion(currentSbtVersion) match {
    case Some((0,13)) =>
      Map(
        "scala.compiler.additionalParams" -> """-Xprompt -Xsource:2.10 -Ymacro-expand:none""",
        "scala.compiler.installation" -> "2.10",
        "verbose" -> "true",
        "deprecation" -> "true",
        "Xelide-below" -> "1000",
        "unchecked" -> "true",
        "scala.compiler.useProjectSettings" -> "true"
      )
    case Some((1,_)) => defaultExpected
    case _ => defaultExpected
  }
  if (settings != expected) error("Expected settings to be '%s', but was '%s'!".format(expected, settings))
}
