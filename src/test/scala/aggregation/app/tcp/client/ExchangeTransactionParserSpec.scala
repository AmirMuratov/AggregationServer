package aggregation.app.tcp.client

import java.time.Instant

import aggregation.app.model.ExchangeTransaction
import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}

class ExchangeTransactionParserSpec extends FlatSpec
  with Matchers {

  "ExchangeTransactionParser.parse" should "parse transactions" in {
    val transaction = ExchangeTransaction(Instant.parse("2019-10-27T17:34:29.882Z"), "SPY", 98.85, 9100)
    val serializedTransaction: Array[Byte] =
      Array[Byte](0, 25, 0, 0, 1, 110, 14, 72, -101, -6, 0, 3, 83, 80, 89, 64, 88, -74, 102,
        102, 102, 102, 102, 0, 0, 35, -116)

    ExchangeTransactionParser.parse(ByteString(serializedTransaction)) shouldBe Right(transaction)
  }


  it should "should not fail on incorrect data field bytes" in {
    val serializedTransaction: Array[Byte] =
      Array[Byte](0, 25, -128, -128, -128, -128, -128, -128, -128, -128, 0, 3,
        -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128)

    ExchangeTransactionParser.parse(ByteString(serializedTransaction)) shouldBe an[Right[_, _]]
  }


  it should "should fail with ParsingError if message contains 1 byte" in {
    val serializedTransaction: Array[Byte] = Array[Byte](0)

    ExchangeTransactionParser.parse(ByteString(serializedTransaction)) shouldBe an[Left[_, _]]
  }

  it should "should fail if message length doesn't correspond real length" in {
    val serializedTransaction: Array[Byte] =
      Array[Byte](0, 100, 0, 0, 1, 110, 14, 72, -101, -6, 0, 3, 83, 80, 89, 64,
        88, -74, 102, 102, 102, 102, 102, 0, 0, 35, -116)

    ExchangeTransactionParser.parse(ByteString(serializedTransaction)) shouldBe an[Left[_, _]]
  }
}
