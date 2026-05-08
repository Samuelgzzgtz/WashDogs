package com.washdog.app.data.models

data class ZonePrices(
    val nearFee:    Double = 0.0,    val nearBuffer:   Int = 0,
    val closeFee:   Double = 50.0,   val closeBuffer:  Int = 10,
    val mediumFee:  Double = 90.0,   val mediumBuffer: Int = 25,
    val farFee:     Double = 120.0,  val farBuffer:    Int = 40
)
