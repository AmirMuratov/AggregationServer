package aggregation.app

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant

import aggregation.app.model.IntervalId

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationLong

package object util {

  implicit class ByteBufferOps(val buffer: ByteBuffer) extends AnyVal {

    def readAsciiString(offset: Int, length: Int): String = {
      val stringBytes = new Array[Byte](length)
      stringBytes.indices.foreach { i =>
        stringBytes(i) = buffer.get(offset + i)
      }
      new String(stringBytes, StandardCharsets.US_ASCII)
    }

    def read2ByteUnsignedInt(offset: Int): Int =
      ((0xff & buffer.get(offset)) << 8) + (0xff & buffer.get(offset + 1))

  }

  implicit class InstantOps(val instant: Instant) extends AnyVal {

    def getDurationUntilNextInterval(intervalDuration: FiniteDuration): FiniteDuration = {
      val currentMillis = instant.toEpochMilli
      val nextIntervalMillis = (currentMillis / intervalDuration.toMillis + 1) * intervalDuration.toMillis
      (nextIntervalMillis - currentMillis).millis
    }

    def getInterval(intervalDuration: FiniteDuration): IntervalId = {
      instant.toEpochMilli / intervalDuration.toMillis
    }

  }
}
