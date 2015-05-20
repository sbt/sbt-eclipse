package com.typesafe.sbteclipse.core.util

import org.scalatest.WordSpec
import org.scalatest.Matchers
import ScalaVersion.FullScalaVersion

class ScalaVersionSpec extends WordSpec with Matchers {
  "ScalaVersion" should {
    """parse Scala version "2.10.0"""" in {
      ScalaVersion.parse("2.10.0") shouldEqual FullScalaVersion(2, 10, 0, None)
    }

    """parse Scala version "2.10.0-SNAPSHOT"""" in {
      ScalaVersion.parse("2.10.0-SNAPSHOT") shouldEqual FullScalaVersion(2, 10, 0, Some("SNAPSHOT"))
    }

    """parse Scala version "2.10.0-RC10"""" in {
      ScalaVersion.parse("2.10.0-RC10") shouldEqual FullScalaVersion(2, 10, 0, Some("RC10"))
    }

    """parse Scala version "2.10.0-M1"""" in {
      ScalaVersion.parse("2.10.0-M1") shouldEqual FullScalaVersion(2, 10, 0, Some("M1"))
    }

    """parse Scala version "2.10.0-51e77037f2adc4ffa7421aa36803a5874292b70d"""" in {
      ScalaVersion.parse("2.10.0-51e77037f2adc4ffa7421aa36803a5874292b70d") shouldEqual FullScalaVersion(2, 10, 0, Some("51e77037f2adc4ffa7421aa36803a5874292b70d"))
    }

    """fail to parse "2.10"""" in {
      ScalaVersion.parse("2.10") shouldEqual NoScalaVersion
    }
  }
}