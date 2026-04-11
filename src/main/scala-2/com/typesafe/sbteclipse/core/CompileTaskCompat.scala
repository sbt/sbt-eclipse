package com.typesafe.sbteclipse.core

import sbt.{Configuration, Def, Keys, Setting, Task}
import xsbti.compile.CompileAnalysis

private object CompileTaskCompat {
  def compileSetting(scope: Configuration, task: Def.Initialize[Task[CompileAnalysis]]): Setting[?] =
    (scope / Keys.compile) := task.value
}
