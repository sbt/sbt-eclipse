package com.typesafe.sbteclipse.core.util

import com.typesafe.sbteclipse.core.util.ScalaVersion.FullScalaVersion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ScalaVersionSpec extends AnyWordSpec with Matchers {
  "ScalaVersion" should {
    """parse Scala version "2.12.0"""" in {
      ScalaVersion.parse("2.12.0") shouldEqual FullScalaVersion(2, 12, 0, None)
    }

    """parse Scala version "2.12.0-SNAPSHOT"""" in {
      ScalaVersion.parse("2.12.0-SNAPSHOT") shouldEqual FullScalaVersion(2, 12, 0, Some("SNAPSHOT"))
    }

    """parse Scala version "2.12.0-RC10"""" in {
      ScalaVersion.parse("2.12.0-RC10") shouldEqual FullScalaVersion(2, 12, 0, Some("RC10"))
    }

    """parse Scala version "2.12.0-M1"""" in {
      ScalaVersion.parse("2.12.0-M1") shouldEqual FullScalaVersion(2, 12, 0, Some("M1"))
    }

    """parse Scala version "2.12.0-51e77037f2adc4ffa7421aa36803a5874292b70d"""" in {
      ScalaVersion.parse("2.12.0-51e77037f2adc4ffa7421aa36803a5874292b70d") shouldEqual FullScalaVersion(2, 12, 0, Some("51e77037f2adc4ffa7421aa36803a5874292b70d"))
    }

    """fail to parse "2.12"""" in {
      ScalaVersion.parse("2.12") shouldEqual NoScalaVersion
    }
  }
}
