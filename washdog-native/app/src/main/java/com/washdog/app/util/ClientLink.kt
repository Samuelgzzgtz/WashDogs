package com.washdog.app.util

import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.Client

/**
 * Migración de citas al vínculo estable por `clientId` (v1.3.0). Las citas legacy se
 * guardaban ligadas al cliente solo por nombre; esta función rellena el `clientId`
 * faltante matcheando `clientName` con `Client.ownerName`. Pura e idempotente.
 */
object ClientLink {

    fun backfill(appointments: List<Appointment>, clients: List<Client>): List<Appointment> {
        if (appointments.none { it.clientId.isBlank() }) return appointments
        val byName = clients.associateBy { it.ownerName }
        return appointments.map { a ->
            if (a.clientId.isBlank()) {
                byName[a.clientName]?.let { a.copy(clientId = it.id) } ?: a
            } else a
        }
    }
}
