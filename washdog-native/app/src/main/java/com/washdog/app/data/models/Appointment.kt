package com.washdog.app.data.models

enum class ApptStatus { DONE, NEXT, REMINDER, SCHEDULED }

data class Appointment(
    val id: String,
    // Vínculo estable al cliente (v1.3.0). clientName se conserva como cache para mostrar
    // sin join y para citas legacy; el borrado en cascada usa clientId cuando existe.
    val clientId: String = "",
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
