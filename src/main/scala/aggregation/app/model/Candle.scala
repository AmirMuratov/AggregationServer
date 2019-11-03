package aggregation.app.model

import java.time.Instant

case class Candle(ticker: String,
                  timestamp: Instant,
                  open: Double,
                  close: Double,
                  high: Double,
                  low: Double,
                  volume: Long)
