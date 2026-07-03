package com.washdog.app

import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.util.RemindLogic
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RemindLogicTest {

    private val today = LocalDate.parse("2026-07-03")

    private fun client(id: String, name: String, lastVisit: String) =
        Client(id = id, ownerName = name, petName = "P", breed = "", lastVisit = lastVisit,
            needsReschedule = false, petType = PetType.DOG)

    private fun appt(clientId: String, clientName: String, date: String) =
        Appointment(id = "a-$date", clientId = clientId, clientName = clientName, petName = "P",
            service = "s", time = "10:00", date = date, status = ApptStatus.SCHEDULED)

    @Test
    fun overdue_client_without_upcoming_is_reminded() {
        val clients = listOf(client("c1", "Ana", "2026-05-01")) // ~63 días
        val result = RemindLogic.clientsNeedingReminder(clients, emptyList(), today)
        assertEquals(listOf("c1"), result.map { it.id })
    }

    @Test
    fun recent_client_is_not_reminded() {
        val clients = listOf(client("c1", "Ana", "2026-06-28")) // ~5 días
        assertEquals(emptyList<Client>(), RemindLogic.clientsNeedingReminder(clients, emptyList(), today))
    }

    @Test
    fun overdue_but_with_upcoming_appointment_is_excluded() {
        val clients = listOf(client("c1", "Ana", "2026-05-01"))
        val appts = listOf(appt("c1", "Ana", "2026-07-10")) // cita futura
        assertEquals(emptyList<Client>(), RemindLogic.clientsNeedingReminder(clients, appts, today))
    }

    @Test
    fun upcoming_match_by_name_for_legacy_appointment() {
        val clients = listOf(client("c1", "Ana", "2026-05-01"))
        val legacyAppt = appt("", "Ana", "2026-07-10") // sin clientId (legacy)
        assertEquals(emptyList<Client>(), RemindLogic.clientsNeedingReminder(clients, listOf(legacyAppt), today))
    }

    @Test
    fun past_appointment_does_not_exclude() {
        val clients = listOf(client("c1", "Ana", "2026-05-01"))
        val pastAppt = appt("c1", "Ana", "2026-06-01") // pasada
        assertEquals(listOf("c1"), RemindLogic.clientsNeedingReminder(clients, listOf(pastAppt), today).map { it.id })
    }
}
