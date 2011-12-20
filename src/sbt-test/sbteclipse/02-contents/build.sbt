import java.io.FileInputStream
import java.util.Properties
import scala.collection.JavaConverters._
import scala.xml.XML

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

TaskKey[Unit]("verify-project-xml") <<= baseDirectory map { dir =>
  val projectDescription = XML.loadFile(dir / ".project")
  val name = (projectDescription \ "name").text
  if (name != "sbteclipse-test")
    error("Expected .project to contain name '%s', but was '%s'!".format("sbteclipse-test", name))
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
  if (!(classpath.child contains <classpathentry kind="src" path="target/scala-2.9.1/src_managed/main" output="target/scala-2.9.1/classes" />))
    error("""Expected .classpath of root project to contain <classpathentry kind="src" path="target/scala-2.9.1/src_managed/main" output="target/scala-2.9.1/classes" /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/main/resources") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/main/resources" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/java") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/test/java" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/test/resources") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/test/resources" output="..." /> """)
  if ((classpath \ "classpathentry" \\ "@path") map (_.text) contains "src/it/scala") 
    error("""Not expected .classpath of root project to contain <classpathentry kind="..." path="src/it/scala" output="..." /> """)
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
  if (!(classpath.child contains <classpathentry kind="output" path="target"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="output" path="target"/> """)
}

TaskKey[Unit]("verify-classpath-xml-suba") <<= baseDirectory map { dir =>
  val home = System.getProperty("user.home")
  val classpath = XML.loadFile(dir / "sub" / "suba" / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of suba project not to contain duplicate entries: %s" format classpath)
  // lib entries without sources
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.ivy2/cache/com.weiglewilczek.slf4s/slf4s_2.9.1/jars/slf4s_2.9.1-1.0.7.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.ivy2/cache/com.weiglewilczek.slf4s/slf4s_2.9.1/jars/slf4s_2.9.1-1.0.7.jar" } />: %s""" format classpath)
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.ivy2/cache/biz.aQute/bndlib/jars/bndlib-1.50.0.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.ivy2/cache/biz.aQute/bndlib/jars/bndlib-1.50.0.jar" } />: %s""" format classpath)
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.ivy2/cache/org.specs2/specs2_2.9.1/jars/specs2_2.9.1-1.6.1.jar" } />))
    error("""Expected .classpath of suba project to contain <classpathentry kind="lib" path={ home + "/.ivy2/cache/org.specs2/specs2_2.9.1/jars/specs2_2.9.1-1.6.1.jar" } />: %s""" format classpath)
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

TaskKey[Unit]("verify-settings") <<= baseDirectory map { dir =>
  val settings = {
    val p = new Properties 
    p.load(new FileInputStream(dir / "sub/subb/.settings/org.scala-ide.sdt.core.prefs"))
    p.asScala.toMap
  }
  val expected = Map(
    "scala.compiler.useProjectSettings" -> "true", 
    "unchecked" -> "true", 
    "deprecation" -> "true"
  ) 
  if (settings != expected) error("Expected settings to be '%s', but was '%s'!".format(expected, settings))
}
