package com.washdog.app

import com.washdog.app.util.Recurrence
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    @Test
    fun stopped_series_preserves_completed_visits_and_survives_reloading_old_cloud_data() {
        fun appointment(id: String, date: String, status: ApptStatus, series: String = "s1") = Appointment(
            id = id, clientName = "Samuel", petName = "Luna", service = "Baño", time = "09:00",
            date = date, status = status, repeatEveryDays = 7, seriesId = series
        )
        val stoppedAt = "2026-01-10T12:00:00Z"
        val oldCloud = listOf(
            appointment("past", "2026-01-01", ApptStatus.DONE),
            appointment("pending", "2026-01-16", ApptStatus.SCHEDULED),
            appointment("completed", "2026-01-31", ApptStatus.DONE),
            appointment("other", "2026-01-16", ApptStatus.SCHEDULED, "s2")
        )
        val stopped = Recurrence.stopSeries(oldCloud, "s1", stoppedAt)
        assertEquals(listOf("past", "completed", "other"), stopped.map { it.id })
        assertTrue(stopped.filter { it.seriesId == "s1" }.all { it.repeatEveryDays == null })
        assertEquals(7, stopped.last().repeatEveryDays)
        assertEquals(stopped, Recurrence.stopSeries(stopped, "s1", stoppedAt))
        val lateCloud = stopped + appointment("late", "2026-02-07", ApptStatus.SCHEDULED)
        assertEquals(stopped, Recurrence.stopSeries(lateCloud, "s1", stoppedAt))
    }

    @Test
    fun occurrences_every_15_days_excludes_start() {
        val result = Recurrence.occurrencesWithinHorizon("2026-01-01", everyDays = 15, horizonDays = 45)
        // 01-01 es el inicio (excluido); dentro de +45 días: 16, 31, y 15-Feb (día 46) queda fuera.
        assertEquals(listOf("2026-01-16", "2026-01-31", "2026-02-15"), result)
    }

    @Test
    fun occurrences_weekly() {
        val result = Recurrence.occurrencesWithinHorizon("2026-03-01", everyDays = 7, horizonDays = 21)
        assertEquals(listOf("2026-03-08", "2026-03-15", "2026-03-22"), result)
    }

    @Test
    fun occurrences_zero_or_negative_interval_is_empty() {
        assertTrue(Recurrence.occurrencesWithinHorizon("2026-01-01", 0).isEmpty())
        assertTrue(Recurrence.occurrencesWithinHorizon("2026-01-01", -5).isEmpty())
    }

    @Test
    fun occurrences_invalid_date_is_empty() {
        assertTrue(Recurrence.occurrencesWithinHorizon("not-a-date", 7).isEmpty())
    }

    @Test
    fun missing_skips_existing_and_past() {
        val today = LocalDate.parse("2026-01-20")
        // Serie cada 15 días desde 01-01: fechas 01-01, 01-16, 01-31, 02-15...
        // Con today=01-20 y horizonte 30d (hasta 02-19): candidatas futuras 01-31, 02-15.
        val existing = setOf("2026-01-31") // 01-31 ya existe → solo falta 02-15.
        val result = Recurrence.missingOccurrences(
            startDate = "2026-01-01",
            everyDays = 15,
            existingDates = existing,
            today = today,
            horizonDays = 30
        )
        assertEquals(listOf("2026-02-15"), result)
    }

    @Test
    fun occurrenceId_is_deterministic() {
        assertEquals("s1@2026-01-16", Recurrence.occurrenceId("s1", "2026-01-16"))
    }

    @Test
    fun missing_generates_future_when_none_exist() {
        val today = LocalDate.parse("2026-01-20")
        val result = Recurrence.missingOccurrences(
            startDate = "2026-01-01",
            everyDays = 15,
            existingDates = emptySet(),
            today = today,
            horizonDays = 30
        )
        assertEquals(listOf("2026-01-31", "2026-02-15"), result)
    }
}
