package com.washdog.app.data.models

enum class TxType { IN, OUT }

data class Transaction(
    val id: String,
    val type: TxType,
    val amount: Double,
    val concept: String,
    val detail: String,
    val dateTime: String
)
