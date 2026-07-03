package com.washdog.app.data

import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientRow(
    val id: String,
    @SerialName("user_id")          val userId: String,
    @SerialName("owner_name")       val ownerName: String,
    @SerialName("pet_name")         val petName: String,
    val breed: String,
    @SerialName("last_visit")       val lastVisit: String,
    @SerialName("needs_reschedule") val needsReschedule: Boolean,
    @SerialName("pet_type")         val petType: String,
    val phone: String = "",
    @SerialName("updated_at")       val updatedAt: String = ""
)

@Serializable
data class AppointmentRow(
    val id: String,
    @SerialName("user_id")           val userId: String,
    @SerialName("client_id")         val clientId: String = "",
    @SerialName("client_name")       val clientName: String,
    @SerialName("pet_name")          val petName: String,
    val service: String,
    val time: String,
    @SerialName("end_time")          val endTime: String = "",
    val date: String,
    val status: String,
    @SerialName("repeat_every_days") val repeatEveryDays: Int? = null,
    @SerialName("series_id")         val seriesId: String? = null,
    @SerialName("updated_at")        val updatedAt: String = ""
)

@Serializable
data class TransactionRow(
    val id: String,
    @SerialName("user_id")   val userId: String,
    val type: String,
    val amount: Double,
    val concept: String,
    val detail: String,
    // La columna en Supabase es `date` (no `date_time`); el desajuste rompía el sync
    // de transacciones en silencio. El modelo local sigue llamándose dateTime.
    @SerialName("date") val dateTime: String,
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class UserSettingsRow(
    @SerialName("user_id")        val userId: String,
    @SerialName("tpl_welcome")    val tplWelcome: String,
    @SerialName("tpl_confirm")    val tplConfirm: String,
    @SerialName("tpl_reschedule") val tplReschedule: String,
    @SerialName("updated_at")     val updatedAt: String = ""
)

/** Registro de borrado sincronizado (tabla `tombstones`). */
@Serializable
data class TombstoneRow(
    @SerialName("user_id")    val userId: String,
    val entity: String,
    @SerialName("entity_id")  val entityId: String,
    @SerialName("deleted_at") val deletedAt: String
)

// ── Mappers ──────────────────────────────────────────────────────────────────

fun Client.toRow(userId: String) = ClientRow(
    id = id, userId = userId, ownerName = ownerName, petName = petName,
    breed = breed, lastVisit = lastVisit, needsReschedule = needsReschedule,
    petType = petType.name, phone = phone, updatedAt = updatedAt
)

fun ClientRow.toClient() = Client(
    id = id, ownerName = ownerName, petName = petName, breed = breed,
    lastVisit = lastVisit, needsReschedule = needsReschedule,
    petType = PetType.valueOf(petType), phone = phone, updatedAt = updatedAt
)

fun Appointment.toRow(userId: String) = AppointmentRow(
    id = id, userId = userId, clientId = clientId, clientName = clientName, petName = petName,
    service = service, time = time, endTime = endTime, date = date,
    status = status.name, repeatEveryDays = repeatEveryDays, seriesId = seriesId,
    updatedAt = updatedAt
)

fun AppointmentRow.toAppointment() = Appointment(
    id = id, clientId = clientId, clientName = clientName, petName = petName, service = service,
    time = time, endTime = endTime, date = date,
    status = ApptStatus.valueOf(status),
    repeatEveryDays = repeatEveryDays, seriesId = seriesId, updatedAt = updatedAt
)

fun Transaction.toRow(userId: String) = TransactionRow(
    id = id, userId = userId, type = type.name,
    amount = amount, concept = concept, detail = detail, dateTime = dateTime,
    updatedAt = updatedAt
)

fun TransactionRow.toTransaction() = Transaction(
    id = id, type = TxType.valueOf(type), amount = amount,
    concept = concept, detail = detail, dateTime = dateTime, updatedAt = updatedAt
)
