package aggregation.app.tcp.server


import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicLong

import aggregation.app.config.AppConfig.TcpServerConfig
import aggregation.app.tcp.server.TcpServer.ConnectionInfo
import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete, Tcp}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


trait TcpServer {

  def start(): Future[Done]

  def getCurrentConnections: Seq[ConnectionInfo]

  def send(connectionId: Long, message: ByteString): Unit

}

class TcpServerImpl(messageOnNewConnection: Instant => ByteString,
                    config: TcpServerConfig)
                   (implicit actorSystem: ActorSystem,
                    materializer: Materializer,
                    executionContext: ExecutionContext) extends TcpServer with StrictLogging {

  protected val clock: Clock = Clock.systemUTC()

  override def start(): Future[Done] = {
    val serverBindingFuture = connections
      .map(handleNewConnection)
      .toMat(Sink.ignore)(Keep.left)
      .run()
    serverBindingFuture.flatMap(_.whenUnbound)
  }

  override def getCurrentConnections: Seq[ConnectionInfo] = currentConnections.values.map(_._2).toSeq

  override def send(connectionId: Long, message: ByteString): Unit = {
    currentConnections.get(connectionId) match {
      case None => logger.error(s"connection with id $connectionId not found")
      case Some((queue, _)) =>
        queue.offer(message).andThen {
          case Failure(exception) =>
            logger.error(s"Can't send message to connection", exception)
            deleteConnection(connectionId)
        }
        ()
    }
  }

  protected def connections: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind(config.interface, config.port)

  private def handleNewConnection(connection: IncomingConnection): Unit = {
    val id = idGenerator.incrementAndGet()
    val connectedAt = Instant.now(clock)
    logger.info(s"New connection ${connection.remoteAddress}")
    val queueSource = Source.queue[ByteString](bufferSize, OverflowStrategy.fail)

    val (queue, closeFuture) =
      queueSource
        .viaMat(connection.flow)(Keep.left)
        .toMat(Sink.ignore)(Keep.both)
        .run()

    queue.offer(messageOnNewConnection(connectedAt)).onComplete {
      case Success(_) =>
        currentConnections.put(id, (queue, ConnectionInfo(id, connectedAt)))
        closeFuture.onComplete {
          case Success(_) =>
            logger.info(s"Closing connection ${connection.remoteAddress}")
            deleteConnection(id)
          case Failure(exception) =>
            logger.error(s"Exception while handling connection ${connection.remoteAddress}", exception)
            deleteConnection(id)
        }
      case Failure(exception) =>
        logger.error(s"Can't send init message to connection", exception)
        queue.complete()
    }
  }

  private def deleteConnection(id: Long): Unit = {
    currentConnections.get(id).foreach(_._1.complete())
    currentConnections.remove(id)
  }

  private val idGenerator = new AtomicLong(0)
  private val currentConnections: TrieMap[Long, (SourceQueueWithComplete[ByteString], ConnectionInfo)] = TrieMap.empty

  private val bufferSize = 100

}

object TcpServer {

  case class ConnectionInfo(id: Long, startedAt: Instant)

}