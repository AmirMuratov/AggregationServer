package aggregation.app.service

import java.time.{Clock, Instant}

import aggregation.app.config.AppConfig.CandleServiceConfig
import aggregation.app.protocol.CandleProtocol
import aggregation.app.tcp.server.TcpServer
import aggregation.app.util.InstantOps
import akka.actor.Scheduler
import akka.util.ByteString

import scala.concurrent.ExecutionContext
import scala.util.chaining._

trait CandleBroadcastService {

  def start(): Unit

  def getHistoryCandles(time: Instant): ByteString

}

class CandleBroadcastServiceImpl(broadcastServer: TcpServer,
                                 aggregationService: TransactionAggregationService,
                                 config: CandleServiceConfig)
                                (implicit scheduler: Scheduler,
                                 executionContext: ExecutionContext) extends CandleBroadcastService {

  protected val clock: Clock = Clock.systemUTC()

  override def start(): Unit =
    scheduler.schedule(Instant.now(clock).getDurationUntilNextInterval(candleInterval), candleInterval) {
      broadcast()
    }

  override def getHistoryCandles(now: Instant): ByteString =
    now.getInterval(candleInterval)
      .pipe(aggregationService.getHistory)
      .pipe(CandleProtocol.serializeCandles)

  private def broadcast(): Unit = {
    val currentInterval = Instant.now(clock).getInterval(candleInterval)
    val serializedMessage = currentInterval
      .pipe(aggregationService.getCandlesForPreviousInterval)
      .pipe(CandleProtocol.serializeCandles)

    broadcastServer.getCurrentConnections
      //filter out connections that already received data for current interval when connected
      .filter(connection => connection.startedAt.getInterval(candleInterval) < currentInterval)
      .foreach(connection => broadcastServer.send(connection.id, serializedMessage))
  }

  private val candleInterval = config.interval
}
