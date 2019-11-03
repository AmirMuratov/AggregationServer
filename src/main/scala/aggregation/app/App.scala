package aggregation.app

import aggregation.app.wiring.Wiring
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success}

object App extends Wiring with StrictLogging {
  def main(args: Array[String]): Unit = {
    client.start().onComplete {
      case Failure(exception) =>
        logger.error("Exchange client failed with exception", exception)
        sys.exit(1)
      case Success(_) =>
        logger.info("Exchange client closed connection")
    }
    server.start().onComplete {
      case Failure(exception) =>
        logger.error("Broadcast server failed with exception", exception)
        sys.exit(1)
      case Success(_) =>
        logger.error("Broadcast server finished stream")
    }
    candleBroadcastService.start()

  }
}
