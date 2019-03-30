sbteclipse [![Build Status](https://travis-ci.org/sbt/sbteclipse.svg?branch=master)](https://travis-ci.org/sbt/sbteclipse)
=========================

Plugin for [sbt](https://github.com/sbt/sbt) to create [Eclipse](http://www.eclipse.org/) project definitions. Please see below for installation details and the [Documentation](http://github.com/sbt/sbteclipse/wiki/) for information about configuring sbteclipse. Information about contribution policy and license can be found below.

Installation and Basic Usage
---------------------

- Open your plugin definition file (or create one if doesn't exist). You can use either:

  - the global file (for version 0.13 and up) at *~/.sbt/SBT_VERSION/plugins/plugins.sbt*
  - the project-specific file at *PROJECT_DIR/project/plugins.sbt*

- Add sbteclipse to the plugin definition file:

  - Version 6.x+ will only support SBT 1.0+ when it is released. Use 5.2.4 or older for previons versions of SBT

```
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")
```

- In sbt use the command `eclipse` to create Eclipse project files

    &gt; eclipse

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
