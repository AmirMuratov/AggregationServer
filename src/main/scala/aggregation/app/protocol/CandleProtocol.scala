package aggregation.app.protocol

import java.time.Instant

import aggregation.app.model.Candle
import akka.util.ByteString
import tethys._
import tethys.derivation.semiauto.jsonWriter
import tethys.jackson.jacksonTokenWriterProducer

object CandleProtocol {

  private implicit val instantWriter: JsonWriter[Instant] = JsonWriter.stringWriter.contramap(_.toString)

  private implicit val jsonCandleWriter: JsonWriter[Candle] = jsonWriter

  def serializeCandles(candles: Seq[Candle]): ByteString =
    if (candles.isEmpty)
      ByteString.empty
    else
      ByteString(candles.map(_.asJson).mkString("", "\n", "\n"))

}
