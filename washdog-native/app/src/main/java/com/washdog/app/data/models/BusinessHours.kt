package com.washdog.app.data.models

data class BusinessHours(
    val openTime: String   = "09:00",
    val closeTime: String  = "19:00",
    val closedDays: List<Int> = listOf(0)   // 0=Dom, 1=Lun … 6=Sáb
)
