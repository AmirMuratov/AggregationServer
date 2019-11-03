package aggregation.app.wiring

import aggregation.app.service.{CandleBroadcastService, CandleBroadcastServiceImpl, TransactionAggregationServiceImpl}
import aggregation.app.tcp.client.ExchangeClientImpl
import aggregation.app.tcp.server.{TcpServer, TcpServerImpl}

trait Wiring extends BaseWiring {

  val aggregationService = new TransactionAggregationServiceImpl(appConfig.candleService)

  val client = new ExchangeClientImpl(aggregationService, appConfig.exchangeClient)

  lazy val candleBroadcastService: CandleBroadcastService =
    new CandleBroadcastServiceImpl(server, aggregationService, appConfig.candleService)

  lazy val server: TcpServer = new TcpServerImpl(candleBroadcastService.getHistoryCandles, appConfig.server)

}
