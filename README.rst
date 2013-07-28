sbteclipse
==========

Plugin for `sbt`_ to create `Eclipse`_ project definitions. Please see the `Documentation`_ for information about installing and using sbteclipse. Information about mailing lists, contribution policy and license can be found below.


For the impatient
-----------------

- sbteclipse 2.3.0 requires sbt 0.13, sbteclipse 2.2.0 works with sbt 0.12 and 0.13!

- Add sbteclipse to your plugin definition file. You can use either the global one at *~/.sbt/plugins/plugins.sbt* or the project-specific one at *PROJECT_DIR/project/plugins.sbt*::

    addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.3.0")

- In sbt use the command *eclipse* to create Eclipse project files::

    > eclipse

- In Eclipse use the *Import Wizard* to import *Existing Projects into Workspace*


Mailing list
------------

Please use the `sbt mailing list`_ and prefix the subject with "[sbteclipse]".


Contribution policy
-------------------

Contributions via GitHub pull requests are gladly accepted from their original author. Before we can accept pull requests, you will need to agree to the `Typesafe Contributor License Agreement`_ online, using your GitHub account - it takes 30 seconds.


License
-------

This code is open source software licensed under the `Apache 2.0 License`_. Feel free to use it accordingly.

.. _`sbt`: http://github.com/harrah/xsbt/
.. _`Eclipse`: http://www.eclipse.org/
.. _`Documentation`: http://github.com/typesafehub/sbteclipse/wiki/
.. _`sbt mailing list`: http://groups.google.com/group/simple-build-tool
.. _`Apache 2.0 License`: http://www.apache.org/licenses/LICENSE-2.0.html
.. _`Typesafe Contributor License Agreement`: http://www.typesafe.com/contribute/cla
