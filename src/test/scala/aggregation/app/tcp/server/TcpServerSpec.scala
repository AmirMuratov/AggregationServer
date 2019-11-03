package aggregation.app.tcp.server

import java.net.InetSocketAddress
import java.time.{Clock, Instant, ZoneId}

import aggregation.app.config.AppConfig.TcpServerConfig
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source, Tcp}
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.DurationInt

class TcpServerSpec extends TestKit(ActorSystem("ServerSpec"))
  with FlatSpecLike
  with ScalaFutures
  with Matchers {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(2.seconds, 100.millis)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher


  "TcpServer" should "send initial message on connect" in new Wiring {
    override val initMessage = ByteString(Array[Byte](0, 1, 2, 3))

    val initMessagePromise = Promise[ByteString]

    val flow = Flow[ByteString].map { byteString =>
      initMessagePromise.trySuccess(byteString)
      byteString
    }

    override def sourceMock =
      Source.single(Tcp.IncomingConnection(addr, addr, flow)).mapMaterializedValue(_ => Future.never)

    server.start()

    initMessagePromise.future.futureValue shouldBe initMessage
  }


  it should "set correct connect time and send messages to connected client" in new Wiring {
    override val initMessage = ByteString(Array.empty[Byte])
    val message = ByteString(Array[Byte](1, 2, 3, 4, 5))

    val initMessagePromise = Promise[ByteString]
    val broadcastMessagePromise = Promise[ByteString]

    val flow = Flow[ByteString].map { byteString =>
      if (byteString.isEmpty)
        initMessagePromise.trySuccess(byteString)
      else
        broadcastMessagePromise.trySuccess(byteString)
      byteString
    }

    override def sourceMock =
      Source.single(Tcp.IncomingConnection(addr, addr, flow)).mapMaterializedValue(_ => Future.never)

    server.start()
    initMessagePromise.future.futureValue
    val connection: TcpServer.ConnectionInfo = server.getCurrentConnections.head
    server.send(connection.id, message)

    connection.startedAt shouldBe instant
    broadcastMessagePromise.future.futureValue shouldBe message
  }

  trait Wiring {
    val addr = InetSocketAddress.createUnresolved("127.0.0.1", 1111)
    val config = TcpServerConfig("0.0.0.0", 8080)
    val instant = Instant.now()

    def initMessage: ByteString

    def sourceMock: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]]

    val server = new TcpServerImpl(_ => initMessage, config) {
      override protected val clock: Clock = Clock.fixed(instant, ZoneId.systemDefault())

      override def connections: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] = sourceMock
    }

  }

}
