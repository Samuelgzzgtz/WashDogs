package com.washdog.app.util

object ClosingBarrierValidator {
    fun minutesExceeded(
        startTime: String,
        durationMinutes: Int,
        travelBuffer: Int,
        closeTime: String
    ): Int {
        val start = toMinutes(startTime) ?: return 0
        val close = toMinutes(closeTime) ?: return 0
        val end   = start + durationMinutes + travelBuffer
        return maxOf(0, end - close)
    }

    private fun toMinutes(time: String): Int? {
        if (time.isBlank()) return null
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 2) parts[0] * 60 + parts[1] else null
    }
}
