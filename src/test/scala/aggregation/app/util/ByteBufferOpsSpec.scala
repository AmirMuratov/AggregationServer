package aggregation.app.util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.scalatest.{FlatSpec, Matchers}

class ByteBufferOpsSpec extends FlatSpec with Matchers {

  "readAsciiString" should "parse ascii strings" in {
    val s1 = "12345678"
    ByteBuffer.wrap(s1.getBytes(StandardCharsets.US_ASCII)).readAsciiString(0, s1.length) shouldBe s1
    val s2 = ""
    ByteBuffer.wrap(s2.getBytes(StandardCharsets.US_ASCII)).readAsciiString(0, s2.length) shouldBe s2
    val s3 = "abcdefg"
    ByteBuffer.wrap(s3.getBytes(StandardCharsets.US_ASCII)).readAsciiString(2, 3) shouldBe "cde"
  }

  "read2ByteInt" should "parse 2 byte unsigned number" in {
    ByteBuffer.wrap(Array[Byte](0, 127)).read2ByteUnsignedInt(0) shouldBe 127
    ByteBuffer.wrap(Array[Byte](0, 0, 127, 0)).read2ByteUnsignedInt(1) shouldBe 127
    ByteBuffer.wrap(Array[Byte](1, 0)).read2ByteUnsignedInt(0) shouldBe 256
    ByteBuffer.wrap(Array[Byte](-1, -1)).read2ByteUnsignedInt(0) shouldBe (1 << 16) - 1
  }

}
