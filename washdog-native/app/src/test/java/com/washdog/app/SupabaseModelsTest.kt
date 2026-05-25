package com.washdog.app

import com.washdog.app.data.AppointmentRow
import com.washdog.app.data.ClientRow
import com.washdog.app.data.TransactionRow
import com.washdog.app.data.toAppointment
import com.washdog.app.data.toClient
import com.washdog.app.data.toRow
import com.washdog.app.data.toTransaction
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseModelsTest {

    @Test
    fun `Client toRow then toClient roundtrip`() {
        val client = Client("1", "Ana López", "Mochi", "Poodle", "2026-05-01",
            false, PetType.DOG, "+525512345678")
        val row = client.toRow("user-abc")
        assertEquals("1", row.id)
        assertEquals("user-abc", row.userId)
        assertEquals("DOG", row.petType)
        val restored = row.toClient()
        assertEquals(client, restored)
    }

    @Test
    fun `Appointment toRow then toAppointment roundtrip`() {
        val appt = Appointment("2", "Ana López", "Mochi", "Baño+Corte",
            "10:00", "2026-05-25", ApptStatus.SCHEDULED, "11:00")
        val row = appt.toRow("user-abc")
        assertEquals("SCHEDULED", row.status)
        assertEquals("11:00", row.endTime)
        val restored = row.toAppointment()
        assertEquals(appt, restored)
    }

    @Test
    fun `Transaction toRow then toTransaction roundtrip`() {
        val tx = Transaction("3", TxType.IN, 450.0, "Baño+Corte", "Luna", "2026-05-25T10:20")
        val row = tx.toRow("user-abc")
        assertEquals("IN", row.type)
        val restored = row.toTransaction()
        assertEquals(tx, restored)
    }
}
