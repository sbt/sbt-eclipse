sbteclipse [![Build Status](https://travis-ci.org/typesafehub/sbteclipse.svg?branch=master)](https://travis-ci.org/typesafehub/sbteclipse)
=========================

Plugin for [sbt](https://github.com/sbt/sbt) to create [Eclipse](http://www.eclipse.org/) project definitions. Please see the [Documentation](http://github.com/typesafehub/sbteclipse/wiki/) for information about installing and using sbteclipse. Information about contribution policy and license can be found below.


For sbt 0.13 and up
---------------------

- Add sbteclipse to your plugin definition file (or create one if doesn't exist). You can use either:

  - the global file (for version 0.13 and up) at *~/.sbt/0.13/plugins/plugins.sbt*
  - the project-specific file at *PROJECT_DIR/project/plugins.sbt*

For the latest version:

    addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")

- In sbt use the command `eclipse` to create Eclipse project files

    &gt; eclipse

- In Eclipse use the *Import Wizard* to import *Existing Projects into Workspace*



Multi ScalaVersion support
-------------------

Since Scala IDE 4.0 multiple scala versions are supported in a single eclipse workspace. The Scala IDE uses by default the highest supported scala version, 2.11 since Scala IDE 4.0 and 2.12 since Scala IDE 4.6. The default can be overwritten at workspace level or at project level. The latter is stored in the file `.settings/org.scala-ide.sdt.core.prefs`. This file can be generated with sbteclipse. 

**For sbteclipse 5.2.5 and up**

Given `Xi.Yi` is the default Scala installation version of your Scala IDE, and `Xp.Yp.Zp` is the targetted scala version of your project, you add following in your sbt build file:
  ```
  EclipseKeys.defaultScalaInstallation := "Xi.Yi" // "2.12" is the default
  ScalaVersion := "Xp.Yp.Zp"
  ```
In case `Xp.Yp` < `Xi.Yi`, the lower `scala.compiler.installation` is configured at project level.

When you're using ScalaIDE-4.6.0 up to 4.7._ (until a new major scala version is the default), and x.y.z matches with one of the pre-installed scala compilers in the IDE, it should work fine out of the box.  

**For sbteclipse 4.0. up to 5.2.4**

In case `Xp.Yp` = `2.10`, the lower `scala.compiler.installation` is configured at project level.

When you're using ScalaIDE-4.0 up to 4.5._, and x.y.z matches with one of the pre-installed scala compilers in the IDE, it should work fine out of the box.  

Contribution policy
-------------------

Contributions via GitHub pull requests are gladly accepted from their original author. Before we can accept pull requests, you will need to agree to the [Typesafe Contributor License Agreement](http://www.typesafe.com/contribute/cla) online, using your GitHub account - it takes 30 seconds.


License
-------

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html). Feel free to use it accordingly.

Maintainers
-------------------

### Releases

Maintainers must run `git tag` to tag a release. The release can then be pushed to bintray with `sbt ^publish`.
