package com.washdog.app.util

import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.Client
import java.time.LocalDate

/**
 * Lógica pura para decidir qué clientes necesitan recordatorio: los que llevan
 * ≥ [minDays] días sin visitar **y** no tienen ya una cita futura agendada. Compartida
 * por la UI (`AppViewModel.clientsToRemind`) y el `ReminderWorker`.
 */
object RemindLogic {

    fun clientsNeedingReminder(
        clients: List<Client>,
        appointments: List<Appointment>,
        today: LocalDate = LocalDate.now(),
        minDays: Long = 30
    ): List<Client> {
        val todayIso = today.toString()
        // Clientes con al menos una cita de hoy en adelante (por id, o nombre si es legacy).
        val idsWithUpcoming = appointments.filter { it.date >= todayIso }
            .map { it.clientId }.filter { it.isNotBlank() }.toSet()
        val namesWithUpcoming = appointments.filter { it.date >= todayIso && it.clientId.isBlank() }
            .map { it.clientName }.toSet()

        return clients.filter { c ->
            val overdue = daysSince(c.lastVisit, today) >= minDays
            val hasUpcoming = c.id in idsWithUpcoming || c.ownerName in namesWithUpcoming
            overdue && !hasUpcoming
        }
    }

    fun daysSince(dateStr: String, today: LocalDate = LocalDate.now()): Long =
        try { today.toEpochDay() - LocalDate.parse(dateStr).toEpochDay() }
        catch (e: Exception) { 0L }
}
