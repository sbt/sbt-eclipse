# sbt-eclipse

[![Build Status](https://github.com/sbt/sbt-eclipse/actions/workflows/build-test.yml/badge.svg)](https://github.com/sbt/sbt-eclipse/actions/workflows/build-test.yml)
[![Repository size](https://img.shields.io/github/repo-size/sbt/sbt-eclipse.svg?logo=git)](https://github.com/sbt/sbt-eclipse)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Plugin for [sbt](https://github.com/sbt/sbt) to create [Eclipse](http://www.eclipse.org/) project definitions. Please see below for installation details and the [Documentation](http://github.com/sbt/sbt-eclipse/wiki/) for information about configuring sbt-eclipse. Information about contribution policy and license can be found below.

Installation and Basic Usage
---------------------

- Open your plugin definition file (or create one if doesn't exist). You can use either:

  - the global file (for version 1.0 and up) at *~/.sbt/SBT_VERSION/plugins/plugins.sbt*
  - the project-specific file at *PROJECT_DIR/project/plugins.sbt*

- Add sbt-eclipse to the plugin definition file:

  - Version 6.x+ only supports SBT 1.4+. Use 5.2.4 or older for previous versions of SBT

```
addSbtPlugin("com.github.sbt" % "sbt-eclipse" % "6.0.0")

// For older releases (< version 6.0.0)
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")
```

- In sbt use the command `eclipse` to create Eclipse project files

    &gt; eclipse

- In Eclipse use the *Import Wizard* to import *Existing Projects into Workspace*

Contribution policy
-------------------

Contributions via GitHub pull requests are gladly accepted from their original author.


License
-------

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html). Feel free to use it accordingly.

Maintainers
-------------------

### Releases

Maintainers must run `git tag` or use the GitHub UI to tag a release. As soon as a tag gets pushed to the repository (or created via the GitHub UI) a release will be pushed to the [Maven Central repository](https://repo1.maven.org/maven2/com/github/sbt/). Also on each push to the main branch [snapshots will be published](https://oss.sonatype.org/content/repositories/snapshots/com/github/sbt/).
