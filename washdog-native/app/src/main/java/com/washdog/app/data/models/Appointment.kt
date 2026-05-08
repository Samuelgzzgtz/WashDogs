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
    val serviceLocation: ServiceLocation = ServiceLocation.HOME,
    val travelFee: Double = 0.0,
    val travelBuffer: Int = 0
)
