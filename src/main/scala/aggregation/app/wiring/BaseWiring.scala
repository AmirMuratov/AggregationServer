package aggregation.app.wiring

import java.util.concurrent.ForkJoinPool

import aggregation.app.config.AppConfig
import akka.actor.{ActorSystem, Scheduler}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt

trait BaseWiring {
  val rawConfig: Config = ConfigFactory.load()
  val appConfig: AppConfig = AppConfig.parse(rawConfig)

  implicit val actorSystem: ActorSystem = ActorSystem.apply("AggregationApp")

  implicit val materializer: Materializer = ActorMaterializer()

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool())

  implicit val scheduler: Scheduler = actorSystem.scheduler

  sys.addShutdownHook {
    Await.ready(actorSystem.terminate(), 10.seconds)
  }

}
