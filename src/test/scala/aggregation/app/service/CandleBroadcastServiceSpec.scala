package aggregation.app.service

import java.nio.charset.StandardCharsets
import java.time.{Clock, Instant, ZoneId}

import aggregation.app.config.AppConfig.CandleServiceConfig
import aggregation.app.model.Candle
import aggregation.app.tcp.server.TcpServer
import aggregation.app.tcp.server.TcpServer.ConnectionInfo
import akka.actor.Scheduler
import akka.event.NoLogging
import akka.testkit.ExplicitlyTriggeredScheduler
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class CandleBroadcastServiceSpec extends FlatSpec
  with Matchers
  with MockFactory
  with ScalaFutures {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(2.seconds, 100.millis)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "getHistoryCandles" should "return serialized history candles" in new Wiring {
    val instant = Instant.ofEpochMilli(1111111)
    val interval = instant.toEpochMilli / config.interval.toMillis

    aggregationService.getHistory _ expects interval returns Seq(candle)

    service.getHistoryCandles(instant) shouldBe serializedCandle
  }

  "start" should "start broadcasting every interval" in new Wiring {
    val curInterval = fixedClock.millis() / config.interval.toMillis
    (aggregationService.getCandlesForPreviousInterval _).expects(curInterval).returning(Seq(candle)).twice()
    (tcpServer.getCurrentConnections _).expects().returning(
      Seq(ConnectionInfo(connectionId, fixedClock.instant().minusMillis(config.interval.toMillis)))).twice()
    (tcpServer.send _).expects(connectionId, serializedCandle).returning().twice()

    service.start()
    scheduler.timePasses(config.interval * 2)
  }

  it should "skip connections if they already received data for current interval" in new Wiring {
    (aggregationService.getCandlesForPreviousInterval _).expects(*).returning(Seq(candle))
    (tcpServer.getCurrentConnections _).expects().returning(Seq(ConnectionInfo(connectionId, fixedClock.instant())))
    (tcpServer.send _).expects(*, *).returning().never()

    service.start()
    scheduler.timePasses(config.interval)
  }

  trait Wiring {
    val connectionId = 23
    val candle = Candle("AAPL", Instant.ofEpochMilli(1570640804000L), 1, 1, 1, 1, 10)
    val serializedCandle =
      ByteString(
        """{"ticker":"AAPL","timestamp":"2019-10-09T17:06:44Z","open":1.0,"close":1.0,"high":1.0,"low":1.0,"volume":10}""" + "\n",
        StandardCharsets.US_ASCII)

    implicit val scheduler: ExplicitlyTriggeredScheduler =
      new ExplicitlyTriggeredScheduler(ConfigFactory.empty(), NoLogging, null)


    val tcpServer = mock[TcpServer]
    val aggregationService = mock[TransactionAggregationService]
    val config = CandleServiceConfig(1.minute, 5)
    val fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    val service = new CandleBroadcastServiceImpl(tcpServer, aggregationService, config) {
      override protected val clock: Clock = fixedClock
    }
  }

}