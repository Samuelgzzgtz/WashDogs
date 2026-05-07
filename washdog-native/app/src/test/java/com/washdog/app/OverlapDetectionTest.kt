package com.washdog.app

import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.util.OverlapChecker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OverlapDetectionTest {

    private fun appt(id: String, date: String, start: String, end: String = "") = Appointment(
        id         = id,
        clientName = "Cliente $id",
        petName    = "Mascota $id",
        service    = "Baño",
        time       = start,
        date       = date,
        status     = ApptStatus.SCHEDULED,
        endTime    = end
    )

    private val DATE = "2026-05-10"

    @Test fun `no overlap when adjacent — end of A equals start of B`() {
        val existing = listOf(appt("1", DATE, "09:00", "10:00"))
        val candidate = appt("2", DATE, "10:00", "11:00")
        assertNull(OverlapChecker.findConflict(candidate, existing))
    }

    @Test fun `overlap when ranges intersect`() {
        val existing = listOf(appt("1", DATE, "09:00", "11:00"))
        val candidate = appt("2", DATE, "10:00", "12:00")
        assertNotNull(OverlapChecker.findConflict(candidate, existing))
    }

    @Test fun `overlap when candidate is fully contained`() {
        val existing = listOf(appt("1", DATE, "09:00", "12:00"))
        val candidate = appt("2", DATE, "10:00", "11:00")
        assertNotNull(OverlapChecker.findConflict(candidate, existing))
    }

    @Test fun `no overlap when different dates`() {
        val existing = listOf(appt("1", "2026-05-10", "09:00", "11:00"))
        val candidate = appt("2", "2026-05-11", "09:00", "11:00")
        assertNull(OverlapChecker.findConflict(candidate, existing))
    }

    @Test fun `no overlap with same id — updating same appointment`() {
        val existing = listOf(appt("1", DATE, "09:00", "10:00"))
        val candidate = appt("1", DATE, "09:00", "10:00")
        assertNull(OverlapChecker.findConflict(candidate, existing))
    }

    @Test fun `overlap defaults to 1h when endTime is empty`() {
        val existing = listOf(appt("1", DATE, "09:00"))
        val candidate = appt("2", DATE, "09:30")
        assertNotNull(OverlapChecker.findConflict(candidate, existing))
    }

    @Test fun `no overlap when candidate starts after default-1h window`() {
        val existing = listOf(appt("1", DATE, "09:00"))
        val candidate = appt("2", DATE, "10:00")
        assertNull(OverlapChecker.findConflict(candidate, existing))
    }
}
