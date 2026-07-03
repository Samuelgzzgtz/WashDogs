package com.washdog.app

import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.util.ClientLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ClientLinkTest {

    private fun client(id: String, name: String) =
        Client(id = id, ownerName = name, petName = "P", breed = "", lastVisit = "",
            needsReschedule = false, petType = PetType.DOG)

    private fun appt(id: String, clientId: String, clientName: String) =
        Appointment(id = id, clientId = clientId, clientName = clientName, petName = "P",
            service = "s", time = "10:00", date = "2026-01-01", status = ApptStatus.SCHEDULED)

    @Test
    fun backfills_missing_client_id_by_name() {
        val clients = listOf(client("c1", "Ana"), client("c2", "Luis"))
        val appts = listOf(appt("a1", "", "Ana"), appt("a2", "", "Luis"))
        val result = ClientLink.backfill(appts, clients)
        assertEquals("c1", result[0].clientId)
        assertEquals("c2", result[1].clientId)
    }

    @Test
    fun keeps_existing_client_id() {
        val clients = listOf(client("c1", "Ana"))
        val appts = listOf(appt("a1", "already", "Ana"))
        assertEquals("already", ClientLink.backfill(appts, clients).single().clientId)
    }

    @Test
    fun leaves_unmatched_name_blank() {
        val clients = listOf(client("c1", "Ana"))
        val appts = listOf(appt("a1", "", "Desconocido"))
        assertEquals("", ClientLink.backfill(appts, clients).single().clientId)
    }

    @Test
    fun returns_same_list_when_nothing_to_backfill() {
        val clients = listOf(client("c1", "Ana"))
        val appts = listOf(appt("a1", "c1", "Ana"))
        assertSame(appts, ClientLink.backfill(appts, clients))
    }
}
