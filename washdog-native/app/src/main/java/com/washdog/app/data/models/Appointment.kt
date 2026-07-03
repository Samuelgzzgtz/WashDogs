package com.washdog.app.data.models

enum class ApptStatus { DONE, NEXT, REMINDER, SCHEDULED }

data class Appointment(
    val id: String,
    val clientName: String,
    val petName: String,
    val service: String,
    val time: String,
    val date: String,
    val status: ApptStatus,
    val endTime: String = "",
    // Recurrencia (v1.3.0): si [repeatEveryDays] != null, la cita pertenece a una serie
    // que se repite cada N días; todas las ocurrencias comparten [seriesId].
    val repeatEveryDays: Int? = null,
    val seriesId: String? = null,
    // Marca de última modificación (ISO-8601 instant) para el merge last-write-wins.
    val updatedAt: String = ""
)
