
{
  val formatPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences().setPreference(DoubleIndentClassDeclaration, true)
  }
  import com.typesafe.sbtscalariform.ScalariformPlugin
  val scalariformPluginSettings = ScalariformPlugin.defaultSettings ++ Seq(
    ScalariformPlugin.formatPreferences in Compile := formatPreferences,
    ScalariformPlugin.formatPreferences in Test := formatPreferences
  )
  seq(scalariformPluginSettings: _*)
}
