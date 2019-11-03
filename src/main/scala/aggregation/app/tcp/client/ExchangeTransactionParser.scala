package aggregation.app.tcp.client

import java.time.Instant

import aggregation.app.model.ExchangeTransaction
import aggregation.app.util.ByteBufferOps
import akka.util.ByteString

object ExchangeTransactionParser {

  type ParsingError = String

  private val messageLenOffset = 0
  private val timestampOffset = 2
  private val tickerLenOffset = 10
  private val tickerOffset = 12
  private def priceOffset(tickerLen: Int) = 12 + tickerLen
  private def volumeOffset(tickerLen: Int) = 20 + tickerLen


  def parse(bytes: ByteString): Either[ParsingError, ExchangeTransaction] = {
    // | len | timestamp | ticker len | ticker | price       | volume
    // 0     2           10           12       12+tickerLen  20+tickerLen
    val buffer = bytes.asByteBuffer
    for {
      messageLength <- Either.cond(bytes.length >= messageLenOffset + 2, buffer.read2ByteUnsignedInt(messageLenOffset), "Can't read message length")
      tickerLength <- Either.cond(bytes.length >= tickerLenOffset + 2, buffer.read2ByteUnsignedInt(tickerLenOffset), "Can't read ticker length")
      isMessageCorrect = bytes.length == messageLength + 2 && messageLength >= tickerLength + 22
      _ <- Either.cond(isMessageCorrect, (), "Message length or ticker length are incorrect")
    } yield {
      val timestamp = Instant.ofEpochMilli(buffer.getLong(timestampOffset))
      val ticker = buffer.readAsciiString(tickerOffset, tickerLength)
      val price = buffer.getDouble(priceOffset(tickerLength))
      val size = buffer.getInt(volumeOffset(tickerLength))
      ExchangeTransaction(timestamp, ticker, price, size)
    }
  }
}
