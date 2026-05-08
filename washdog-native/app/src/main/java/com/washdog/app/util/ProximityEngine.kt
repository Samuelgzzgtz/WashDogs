package com.washdog.app.util

import com.washdog.app.data.models.ServiceLocation
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.data.models.ZonePrices

object ProximityEngine {

    private val NEAR_KEYWORDS  = listOf("granada")
    private val CLOSE_KEYWORDS = listOf("ensenada", "cedral", "ajusco")

    fun detectZone(address: String): ServiceZone {
        val n = address.lowercase()
        return when {
            NEAR_KEYWORDS.any  { n.contains(it) } -> ServiceZone.NEAR
            CLOSE_KEYWORDS.any { n.contains(it) } -> ServiceZone.CLOSE
            n.contains("mitras poniente")          -> ServiceZone.MEDIUM
            else                                   -> ServiceZone.FAR
        }
    }

    fun travelFee(zone: ServiceZone, location: ServiceLocation, prices: ZonePrices): Double =
        if (location == ServiceLocation.BASE) 0.0
        else when (zone) {
            ServiceZone.NEAR   -> prices.nearFee
            ServiceZone.CLOSE  -> prices.closeFee
            ServiceZone.MEDIUM -> prices.mediumFee
            ServiceZone.FAR    -> prices.farFee
        }

    fun travelBuffer(zone: ServiceZone, location: ServiceLocation, prices: ZonePrices): Int =
        if (location == ServiceLocation.BASE) 0
        else when (zone) {
            ServiceZone.NEAR   -> prices.nearBuffer
            ServiceZone.CLOSE  -> prices.closeBuffer
            ServiceZone.MEDIUM -> prices.mediumBuffer
            ServiceZone.FAR    -> prices.farBuffer
        }
}
