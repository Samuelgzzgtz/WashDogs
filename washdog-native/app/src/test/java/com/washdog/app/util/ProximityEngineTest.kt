package com.washdog.app.util

import com.washdog.app.data.models.ServiceLocation
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.data.models.ZonePrices
import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityEngineTest {

    // ── detectZone ────────────────────────────────────────────────────────────

    @Test
    fun `detectZone returns NEAR for granada keyword`() {
        assertEquals(ServiceZone.NEAR, ProximityEngine.detectZone("Privada Granada 45"))
    }

    @Test
    fun `detectZone is case insensitive`() {
        assertEquals(ServiceZone.NEAR, ProximityEngine.detectZone("privada GRANADA 12, Mitras Poniente"))
    }

    @Test
    fun `detectZone returns CLOSE for ensenada`() {
        assertEquals(ServiceZone.CLOSE, ProximityEngine.detectZone("Privada Ensenada 10"))
    }

    @Test
    fun `detectZone returns CLOSE for cedral`() {
        assertEquals(ServiceZone.CLOSE, ProximityEngine.detectZone("Privada Cedral 5, Mitras Poniente"))
    }

    @Test
    fun `detectZone returns CLOSE for ajusco`() {
        assertEquals(ServiceZone.CLOSE, ProximityEngine.detectZone("Privada Ajusco 3"))
    }

    @Test
    fun `detectZone returns MEDIUM for mitras poniente without near keywords`() {
        assertEquals(ServiceZone.MEDIUM, ProximityEngine.detectZone("Calle Hidalgo 200, Mitras Poniente"))
    }

    @Test
    fun `detectZone returns FAR for unrelated address`() {
        assertEquals(ServiceZone.FAR, ProximityEngine.detectZone("Av. Constitución 500, Centro"))
    }

    @Test
    fun `detectZone returns FAR for empty address`() {
        assertEquals(ServiceZone.FAR, ProximityEngine.detectZone(""))
    }

    // ── travelFee ─────────────────────────────────────────────────────────────

    @Test
    fun `travelFee returns 0 for BASE regardless of zone`() {
        val prices = ZonePrices()
        assertEquals(0.0, ProximityEngine.travelFee(ServiceZone.FAR,    ServiceLocation.BASE, prices), 0.01)
        assertEquals(0.0, ProximityEngine.travelFee(ServiceZone.MEDIUM, ServiceLocation.BASE, prices), 0.01)
        assertEquals(0.0, ProximityEngine.travelFee(ServiceZone.CLOSE,  ServiceLocation.BASE, prices), 0.01)
        assertEquals(0.0, ProximityEngine.travelFee(ServiceZone.NEAR,   ServiceLocation.BASE, prices), 0.01)
    }

    @Test
    fun `travelFee returns correct fee per zone for HOME`() {
        val prices = ZonePrices()
        assertEquals(0.0,   ProximityEngine.travelFee(ServiceZone.NEAR,   ServiceLocation.HOME, prices), 0.01)
        assertEquals(50.0,  ProximityEngine.travelFee(ServiceZone.CLOSE,  ServiceLocation.HOME, prices), 0.01)
        assertEquals(90.0,  ProximityEngine.travelFee(ServiceZone.MEDIUM, ServiceLocation.HOME, prices), 0.01)
        assertEquals(120.0, ProximityEngine.travelFee(ServiceZone.FAR,    ServiceLocation.HOME, prices), 0.01)
    }

    @Test
    fun `travelFee respects custom ZonePrices`() {
        val prices = ZonePrices(farFee = 200.0)
        assertEquals(200.0, ProximityEngine.travelFee(ServiceZone.FAR, ServiceLocation.HOME, prices), 0.01)
    }

    // ── travelBuffer ──────────────────────────────────────────────────────────

    @Test
    fun `travelBuffer returns 0 for BASE regardless of zone`() {
        val prices = ZonePrices()
        assertEquals(0, ProximityEngine.travelBuffer(ServiceZone.FAR,    ServiceLocation.BASE, prices))
        assertEquals(0, ProximityEngine.travelBuffer(ServiceZone.MEDIUM, ServiceLocation.BASE, prices))
    }

    @Test
    fun `travelBuffer returns correct buffer per zone for HOME`() {
        val prices = ZonePrices()
        assertEquals(0,  ProximityEngine.travelBuffer(ServiceZone.NEAR,   ServiceLocation.HOME, prices))
        assertEquals(10, ProximityEngine.travelBuffer(ServiceZone.CLOSE,  ServiceLocation.HOME, prices))
        assertEquals(25, ProximityEngine.travelBuffer(ServiceZone.MEDIUM, ServiceLocation.HOME, prices))
        assertEquals(40, ProximityEngine.travelBuffer(ServiceZone.FAR,    ServiceLocation.HOME, prices))
    }
}
