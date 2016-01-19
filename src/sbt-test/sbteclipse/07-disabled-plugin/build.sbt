lazy val projectA = project in file("a")
lazy val projectB = (project in file("b"))
  .disablePlugins(EclipsePlugin)
