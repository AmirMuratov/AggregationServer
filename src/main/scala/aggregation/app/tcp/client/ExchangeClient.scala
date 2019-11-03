package aggregation.app.tcp.client

import java.nio.ByteOrder

import aggregation.app.config.AppConfig.ExchangeClientConfig
import aggregation.app.model.ExchangeTransaction
import aggregation.app.service.TransactionAggregationService
import aggregation.app.tcp.client.ExchangeTransactionParser.ParsingError
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Framing, RestartSource, Sink, Source, Tcp}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait ExchangeClient {
  def start(): Future[Done]
}

class ExchangeClientImpl(aggregationService: TransactionAggregationService,
                         config: ExchangeClientConfig)
                        (implicit actorSystem: ActorSystem,
                         materializer: Materializer) extends ExchangeClient with StrictLogging {


  override def start(): Future[Done] = {
    clientSource
      .via(parseTransactions)
      .via(filterCorrupted)
      .async
      .via(handleTransactions)
      .runWith(Sink.ignore)
  }

  protected def clientSource: Source[ByteString, NotUsed] =
    RestartSource.withBackoff(
      minBackoff = config.connectionRetry.minBackoff,
      maxBackoff = config.connectionRetry.maxBackoff,
      randomFactor = 0) { () =>

      logger.info(s"Connecting to ${config.host}:${config.port}...")
      Source.maybe.via(Tcp().outgoingConnection(config.host, config.port))
    }

  private def parseTransactions =
    Flow[ByteString]
      .via(Framing.lengthField(2, 0, 1 << 16, ByteOrder.BIG_ENDIAN))
      .map(ExchangeTransactionParser.parse)


  private def filterCorrupted =
    Flow[Either[ParsingError, ExchangeTransaction]]
      .map {
        case left@Left(error) =>
          logger.error(s"Error while parsing transaction: $error")
          left
        case other => other
      }
      .collect {
        case Right(transaction) => transaction
      }

  private def handleTransactions =
    Flow[ExchangeTransaction]
      .map { transaction =>
        Try(aggregationService.handleTransaction(transaction)) match {
          case Success(_) => Done
          case Failure(exception) =>
            logger.error("exception in aggregationService.handleTransaction method", exception)
            Done
        }
      }

}
