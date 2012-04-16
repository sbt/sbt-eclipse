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

import com.typesafe.sbtscalariform.ScalariformPlugin._
import name.heikoseeberger.sbtproperties.PropertiesPlugin._
import posterous.Publish._
import sbtrelease._
import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin._

object Build extends Build {

  lazy val root = Project(
    "sbteclipse",
    file("."),
    aggregate = Seq(sbteclipseCore, sbteclipsePlugin),
    settings = commonSettings ++ Seq(
      publishArtifact := false,
      aggregate in Posterous := false
    )
  )

  lazy val sbteclipseCore = Project(
    "sbteclipse-core",
    file("sbteclipse-core"),
    settings = commonSettings ++ Seq(
      libraryDependencies ++= Seq("org.scalaz" %% "scalaz-core" % "6.0.3")
    )
  )

  lazy val sbteclipsePlugin = Project(
    "sbteclipse-plugin",
    file("sbteclipse-plugin"),
    dependencies = Seq(sbteclipseCore),
    settings = commonSettings
  )

  def commonSettings = Defaults.defaultSettings ++
    Seq(
      organization := "com.typesafe.sbteclipse",
      // version is defined in version.sbt in order to support sbt-release
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      publishTo <<= (version)(version =>
        Some(if (version endsWith "SNAPSHOT") Classpaths.typesafeSnapshots else Classpaths.typesafeResolver)
      ),
      sbtPlugin := true,
      publishMavenStyle := false,
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false
    ) ++
    posterousSettings ++ Seq(
      (email in Posterous) <<= PropertiesKeys.properties(_ get "posterous.email"),
      (password in Posterous) <<= PropertiesKeys.properties(_ get "posterous.password")
    ) ++
    propertiesSettings ++
    Release.releaseSettings ++ Seq(
      ReleaseKeys.releaseProcess <<= thisProjectRef { ref =>
        import ReleaseStateTransformations._
        Seq[ReleasePart](
          initialGitChecks,
          checkSnapshotDependencies,
          releaseTask(check in Posterous in ref),
          inquireVersions,
          runTest,
          setReleaseVersion,
          commitReleaseVersion,
          tagRelease,
          releaseTask(publish in Global in ref),
          releaseTask(publish in Posterous in ref),
          setNextVersion,
          commitNextVersion
        )
      }
    ) ++
    scalariformSettings ++
    scriptedSettings
}
