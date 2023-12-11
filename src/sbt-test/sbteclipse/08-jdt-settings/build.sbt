
val check = TaskKey[Unit]("check") := {
  import java.util.Properties
  import java.io.FileInputStream
  import scala.collection.JavaConverters._

  val s: TaskStreams = streams.value
  val expectedFile = baseDirectory.value / "expected"
  val resultFile = baseDirectory.value / ".settings" / "org.eclipse.jdt.core.prefs"

  if (expectedFile.exists()) {
    val expectedIn = new FileInputStream(expectedFile)
    val expected =
      try {
        val prop = new Properties()
        prop.load(expectedIn)
        prop.asScala.toMap
      } finally {
        expectedIn.close()
      }

    val resultIn = new FileInputStream(resultFile)
    val result =
      try {
        val prop = new Properties()
        prop.load(resultIn)
        prop.asScala.toMap
      } finally {
        resultIn.close()
      }

    if (expected == result)
      s.log.info(s"correct data: ${resultFile}")
    else
      sys.error("Expected settings to be '%s', but was '%s'!".format(expected, result))
  }
}

// ensure org.eclipse.core.resources.prefs will always be generated
ThisBuild / scalacOptions ++= Seq("-encoding", "utf-8")

// check that no JDT file is generated (default ignore, no runtime defined)
lazy val projectA = (project in file("a"))
  .settings(
    check
  )

// check that a new and correct JDT file is generated
lazy val projectB = (project in file("b"))
  .settings(
    EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18),
    EclipseKeys.jdtMode := EclipseJDTMode.Update,
    check
  )

// check that a correct JDT file is is not updated
lazy val projectC = (project in file("c"))
  .settings(
    EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE11),
    EclipseKeys.jdtMode := EclipseJDTMode.Update,
    check
  )

// check that an outdated JDT file is selectively updated
lazy val projectD = (project in file("d"))
  .settings(
    EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE_17),
    EclipseKeys.jdtMode := EclipseJDTMode.Update,
    check
  )

// check that a JDT file is overwritten
lazy val projectE = (project in file("e"))
  .settings(
    EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE11),
    EclipseKeys.jdtMode := EclipseJDTMode.Overwrite,
    check
  )

// check that an JDT file is removed
lazy val projectF = (project in file("f"))
  .settings(
    EclipseKeys.jdtMode := EclipseJDTMode.Remove,
    check
  )

// check that an JDT file is default ignored, but written on command
lazy val projectG = (project in file("g"))
  .settings(
    EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18),
    check
  )
