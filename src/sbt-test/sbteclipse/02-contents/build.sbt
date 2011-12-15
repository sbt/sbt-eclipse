import java.io.FileInputStream
import java.util.Properties
import scala.collection.JavaConverters._
import scala.xml.XML

organization := "com.typesafe.sbteclipse"

name := "sbteclipse-test"

version := "1.2.3"

TaskKey[Unit]("verify-project-xml") <<= baseDirectory map { dir =>
  val projectDescription = XML.loadFile(new File(dir, ".project"))
  val name = (projectDescription \ "name").text
  if (name != "sbteclipse-test")
    error("Expected .project to contain name '%s', but was '%s'!".format("sbteclipse-test", name))
}

TaskKey[Unit]("verify-classpath-xml-root") <<= baseDirectory map { dir =>
  // src entries
  val classpath = XML.loadFile(dir / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of root project not to contain duplicate entries: %s" format classpath)
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
  // other entries
  if (!(classpath.child contains <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="con" path="org.scala-ide.sdt.launching.SCALA_CONTAINER"/> """)
  if (!(classpath.child contains <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/> """)
  if (!(classpath.child contains <classpathentry kind="output" path="target/scala-2.9.1/classes"/>))
    error("""Expected .classpath of root project to contain <classpathentry kind="output" path="target/scala-2.9.1/classes"/> """)
}

TaskKey[Unit]("verify-classpath-xml-subb") <<= baseDirectory map { dir =>
  // root: src entries
  val home = System.getProperty("user.home")
  val classpath = XML.loadFile(dir / "sub" / "subb" / ".classpath")
  if ((classpath \ "classpathentry") != (classpath \ "classpathentry").distinct)
    error("Expected .classpath of subb project not to contain duplicate entries: %s" format classpath)
  // root: lib entries without sources
  if (!(classpath.child contains <classpathentry kind="lib" path={ home + "/.ivy2/cache/com.weiglewilczek.slf4s/slf4s_2.9.1/jars/slf4s_2.9.1-1.0.7.jar" } />))
    error("""Expected .classpath of root project to contain <classpathentry kind="lib" path={ home + "/.ivy2/cache/com.weiglewilczek.slf4s/slf4s_2.9.1/jars/slf4s_2.9.1-1.0.7.jar" } />: %s""" format classpath)
}

TaskKey[Unit]("verify-settings") <<= baseDirectory map { dir =>
  val settings = {
    val p = new Properties 
    p.load(new FileInputStream(new File(dir, "sub/subb/.settings/org.scala-ide.sdt.core.prefs")))
    p.asScala.toMap
  }
  val expected = Map(
    "scala.compiler.useProjectSettings" -> "true", 
    "unchecked" -> "true", 
    "deprecation" -> "true"
  ) 
  if (settings != expected) error("Expected settings to be '%s', but was '%s'!".format(expected, settings))
}
