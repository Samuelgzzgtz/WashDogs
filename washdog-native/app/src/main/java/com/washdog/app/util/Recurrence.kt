package com.washdog.app.util

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus

/**
 * Lógica pura de recurrencia de citas (sin dependencias de Android para poder
 * testearla como unit test JVM). Genera las fechas ISO-8601 ("yyyy-MM-dd") de una
 * serie que se repite cada [everyDays] días dentro de un horizonte.
 */
object Recurrence {

    /** Horizonte por defecto: se materializan las ocurrencias de los próximos 90 días. */
    const val DEFAULT_HORIZON_DAYS = 90

    /**
     * Id determinista de una ocurrencia generada: `<seriesId>@<fecha>`. Al ser el mismo
     * en todos los dispositivos, el merge por id deduplica en vez de crear copias si dos
     * dispositivos materializan la misma serie sin conexión a la vez.
     */
    fun occurrenceId(seriesId: String, date: String): String = "$seriesId@$date"

    /** Aplica también las cancelaciones persistidas mientras el dispositivo estaba sin conexión. */
    fun stopSeries(current: List<Appointment>, seriesId: String, stoppedAt: String): List<Appointment> {
        val stopDate = seriesStopDate(stoppedAt)
        return current.mapNotNull { a ->
            when {
                a.seriesId != seriesId -> a
                a.date >= stopDate && a.status != ApptStatus.DONE -> null
                a.repeatEveryDays == null -> a
                else -> a.copy(repeatEveryDays = null, updatedAt = maxOf(a.updatedAt, stoppedAt))
            }
        }
    }

    fun seriesStopDate(stoppedAt: String): String =
        Instant.parse(stoppedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /**
     * Fechas de la serie que caen en `(startDate, startDate + horizonDays]`.
     *
     * NO incluye [startDate] (esa es la cita original que el usuario ya creó); devuelve
     * solo las ocurrencias futuras a materializar. Devuelve vacío si [everyDays] <= 0 o
     * si las fechas son inválidas.
     */
    fun occurrencesWithinHorizon(
        startDate: String,
        everyDays: Int,
        horizonDays: Int = DEFAULT_HORIZON_DAYS
    ): List<String> {
        if (everyDays <= 0 || horizonDays <= 0) return emptyList()
        val start = parseOrNull(startDate) ?: return emptyList()
        val limit = start.plusDays(horizonDays.toLong())
        val result = mutableListOf<String>()
        var next = start.plusDays(everyDays.toLong())
        while (!next.isAfter(limit)) {
            result.add(next.toString())
            next = next.plusDays(everyDays.toLong())
        }
        return result
    }

    /**
     * Fechas de la serie dentro del horizonte que aún NO existen (top-up incremental).
     * [existingDates] son las fechas ya materializadas de la serie.
     *
     * El horizonte se calcula desde [today] (no desde el inicio de la serie) para que la
     * serie siga "avanzando" en cada arranque. Genera desde [startDate] cada [everyDays].
     */
    fun missingOccurrences(
        startDate: String,
        everyDays: Int,
        existingDates: Set<String>,
        today: LocalDate = LocalDate.now(),
        horizonDays: Int = DEFAULT_HORIZON_DAYS
    ): List<String> {
        if (everyDays <= 0 || horizonDays <= 0) return emptyList()
        val start = parseOrNull(startDate) ?: return emptyList()
        val limit = today.plusDays(horizonDays.toLong())
        val result = mutableListOf<String>()
        var next = start
        while (!next.isAfter(limit)) {
            val iso = next.toString()
            // Solo materializamos ocurrencias futuras (>= hoy) que falten.
            if (!next.isBefore(today) && iso !in existingDates) result.add(iso)
            next = next.plusDays(everyDays.toLong())
        }
        return result
    }

    private fun parseOrNull(date: String): LocalDate? =
        try { LocalDate.parse(date) } catch (e: Exception) { null }
}
