package aggregation.app.model

import java.time.Instant

case class ExchangeTransaction(timestamp: Instant,
                               ticker: String,
                               price: Double,
                               volume: Int)