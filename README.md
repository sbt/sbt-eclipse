sbteclipse [![Build Status](https://travis-ci.org/typesafehub/sbteclipse.png?branch=master)](https://travis-ci.org/typesafehub/sbteclipse)
=========================

Plugin for [sbt](https://github.com/sbt/sbt) to create [Eclipse](http://www.eclipse.org/) project definitions. Please see the [Documentation](http://github.com/typesafehub/sbteclipse/wiki/) for information about installing and using sbteclipse. Information about contribution policy and license can be found below.


For sbt 0.13 and up
---------------------

- Add sbteclipse to your plugin definition file (or create one if doesn't exist). You can use either:

  - the global file (for version 0.13 and up) at *~/.sbt/0.13/plugins/plugins.sbt*
  - the project-specific file at *PROJECT_DIR/project/plugins.sbt*

For the latest version:

    addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.2")

- In sbt use the command `eclipse` to create Eclipse project files

    > eclipse

- In Eclipse use the *Import Wizard* to import *Existing Projects into Workspace*

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
