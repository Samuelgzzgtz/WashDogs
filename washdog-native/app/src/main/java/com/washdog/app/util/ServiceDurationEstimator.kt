package com.washdog.app.util

object ServiceDurationEstimator {
    fun suggest(serviceName: String): Int {
        val n = serviceName.lowercase()
            .replace("á","a").replace("é","e").replace("í","i")
            .replace("ó","o").replace("ú","u").replace("ñ","n")
        return when {
            n.contains("jumbo")                         -> 360
            n.contains("grande")                        -> 240
            n.contains("mediano")                       -> 120
            n.contains("pequen") || n.contains("gato")  -> 90
            else                                        -> 60
        }
    }
}
