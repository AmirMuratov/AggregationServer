package aggregation.app.tcp.client

import java.time.Instant

import aggregation.app.config.AppConfig
import aggregation.app.config.AppConfig.ExchangeClientConfig
import aggregation.app.model.ExchangeTransaction
import aggregation.app.service.TransactionAggregationService
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.duration._

class ExchangeClientSpec extends TestKit(ActorSystem("ExchangeClientSpec"))
  with FlatSpecLike
  with Matchers
  with ScalaFutures
  with MockFactory {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(2.seconds, 100.millis)
  implicit val materializer: Materializer = ActorMaterializer()

  "ExchangeClient" should "parse exchange transaction" in new Wiring {
    val bytes: Seq[ByteString] = Seq(ByteString(serializedTransaction))
    client.start().futureValue
    (aggregationService.handleTransaction _).verify(transaction).once()
  }

  it should "parse 2 exchange transactions in one bytes pack" in new Wiring {
    val bytes: Seq[ByteString] = Seq(ByteString(serializedTransaction ++ serializedTransaction))
    client.start().futureValue
    (aggregationService.handleTransaction _).verify(transaction).twice()
  }

  it should "parse 2 exchange transactions in 2 packs" in new Wiring {
    val bytes: Seq[ByteString] = Seq(ByteString(serializedTransaction), ByteString(serializedTransaction))
    client.start().futureValue
    (aggregationService.handleTransaction _).verify(transaction).twice()
  }

  it should "parse exchange transactions if bytes are coming one by one" in new Wiring {
    val bytes: Seq[ByteString] = serializedTransaction.map(byte => ByteString(Array(byte)))
    client.start().futureValue
    (aggregationService.handleTransaction _).verify(transaction).once()
  }

  trait Wiring {
    val transaction = ExchangeTransaction(Instant.parse("2019-10-27T17:34:29.882Z"), "SPY", 98.85, 9100)
    val serializedTransaction: Array[Byte] =
      Array[Byte](0, 25, 0, 0, 1, 110, 14, 72, -101, -6, 0, 3, 83, 80, 89, 64, 88, -74, 102, 102, 102, 102, 102, 0, 0, 35, -116)


    val aggregationService: TransactionAggregationService = stub[TransactionAggregationService]
    aggregationService.handleTransaction _ when transaction returns()

    val config: ExchangeClientConfig = ExchangeClientConfig("127.0.0.1", 5555, AppConfig.RetryPolicy(5.seconds, 6.seconds))

    def bytes: Seq[ByteString]

    val client: ExchangeClientImpl = new ExchangeClientImpl(aggregationService, config) {
      override protected def clientSource: Source[ByteString, NotUsed] = Source(bytes)
    }
  }

}
