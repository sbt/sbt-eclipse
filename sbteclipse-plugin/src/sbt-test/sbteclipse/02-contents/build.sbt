import java.io.FileInputStream
import java.util.Properties
import scala.collection.JavaConverters._
import scala.xml.XML

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

TaskKey[Unit]("verify-project-xml") <<= baseDirectory map { dir =>
  val projectDescription = XML.loadFile(dir / ".project")
  // verifier method
  def verify[A](name: String, expected: A, actual: A) =
    if (actual != expected) error("Expected .project to contain %s '%s', but was '%s'!".format(name, expected, actual))
  // project name
  verify("name", "sbteclipse-test",  (projectDescription \ "name").text)
  // scala project nature
  verify("buildCommand", "org.scala-ide.sdt.core.scalabuilder", (projectDescription \ "buildSpec" \ "buildCommand" \ "name").text)
  verify("natures", Set("org.scala-ide.sdt.core.scalanature", "org.eclipse.jdt.core.javanature"), (projectDescription \ "natures" \ "nature").map(_.text).toSet)
  // java project nature
  val javaProjectDescription = XML.loadFile(dir / "java" / ".project")
  verify("buildCommand", "org.eclipse.jdt.core.javabuilder", (javaProjectDescription \ "buildSpec" \ "buildCommand" \ "name").text)
  verify("natures", "org.eclipse.jdt.core.javanature", (javaProjectDescription \ "natures" \ "nature").text)
}

TaskKey[Unit]("verify-classpath-xml-root") <<= baseDirectory map { dir =>
  val classpath = XML.loadFile(dir / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of root project not to contain duplicate entries: %s" format classpath)
  // src entries
  if (!(classpath.child contains <classpathentry kind="src" path="src/main/scala" output="target/scala-2.9.1/classes" />))
    error("""Expected .classpath of root project to contain <classpathentry kind="src" path="src/main/scala" output="target/scala-2.9.1/classes" /> """)
  if (!(classpath.child contains <classpathentry kind="src" path="src/main/java" output="target/scala-2.9.1/classes" />))
    error("""Expected .classpath of root project to contain <classpathentry kind="src" path="src/main/java" output="target/scala-2.9.1/classes" /> """)
  if (!(classpath.child contains <classpathentry kind="src" path="src/test/scala" output="target/scala-2.9.1/test-classes" />))
    error("""Expected .classpath of root project to contain <classpathentry kind="src" path="src/test/scala" output="target/scala-2.9.1/test-classes" /> """)
  if (!(classpath.child contains <classpathentry kind="src" path="src/test/java" output="target/scala-2.9.1/test-classes" />))
    error("""Expected .classpath of root project to contain <classpathentry kind="src" path="src/test/java" output="target/scala-2.9.1/test-classes" /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/main/resources") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/main/resources" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/resources") 
    	error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/test/resources" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.9.1/src_managed/main") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="...src_managed/main" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.9.1/src_managed/test") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="...src_managed/test" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.9.1/resource_managed/main") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="...resource_managed/main" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.9.1/resource_managed/test") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="...resource_managed/test" output="..." /> """)
  // lib entries without sources
  if (!(classpath.child contains <classpathentry kind="lib" path="./lib_managed/jars/biz.aQute/bndlib/bndlib-1.50.0.jar" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="./lib_managed/jars/biz.aQute/bndlib/bndlib-1.50.0.jar" />: %s""" format classpath)
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

TaskKey[Unit]("verify-classpath-xml-sub") <<= baseDirectory map { dir =>
  val home = System.getProperty("user.home")
  val classpath = XML.loadFile(dir / "sub" / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of sub project not to contain duplicate entries: %s" format classpath)
  // lib entries with sources
  if (!(classpath.child contains <classpathentry kind="lib" path="../lib_managed/jars/biz.aQute/bndlib/bndlib-1.50.0.jar" sourcepath="../lib_managed/srcs/biz.aQute/bndlib/bndlib-1.50.0-sources.jar" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="../lib_managed/jars/biz.aQute/bndlib/bndlib-1.50.0.jar" sourcepath="../lib_managed/srcs/biz.aQute/bndlib/bndlib-1.50.0-sources.jar" />: %s""" format classpath)
  // other entries
  if (!(classpath.child contains <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6"/>: %s""" format classpath)
}

TaskKey[Unit]("verify-classpath-xml-suba") <<= baseDirectory map { dir =>
  val home = System.getProperty("user.home")
  val classpath = XML.loadFile(dir / "sub" / "suba" / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of suba project not to contain duplicate entries: %s" format classpath)
  // src entries
  if (!(classpath.child contains <classpathentry kind="src" path="target/scala-2.9.1/resource_managed/main" output="target/scala-2.9.1/classes" />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="src" path="target/scala-2.9.1/resource_managed/main" output="target/scala-2.9.1/classes" />: %s """ format classpath)
  if (!(classpath.child contains <classpathentry kind="src" path="target/scala-2.9.1/resource_managed/test" output="target/scala-2.9.1/test-classes" />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="src" path="target/scala-2.9.1/resource_managed/test" output="target/scala-2.9.1/test-classes" />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.9.1/src_managed/main") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="...src_managed/main" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "target/scala-2.9.1/src_managed/test") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="...src_managed/test" output="..." />: %s """ format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/main/scala") 
    error("""Not expected .classpath of suba project to contain <classpathentry kind="..." path="src/main/scala" output="..." />: %s """ format classpath)
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
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.ivy2/cache/com.weiglewilczek.slf4s/slf4s_2.9.1/jars/slf4s_2.9.1-1.0.7.jar" } sourcepath={ home + "/.ivy2/cache/com.weiglewilczek.slf4s/slf4s_2.9.1/srcs/slf4s_2.9.1-1.0.7-sources.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.ivy2/cache/com.weiglewilczek.slf4s/slf4s_2.9.1/jars/slf4s_2.9.1-1.0.7.jar" } sourcepath={ home + "/.ivy2/cache/com.weiglewilczek.slf4s/slf4s_2.9.1/srcs/slf4s_2.9.1-1.0.7-sources.jar" } />: %s""" format classpath)
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.ivy2/cache/biz.aQute/bndlib/jars/bndlib-1.50.0.jar" } sourcepath={ home + "/.ivy2/cache/biz.aQute/bndlib/srcs/bndlib-1.50.0-sources.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.ivy2/cache/biz.aQute/bndlib/jars/bndlib-1.50.0.jar" } sourcepath={ home + "/.ivy2/cache/biz.aQute/bndlib/srcs/bndlib-1.50.0-sources.jar" } />: %s""" format classpath)
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.ivy2/cache/org.specs2/specs2_2.9.1/jars/specs2_2.9.1-1.6.1.jar" } sourcepath={ home + "/.ivy2/cache/org.specs2/specs2_2.9.1/srcs/specs2_2.9.1-1.6.1-sources.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.ivy2/cache/org.specs2/specs2_2.9.1/jars/specs2_2.9.1-1.6.1.jar" } sourcepath={ home + "/.ivy2/cache/org.specs2/specs2_2.9.1/srcs/specs2_2.9.1-1.6.1-sources.jar" } />: %s""" format classpath)
}

TaskKey[Unit]("verify-classpath-xml-subb") <<= baseDirectory map { dir =>
  val classpath = XML.loadFile(dir / "sub" / "subb" / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of subb project not to contain duplicate entries: %s" format classpath)
    // src entries
  if (!(classpath.child contains <classpathentry kind="src" path="src/it/scala" output="target/scala-2.9.1/it-classes" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="src" path="src/it/scala" output="target/scala-2.9.1/it-classes" />: %s""" format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/scala") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/test/scala" output="..." /> """)
  // lib entries without sources
  if (!(classpath.child contains <classpathentry kind="lib" path="../../lib_managed/jars/com.weiglewilczek.slf4s/slf4s_2.9.1/slf4s_2.9.1-1.0.7.jar" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="../../lib_managed/jars/com.weiglewilczek.slf4s/slf4s_2.9.1/slf4s_2.9.1-1.0.7.jar" />: %s""" format classpath)
  if (!(classpath.child contains <classpathentry kind="lib" path="../../lib_managed/jars/biz.aQute/bndlib/bndlib-1.50.0.jar" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="../../lib_managed/jars/biz.aQute/bndlib/bndlib-1.50.0.jar" />: %s""" format classpath)
  if (!(classpath.child contains <classpathentry kind="lib" path="../../lib_managed/jars/junit/junit/junit-4.7.jar" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="lib" path="../../lib_managed/jars/junit/junit/junit-4.7.jar" />: %s""" format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "specs2_2.9.1") 
    error("""Not expected .classpath of subb project to contain <classpathentry kind="..." path="...specs2_2.9.1..." output="..." /> """)
  // project dependencies
  if (!(classpath.child contains <classpathentry kind="src" path="/suba" exported="true" combineaccessrules="false" />))
    error("""Expected .classpath of subb project to contain <classpathentry kind="src" path="/suba" exported="true" combineaccessrules="false" />: %s""" format classpath)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "/subc")
    error("""Not expected .classpath of subb project to contain <classpathentry kind="..." path="...subc..." output="..." /> """)
}

TaskKey[Unit]("verify-classpath-xml-subc") <<= baseDirectory map { dir =>
  val classpath = XML.loadFile(dir / "sub" / "subc" / ".classpath")
  val project = XML.loadFile(dir / "sub" / "subc" / ".project")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of subc project not to contain duplicate entries: %s" format classpath)
  // src entries
  if (!(classpath.child contains <classpathentry kind="src" path="src/main/scala" output=".target" />))
    error("""Expected .classpath of subc project to contain <classpathentry kind="src" path="src/main/scala" output=".target" /> """)
  // lib entries with absolute paths
  if (!(classpath.child contains <classpathentry kind="lib" path={ "%s/lib_managed/jars/biz.aQute/bndlib/bndlib-1.50.0.jar".format(dir.getCanonicalPath) } />))
    error("""Expected .classpath of subc project to contain <classpathentry kind="lib" path="%s/lib_managed/jars/biz.aQute/bndlib/bndlib-1.50.0.jar" />: %s""".format(dir.getCanonicalPath, classpath))
  // classpath transformer
  if (!(classpath.child contains <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/> """)
  if (!(classpath.child contains <foo bar="baz"/>))
    error("""Expected .classpath of subc project to contain <foo bar="baz"/>!""")
  if (!(project.child contains <foo bar="baz"/>))
    error("""Expected .project of subc project to contain <foo bar="baz"/>!""")
}

TaskKey[Unit]("verify-settings") <<= baseDirectory map { dir =>
  val settings = {
    val p = new Properties 
    p.load(new FileInputStream(dir / "sub/subb/.settings/org.scala-ide.sdt.core.prefs"))
    p.asScala.toMap
  }
  val expected = Map(
    "scala.compiler.useProjectSettings" -> "true", 
    "unchecked" -> "true", 
    "deprecation" -> "true",
    "Xelide-below" -> "0"
  ) 
  if (settings != expected) error("Expected settings to be '%s', but was '%s'!".format(expected, settings))
}
