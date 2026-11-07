package com.washdog.app.data.models

enum class TxType { IN, OUT }

/** Método de pago al ingresar dinero. La tarjeta se cuenta como transferencia. */
enum class PaymentMethod { CASH, TRANSFER }

data class Transaction(
    val id: String,
    val type: TxType,
    val amount: Double,
    val concept: String,
    val detail: String,
    val dateTime: String,
    // Marca de última modificación (ISO-8601 instant) para el merge last-write-wins.
    val updatedAt: String = "",
    // Método de pago, obligatorio solo en ingresos IN nuevos.
    // NULL en filas viejas y en gastos OUT: se muestra como "Sin método".
    val paymentMethod: PaymentMethod? = null
)
