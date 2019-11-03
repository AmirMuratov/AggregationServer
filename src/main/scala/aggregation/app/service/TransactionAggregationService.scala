package aggregation.app.service

import java.time.{Clock, Instant}

import aggregation.app.config.AppConfig.CandleServiceConfig
import aggregation.app.model.{Candle, ExchangeTransaction, IntervalId}
import aggregation.app.service.TransactionAggregationServiceImpl._
import aggregation.app.util.InstantOps
import akka.actor.Scheduler
import com.typesafe.scalalogging.StrictLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext

trait TransactionAggregationService {

  def handleTransaction(transaction: ExchangeTransaction): Unit

  def getCandlesForPreviousInterval(curInterval: IntervalId): Seq[Candle]

  def getHistory(curInterval: IntervalId): Seq[Candle]

}

class TransactionAggregationServiceImpl(config: CandleServiceConfig)
                                       (implicit scheduler: Scheduler,
                                        executionContext: ExecutionContext) extends TransactionAggregationService with StrictLogging {

  protected def clock: Clock = Clock.systemUTC()

  scheduler.schedule(Instant.now(clock).getDurationUntilNextInterval(config.interval), config.interval) {
    val oldestInterval = Instant.now(clock).getInterval(candleInterval) - historySize - 1
    timeIntervalsData.remove(oldestInterval)
  }

  override def handleTransaction(transaction: ExchangeTransaction): Unit = {
    logger.debug(s"New transaction: $transaction")
    val oldestIntervalStart = getIntervalStartTime(Instant.now(clock).getInterval(candleInterval) - historySize)

    if (transaction.timestamp.isBefore(oldestIntervalStart)) {
      logger.warn(s"Transaction $transaction is too old, skipping")
    } else {
      val intervalId = transaction.timestamp.getInterval(candleInterval)
      val tickerCandles = timeIntervalsData.getOrElseUpdate(intervalId, new TrieMap())

      tickerCandles.updateWith(transaction.ticker) {
        case None => Some(CandleData.initWithTransaction(transaction))
        case Some(data) => Some(data.update(transaction))
      }
    }

  }

  override def getCandlesForPreviousInterval(curInterval: IntervalId): Seq[Candle] =
    getCandlesForInterval(curInterval - 1)


  override def getHistory(curInterval: IntervalId): Seq[Candle] = {
    val lastCompletedInterval = curInterval - 1
    val oldestInterval = curInterval - historySize

    (oldestInterval to lastCompletedInterval).flatMap(getCandlesForInterval)
  }

  private def getCandlesForInterval(intervalId: IntervalId): Seq[Candle] = {
    val intervalStartTime = getIntervalStartTime(intervalId)

    timeIntervalsData.get(intervalId)
      .map(_.iterator.map { case (ticker, candleData) =>
        buildCandle(intervalStartTime, ticker, candleData)
      }.toSeq)
      .getOrElse(Seq())
  }

  private def getIntervalStartTime(intervalId: IntervalId): Instant =
    Instant.ofEpochMilli(intervalId * candleInterval.toMillis)


  private val timeIntervalsData: TrieMap[IntervalId, TrieMap[String, CandleData]] = new TrieMap()

  private val historySize = config.historySize
  private val candleInterval = config.interval

}

object TransactionAggregationServiceImpl {

  private case class CandleData(open: Double,
                                close: Double,
                                high: Double,
                                low: Double,
                                volume: Long,
                                earliest: Instant,
                                latest: Instant) {
    def update(transaction: ExchangeTransaction): CandleData = {
      CandleData(
        open = if (transaction.timestamp.isBefore(earliest)) transaction.price else open,
        close = if (transaction.timestamp.isAfter(latest)) transaction.price else close,
        high = Math.max(high, transaction.price),
        low = Math.min(low, transaction.price),
        volume = volume + transaction.volume,
        earliest = if (transaction.timestamp.isBefore(earliest)) transaction.timestamp else earliest,
        latest = if (transaction.timestamp.isAfter(latest)) transaction.timestamp else latest,
      )
    }
  }

  private object CandleData {
    def initWithTransaction(transaction: ExchangeTransaction): CandleData = {
      CandleData(
        open = transaction.price,
        close = transaction.price,
        high = transaction.price,
        low = transaction.price,
        volume = transaction.volume,
        earliest = transaction.timestamp,
        latest = transaction.timestamp
      )
    }
  }


  private def buildCandle(instant: Instant, ticker: String, candleData: CandleData): Candle =
    Candle(
      ticker = ticker,
      timestamp = instant,
      open = candleData.open,
      close = candleData.close,
      high = candleData.high,
      low = candleData.low,
      volume = candleData.volume,
    )

}