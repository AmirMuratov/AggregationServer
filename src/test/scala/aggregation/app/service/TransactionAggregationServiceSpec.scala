package aggregation.app.service

import java.time.{Clock, Instant, ZoneId}

import aggregation.app.config.AppConfig.CandleServiceConfig
import aggregation.app.model.{Candle, ExchangeTransaction}
import akka.actor.{ActorSystem, Scheduler}
import akka.event.NoLogging
import akka.testkit.{ExplicitlyTriggeredScheduler, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class TransactionAggregationServiceSpec extends TestKit(ActorSystem("TransactionAggregationServiceSpec"))
  with FlatSpecLike
  with Matchers
  with ScalaFutures {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(2.seconds, 100.millis)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val scheduler: Scheduler =
    new ExplicitlyTriggeredScheduler(ConfigFactory.empty(), NoLogging, null)

  "TransactionAggregationService" should "handle and aggregate transactions" in new Wiring {
    service.handleTransaction(ExchangeTransaction(transactionTimestamp, ticker, 100, 1000))
    val candle = service.getCandlesForPreviousInterval(intervalId).head
    candle.ticker shouldBe ticker
    candle.open === 100
    candle.close === 100
    candle.high === 100
    candle.low === 100
    candle.volume === 1000
  }

  it should "correctly calculate open, close, high, low, volume params " in new Wiring {
    service.handleTransaction(ExchangeTransaction(transactionTimestamp.minusMillis(1), ticker, 200, 1000))
    service.handleTransaction(ExchangeTransaction(transactionTimestamp, ticker, 50, 2000))
    service.handleTransaction(ExchangeTransaction(transactionTimestamp.plusMillis(1), ticker, 300, 3000))
    val candle = service.getCandlesForPreviousInterval(intervalId).head
    candle.ticker shouldBe ticker
    candle.open === 200
    candle.close === 300
    candle.high === 300
    candle.low === 50
    candle.volume === 6000
  }

  it should "return history candles for N intervals" in new Wiring {
    service.handleTransaction(ExchangeTransaction(transactionTimestamp, ticker, 100, 1000))
    service.handleTransaction(ExchangeTransaction(transactionTimestamp.minusMillis(config.interval.toMillis), ticker, 100, 1000))
    service.handleTransaction(ExchangeTransaction(transactionTimestamp.minusMillis(config.interval.toMillis * 2), ticker, 100, 1000))
    service.handleTransaction(ExchangeTransaction(transactionTimestamp.minusMillis(config.interval.toMillis * 3), ticker, 100, 1000))
    val candles = service.getHistory(intervalId)
    candles.exists(_.timestamp == Instant.ofEpochMilli((intervalId - 1) * config.interval.toMillis)) shouldBe true
    candles.exists(_.timestamp == Instant.ofEpochMilli((intervalId - 2) * config.interval.toMillis)) shouldBe true
    candles.exists(_.timestamp == Instant.ofEpochMilli((intervalId - 3) * config.interval.toMillis)) shouldBe true
    candles.exists(_.timestamp == Instant.ofEpochMilli((intervalId - 4) * config.interval.toMillis)) shouldBe false
  }


  trait Wiring {
    val ticker = "SOME_STOCK_TICKER"

    val config = CandleServiceConfig(1.minute, 3)
    val intervalId = 10000
    val instant = Instant.ofEpochMilli(config.interval.toMillis * intervalId + config.interval.toMillis / 2)
    val fixedClock = Clock.fixed(instant, ZoneId.systemDefault())
    val transactionTimestamp = instant.minusMillis(config.interval.toMillis)
    val service = new TransactionAggregationServiceImpl(config) {
      override protected def clock: Clock = fixedClock
    }
  }

}