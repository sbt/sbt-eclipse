package com.typesafe.sbteclipse.core

import collection.breakOut
import scala.language.postfixOps

private trait EclipseSDTConfig {
  def fromScalacToSDT(options: Seq[String]): Seq[(String, String)] =
    aggregate((Map() withDefaultValue "", options))._1.toSeq

  private type Aggregate = (String Map String, Seq[String])
  private type ArgumentConsumer = Aggregate PartialFunction Aggregate

  private lazy val aggregate: ArgumentConsumer = {
    case done @ (_, Seq()) => done
    case formerResult => aggregate(consumeOptionsFrom(formerResult) getOrElse formerResult)
  }

  private lazy val consumeOptionsFrom = supportedOptions.foldRight(Unsupported.classify)(_.classify orElse _).lift

  private lazy val supportedOptions = Set(
    ColonSeparated("P", once = false),
    Flag("deprecation"),
    Flag("explaintypes"),
    ColonSeparated("g"),
    Flag("no-specialization"),
    Flag("nowarn"),
    Flag("optimise"),
    ColonSeparated("target"),
    Flag("unchecked"),
    Flag("verbose"),

    Flag("Xcheck-null"),
    Flag("Xcheckinit"),
    Flag("Xdisable-assertions"),
    TakesArg("Xelide-below"),
    Flag("Xexperimental"),
    Flag("Xfatal-warnings"),
    Flag("Xfuture"),
    Flag("Xlog-implicits"),
    Flag("Xmigration"),
    Flag("Xno-uescape"),
    XPluginHackery,
    ColonSeparated("Xplugin-disable"),
    ColonSeparated("Xplugin-require"),
    TakesArg("Xpluginsdir"),
    TakesArg("Xmax-classfile-name"),

    Flag("Yno-generic-signatures"),
    Flag("Yno-imports"),
    TakesArg("Ypresentation-delay"),
    TakesArg("Ypresentation-log"),
    TakesArg("Ypresentation-replay"),
    Flag("Ypresentation-verbose"),
    TakesArg("Yrecursion"),
    Flag("Yself-in-annots"),
    ColonSeparated("Ystruct-dispatch"),
    Flag("Ywarn-dead-code")
  )

  private sealed trait SDTOption {
    def name: String
    def classify: ArgumentConsumer
  }

  private sealed trait ScalacOption { this: SDTOption =>
    lazy val scalacOption = "-" + name
  }

  private case class Flag(name: String) extends SDTOption with ScalacOption {
    val classify: ArgumentConsumer = {
      case (akku, Seq(`scalacOption`, tail @ _*)) => (akku + (name -> String.valueOf(true)), tail)
    }
  }

  // Eclipse Scala IDE automagically adds the continuations plugin, so we have a hack here to detect
  // and remove it (which is dangerous, but less dangerous than not working...)
  // Note: Reference Scala issue: https://issues.scala-lang.org/browse/SI-5491
  private object XPluginHackery extends ColonSeparated("Xplugin", false) {
    override def maybeAppend(pre: String, post: String) =
      if (post contains "continuations") pre
      else super.maybeAppend(pre, post)
  }

  private case class ColonSeparated(name: String, once: Boolean = true) extends SDTOption with ScalacOption {
    val nameThenColonThen = scalacOption + ":(.+)" r

    val classify: ArgumentConsumer = {
      case (akku, Seq(nameThenColonThen(value), tail @ _*)) =>
        (akku + (name -> maybeAppend(akku(name), value)), tail)
    }

    def maybeAppend(pre: String, post: String) =
      if (once) post else append(pre, post, ",")
  }

  private case class TakesArg(name: String) extends SDTOption with ScalacOption {
    val classify: ArgumentConsumer = {
      case (akku, Seq(`scalacOption`, value, tail @ _*)) => (akku + (name -> value), tail)
    }
  }

  private object Unsupported extends SDTOption {
    def name = "scala.compiler.additionalParams"
    val classify: ArgumentConsumer = {
      case (akku, Seq(option, tail @ _*)) =>
        (akku + (name -> append(akku(name), option, " ")), tail)
    }
  }

  private def append(pre: String, post: String, separator: String) =
    if (pre isEmpty) post else pre + separator + post
}

