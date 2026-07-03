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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val appt = Appointment(
            id = "2", clientId = "c1", clientName = "Ana López", petName = "Mochi",
            service = "Baño+Corte", time = "10:00", date = "2026-05-25",
            status = ApptStatus.SCHEDULED, endTime = "11:00"
        )
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

    // Regresión v1.3.2: la columna de la fecha en Supabase es `date`, no `date_time`.
    // Un desajuste en el @SerialName rompía el sync de transacciones en silencio.
    @Test
    fun `TransactionRow serializes date field with column name date`() {
        val row = Transaction("3", TxType.IN, 450.0, "c", "d", "2026-05-25T10:20").toRow("u")
        val json = Json.encodeToString(row)
        assertTrue("Debe usar la clave 'date'", json.contains("\"date\":"))
        assertTrue("No debe usar 'date_time'", !json.contains("date_time"))
    }
}
