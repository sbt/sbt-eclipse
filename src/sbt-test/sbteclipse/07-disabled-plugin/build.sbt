lazy val projectA = project.in(file("a"))
lazy val projectB = project.in(file("b"))
  .disablePlugins(EclipsePlugin)

ThisBuild / scalaVersion := {
  if (sbtVersion.value.startsWith("1.")) "2.12.21" else "3.9.0"
}
