package com.washdog.app.util

import com.washdog.app.data.models.Appointment

object OverlapChecker {

    fun findConflict(candidate: Appointment, existing: List<Appointment>): Appointment? {
        val candStart = toMinutes(candidate.time) ?: return null
        val candEnd   = toMinutes(candidate.endTime)
            .takeIf { candidate.endTime.isNotEmpty() } ?: (candStart + 60)

        return existing.firstOrNull { appt ->
            if (appt.id == candidate.id)      return@firstOrNull false
            if (appt.date != candidate.date)  return@firstOrNull false
            val exStart = toMinutes(appt.time) ?: return@firstOrNull false
            val exEnd   = toMinutes(appt.endTime)
                .takeIf { appt.endTime.isNotEmpty() } ?: (exStart + 60)
            candStart < exEnd && candEnd > exStart
        }
    }

    internal fun toMinutes(time: String): Int? {
        if (time.isBlank()) return null
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 2) parts[0] * 60 + parts[1] else null
    }
}
