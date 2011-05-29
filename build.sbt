
organization := "com.weiglewilczek.sbteclipse"

name := "sbteclipse"

version := "0.7-SNAPSHOT"

sbtPlugin := true

libraryDependencies += "org.scalaz" % "scalaz-core_2.9.0" % "6.0.RC2"

publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

pomPostProcess := { (pom: scala.xml.Node) =>
  import scala.xml._
  import scala.xml.transform._
  val rewriteRule = new RewriteRule {
    override def transform(n: Node) = n match {
      case elem @ Elem(_, "dependency", _, _, _*) if ((elem \ "groupId").text == "org.scala-tools.sbt") && ((elem \ "artifactId").text startsWith "sbt_")  => NodeSeq.Empty
      case other => other
    }
  }
  new RuleTransformer(rewriteRule)(pom)
}
