package aggregation.app.util

import java.time.Instant

import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration.DurationInt

class InstantOpsSpec extends FlatSpec with Matchers {

  "getDurationUntilNextInterval" should "return time until next interval" in {
    val interval = 1.minute
    Instant.ofEpochMilli(1570710000).getDurationUntilNextInterval(interval).toMillis shouldBe 30000
    Instant.ofEpochMilli(1570679999).getDurationUntilNextInterval(interval).toMillis shouldBe 1
    Instant.ofEpochMilli(1570680001).getDurationUntilNextInterval(interval).toMillis shouldBe 59999
  }

}
