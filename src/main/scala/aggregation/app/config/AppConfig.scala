package aggregation.app.config

import aggregation.app.config.AppConfig.{CandleServiceConfig, ExchangeClientConfig, TcpServerConfig}
import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase

import scala.concurrent.duration.FiniteDuration

case class AppConfig(server: TcpServerConfig,
                     exchangeClient: ExchangeClientConfig,
                     candleService: CandleServiceConfig)

object AppConfig {

  case class TcpServerConfig(interface: String, port: Int)

  case class RetryPolicy(minBackoff: FiniteDuration, maxBackoff: FiniteDuration)

  case class ExchangeClientConfig(host: String, port: Int, connectionRetry: RetryPolicy)

  case class CandleServiceConfig(interval: FiniteDuration, historySize: Int)

  def parse(config: Config): AppConfig = config.as[AppConfig]

}