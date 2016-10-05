sbteclipse |Build Status|
=========================

Plugin for `sbt`_ to create `Eclipse`_ project definitions. Please see the `Documentation`_ for information about installing and using sbteclipse. Information about contribution policy and license can be found below.


For sbt 0.13 and up
---------------------

- Add sbteclipse to your plugin definition file (or create one if doesn't exist). You can use either:

  - the global file (for version 0.13 and up) at *~/.sbt/0.13/plugins/plugins.sbt*
  - the project-specific file at *PROJECT_DIR/project/plugins.sbt*

For the latest version:

::

  addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.0.0")

- In sbt use the command *eclipse* to create Eclipse project files

::

  > eclipse

- In Eclipse use the *Import Wizard* to import *Existing Projects into Workspace*

Contribution policy
-------------------

Contributions via GitHub pull requests are gladly accepted from their original author. Before we can accept pull requests, you will need to agree to the `Typesafe Contributor License Agreement`_ online, using your GitHub account - it takes 30 seconds.


License
-------

This code is open source software licensed under the `Apache 2.0 License`_. Feel free to use it accordingly.

.. _`sbt`: http://github.com/harrah/xsbt/
.. _`Eclipse`: http://www.eclipse.org/
.. _`Documentation`: http://github.com/typesafehub/sbteclipse/wiki/
.. _`Apache 2.0 License`: http://www.apache.org/licenses/LICENSE-2.0.html
.. _`Typesafe Contributor License Agreement`: http://www.typesafe.com/contribute/cla
.. |Build Status| image:: https://travis-ci.org/typesafehub/sbteclipse.png?branch=master
                        :target: https://travis-ci.org/typesafehub/sbteclipse
