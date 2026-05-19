package com.washdog.app.data

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.washdog.app.BuildConfig
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.BusinessHours
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.ServiceLocation
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import com.washdog.app.data.models.ZonePrices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val db = SupabaseDataSource(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var knownClientIds      = mutableSetOf<String>()
    private var knownAppointmentIds = mutableSetOf<String>()
    private var knownTransactionIds = mutableSetOf<String>()

    companion object {
        const val DEFAULT_WELCOME    = "Hola {{nombre}}, bienvenido/a. Estamos felices de tenerte con nosotros. 😊"
        const val DEFAULT_CONFIRM    = "Hola {{nombre}}, confirmamos tu cita para el {{fecha}} a las {{hora}}. ¡Te esperamos!"
        const val DEFAULT_RESCHEDULE = "Hola {{nombre}}, queremos coordinar una nueva fecha para tu cita. ¿Cuándo te queda bien?"
    }

    private fun isValidUuid(s: String): Boolean =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(s)

    private fun ensureUuid(id: String): String =
        if (isValidUuid(id)) id else java.util.UUID.randomUUID().toString()

    // ── SAVES (fire-and-forget) ───────────────────────────────────────────────

    fun saveClients(list: List<Client>) {
        saveScope.launch {
            val safeList = list.map { it.copy(id = ensureUuid(it.id)) }
            val newIds = safeList.map { it.id }.toSet()
            (knownClientIds - newIds).forEach { id -> db.delete("clients", "id", id) }
            if (safeList.isNotEmpty()) {
                // Explicit map: lastVisit="" → null (DB column is date type, rejects empty string)
                val rows = safeList.map { c ->
                    mapOf(
                        "id"               to c.id,
                        "owner_name"       to c.ownerName,
                        "pet_name"         to c.petName,
                        "breed"            to c.breed,
                        "phone"            to c.phone,
                        "address"          to c.address,
                        "zone"             to c.zone.name,
                        "pet_type"         to c.petType.name,
                        "last_visit"       to c.lastVisit.ifEmpty { null },
                        "needs_reschedule" to c.needsReschedule,
                    )
                }
                db.upsertList("clients", db.gson.toJson(rows))
            }
            knownClientIds = newIds.toMutableSet()
        }
    }

    fun saveAppointments(list: List<Appointment>) {
        saveScope.launch {
            val safeList = list.map { it.copy(id = ensureUuid(it.id)) }
            val newIds = safeList.map { it.id }.toSet()
            (knownAppointmentIds - newIds).forEach { id -> db.delete("appointments", "id", id) }
            if (safeList.isNotEmpty()) db.upsertList("appointments", db.gson.toJson(safeList))
            knownAppointmentIds = newIds.toMutableSet()
        }
    }

    fun saveTransactions(list: List<Transaction>) {
        saveScope.launch {
            val safeList = list.map { it.copy(id = ensureUuid(it.id)) }
            val newIds = safeList.map { it.id }.toSet()
            (knownTransactionIds - newIds).forEach { id -> db.delete("transactions", "id", id) }
            if (safeList.isNotEmpty()) {
                // Explicit map: exclude `detail` (not in Supabase schema)
                val rows = safeList.map { t ->
                    mapOf(
                        "id"        to t.id,
                        "type"      to t.type.name,
                        "amount"    to t.amount,
                        "concept"   to t.concept,
                        "date_time" to t.dateTime,
                    )
                }
                db.upsertList("transactions", db.gson.toJson(rows))
            }
            knownTransactionIds = newIds.toMutableSet()
        }
    }

    fun saveTemplates(welcome: String, confirm: String, reschedule: String) {
        saveScope.launch {
            val rows = listOf(
                mapOf("key" to "welcome",    "body" to welcome),
                mapOf("key" to "confirm",    "body" to confirm),
                mapOf("key" to "reschedule", "body" to reschedule),
            )
            db.upsertList("templates", db.gson.toJson(rows))
        }
    }

    fun saveBusinessHours(bh: BusinessHours) {
        saveScope.launch {
            val json = "[" + db.gson.toJson(mapOf(
                "id"          to 1,
                "open_time"   to bh.openTime,
                "close_time"  to bh.closeTime,
                "closed_days" to bh.closedDays,
            )) + "]"
            db.upsertList("business_hours", json)
        }
    }

    fun saveZonePrices(zp: ZonePrices) {
        saveScope.launch {
            val json = "[" + db.gson.toJson(mapOf(
                "id"            to 1,
                "near_fee"      to zp.nearFee,    "near_buffer"   to zp.nearBuffer,
                "close_fee"     to zp.closeFee,   "close_buffer"  to zp.closeBuffer,
                "medium_fee"    to zp.mediumFee,  "medium_buffer" to zp.mediumBuffer,
                "far_fee"       to zp.farFee,     "far_buffer"    to zp.farBuffer,
            )) + "]"
            db.upsertList("zone_prices", json)
        }
    }

    fun markInitialized() {
        context.getSharedPreferences("washdog_meta", Context.MODE_PRIVATE)
            .edit().putBoolean("initialized", true).apply()
    }

    suspend fun isInitialized(): Boolean = withContext(Dispatchers.IO) {
        context.getSharedPreferences("washdog_meta", Context.MODE_PRIVATE)
            .getBoolean("initialized", false)
    }

    // ── LOADS (suspend) ───────────────────────────────────────────────────────

    suspend fun loadClients(): List<Client> {
        return try {
            data class ClientRow(
                val id: String,
                val ownerName: String?, val petName: String?,
                val breed: String?, val phone: String?, val address: String?,
                val zone: ServiceZone?, val petType: PetType?,
                val lastVisit: String?, val needsReschedule: Boolean?
            )
            val rows: List<ClientRow> = db.selectAll("clients",
                TypeToken.getParameterized(List::class.java, ClientRow::class.java).type)
            knownClientIds = rows.map { it.id }.toMutableSet()
            rows.map { r ->
                Client(
                    id              = r.id,
                    ownerName       = r.ownerName       ?: "",
                    petName         = r.petName         ?: "",
                    breed           = r.breed           ?: "",
                    phone           = r.phone           ?: "",
                    address         = r.address         ?: "",
                    zone            = r.zone            ?: ServiceZone.FAR,
                    petType         = r.petType         ?: PetType.DOG,
                    lastVisit       = r.lastVisit       ?: "",
                    needsReschedule = r.needsReschedule ?: false,
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadAppointments(): List<Appointment> {
        return try {
            data class ApptRow(
                val id: String,
                val clientName: String?, val petName: String?,
                val service: String?, val time: String?, val date: String?,
                val status: ApptStatus?, val endTime: String?,
                val serviceLocation: ServiceLocation?,
                val travelFee: Double?, val travelBuffer: Int?,
                val durationMinutes: Int?
            )
            val rows: List<ApptRow> = db.selectAll("appointments",
                TypeToken.getParameterized(List::class.java, ApptRow::class.java).type)
            knownAppointmentIds = rows.map { it.id }.toMutableSet()
            rows.map { r ->
                Appointment(
                    id              = r.id,
                    clientName      = r.clientName      ?: "",
                    petName         = r.petName         ?: "",
                    service         = r.service         ?: "",
                    time            = r.time            ?: "",
                    date            = r.date            ?: "",
                    status          = r.status          ?: ApptStatus.SCHEDULED,
                    endTime         = r.endTime         ?: "",
                    serviceLocation = r.serviceLocation ?: ServiceLocation.HOME,
                    travelFee       = r.travelFee       ?: 0.0,
                    travelBuffer    = r.travelBuffer    ?: 0,
                    durationMinutes = r.durationMinutes ?: 60,
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadTransactions(): List<Transaction> {
        return try {
            data class TxRow(
                val id: String, val type: TxType?,
                val amount: Double?, val concept: String?, val dateTime: String?
            )
            val rows: List<TxRow> = db.selectAll("transactions",
                TypeToken.getParameterized(List::class.java, TxRow::class.java).type)
            knownTransactionIds = rows.map { it.id }.toMutableSet()
            rows.map { r ->
                Transaction(
                    id       = r.id,
                    type     = r.type     ?: TxType.IN,
                    amount   = r.amount   ?: 0.0,
                    concept  = r.concept  ?: "",
                    detail   = "",
                    dateTime = r.dateTime ?: "",
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadTemplates(): Triple<String, String, String> {
        return try {
            data class TplRow(val key: String, val body: String)
            val rows: List<TplRow> = db.selectAll("templates",
                TypeToken.getParameterized(List::class.java, TplRow::class.java).type)
            val map = rows.associateBy { it.key }
            Triple(
                map["welcome"]?.body    ?: DEFAULT_WELCOME,
                map["confirm"]?.body    ?: DEFAULT_CONFIRM,
                map["reschedule"]?.body ?: DEFAULT_RESCHEDULE,
            )
        } catch (e: Exception) {
            Triple(DEFAULT_WELCOME, DEFAULT_CONFIRM, DEFAULT_RESCHEDULE)
        }
    }

    suspend fun loadBusinessHours(): BusinessHours {
        return try {
            data class BhRow(val openTime: String?, val closeTime: String?, val closedDays: List<Int>?)
            db.selectSingle("business_hours", "id=eq.1", BhRow::class.java)
                ?.let { BusinessHours(it.openTime ?: "09:00", it.closeTime ?: "19:00", it.closedDays ?: listOf(0)) }
                ?: BusinessHours()
        } catch (e: Exception) { BusinessHours() }
    }

    suspend fun loadZonePrices(): ZonePrices {
        return try {
            data class ZpRow(
                val nearFee: Double?, val nearBuffer: Int?,
                val closeFee: Double?, val closeBuffer: Int?,
                val mediumFee: Double?, val mediumBuffer: Int?,
                val farFee: Double?, val farBuffer: Int?,
            )
            db.selectSingle("zone_prices", "id=eq.1", ZpRow::class.java)
                ?.let { ZonePrices(
                    it.nearFee   ?: 0.0,   it.nearBuffer   ?: 0,
                    it.closeFee  ?: 50.0,  it.closeBuffer  ?: 10,
                    it.mediumFee ?: 90.0,  it.mediumBuffer ?: 25,
                    it.farFee    ?: 120.0, it.farBuffer    ?: 40,
                ) }
                ?: ZonePrices()
        } catch (e: Exception) { ZonePrices() }
    }
}
