package com.washdog.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.Transaction
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "washdog_prefs")

class AppRepository(private val context: Context) {

    private val gson      = Gson()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Claves de negocio particionadas por usuario (washdog_prefs es compartido por
    // todas las cuentas del dispositivo; el namespace por uid las aísla).
    private fun keyTx(u: String?)   = stringPreferencesKey(LocalKeys.scoped("transactions", u))
    private fun keyCl(u: String?)   = stringPreferencesKey(LocalKeys.scoped("clients", u))
    private fun keyAppt(u: String?) = stringPreferencesKey(LocalKeys.scoped("appointments", u))
    private fun keyW(u: String?)    = stringPreferencesKey(LocalKeys.scoped("tpl_welcome", u))
    private fun keyC(u: String?)    = stringPreferencesKey(LocalKeys.scoped("tpl_confirm", u))
    private fun keyR(u: String?)    = stringPreferencesKey(LocalKeys.scoped("tpl_reschedule", u))
    private fun keyInit(u: String?) = booleanPreferencesKey(LocalKeys.scoped("initialized", u))

    // Claves legacy (sin namespace, pre v1.1.x) — solo para la migración única.
    private val KEY_LEGACY_MIGRATED = booleanPreferencesKey("legacy_migrated")

    companion object {
        const val DEFAULT_WELCOME    = "Hola {{nombre}}, bienvenido/a. Estamos felices de tenerte con nosotros. 😊"
        const val DEFAULT_CONFIRM    = "Hola {{nombre}}, confirmamos tu cita para el {{fecha}} a las {{hora}}. ¡Te esperamos!"
        const val DEFAULT_RESCHEDULE = "Hola {{nombre}}, queremos coordinar una nueva fecha para tu cita. ¿Cuándo te queda bien?"

        // Bases de string que se migran de legacy → namespace del usuario.
        private val LEGACY_STRING_BASES = listOf(
            "transactions", "clients", "appointments",
            "tpl_welcome", "tpl_confirm", "tpl_reschedule"
        )
        private const val BASE_INITIALIZED = "initialized"
    }

    // ── LEGACY MIGRATION ──────────────────────────────────────────────────────

    /**
     * Migración única: asigna los datos locales legacy (sin namespace) al usuario
     * logueado actual —dueño de esos datos antes de la actualización— y borra las
     * claves legacy. Idempotente vía flag global [KEY_LEGACY_MIGRATED]. Garantiza
     * cero pérdida de datos al actualizar a la caché por usuario (rule #3).
     */
    suspend fun migrateLegacyToCurrentUserIfNeeded() {
        val u = userId() ?: return
        try {
            context.dataStore.edit { p ->
                if (p[KEY_LEGACY_MIGRATED] == true) return@edit

                val legacyPresent = LEGACY_STRING_BASES
                    .filter { p[stringPreferencesKey(it)] != null }.toSet()
                val scopedPresent = LEGACY_STRING_BASES
                    .filter { p[stringPreferencesKey(LocalKeys.scoped(it, u))] != null }.toSet()
                val toCopy = LocalKeys.basesToCopy(LEGACY_STRING_BASES, legacyPresent, scopedPresent).toSet()

                for (base in LEGACY_STRING_BASES) {
                    val legacyKey = stringPreferencesKey(base)
                    if (base in toCopy) {
                        p[stringPreferencesKey(LocalKeys.scoped(base, u))] = p[legacyKey]!!
                    }
                    p.remove(legacyKey)
                }

                val legacyInit = booleanPreferencesKey(BASE_INITIALIZED)
                val scopedInit = booleanPreferencesKey(LocalKeys.scoped(BASE_INITIALIZED, u))
                p[legacyInit]?.let { if (p[scopedInit] == null) p[scopedInit] = it }
                p.remove(legacyInit)

                p[KEY_LEGACY_MIGRATED] = true
            }
        } catch (e: Exception) { /* best-effort; si falla, se reintenta el próximo arranque */ }
    }

    // ── LOCAL SAVES ───────────────────────────────────────────────────────────

    fun saveTransactions(list: List<Transaction>) {
        val u = userId()
        saveScope.launch { context.dataStore.edit { it[keyTx(u)] = gson.toJson(list) } }
    }

    fun saveClients(list: List<Client>) {
        val u = userId()
        saveScope.launch { context.dataStore.edit { it[keyCl(u)] = gson.toJson(list) } }
    }

    fun saveAppointments(list: List<Appointment>) {
        val u = userId()
        saveScope.launch { context.dataStore.edit { it[keyAppt(u)] = gson.toJson(list) } }
    }

    fun saveTemplates(welcome: String, confirm: String, reschedule: String) {
        val u = userId()
        saveScope.launch {
            context.dataStore.edit { prefs ->
                prefs[keyW(u)] = welcome
                prefs[keyC(u)] = confirm
                prefs[keyR(u)] = reschedule
            }
        }
    }

    fun markInitialized() {
        val u = userId()
        saveScope.launch { context.dataStore.edit { it[keyInit(u)] = true } }
    }

    // ── LOCAL LOADS ───────────────────────────────────────────────────────────

    suspend fun isInitialized(): Boolean {
        return try { context.dataStore.data.first()[keyInit(userId())] == true }
        catch (e: Exception) { false }
    }

    suspend fun loadTransactions(): List<Transaction> {
        return try {
            val json = context.dataStore.data.first()[keyTx(userId())] ?: return emptyList()
            gson.fromJson(json, object : TypeToken<List<Transaction>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadClients(): List<Client> {
        return try {
            val json = context.dataStore.data.first()[keyCl(userId())] ?: return emptyList()
            gson.fromJson(json, object : TypeToken<List<Client>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadAppointments(): List<Appointment> {
        return try {
            val json = context.dataStore.data.first()[keyAppt(userId())] ?: return emptyList()
            gson.fromJson(json, object : TypeToken<List<Appointment>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadTemplates(): Triple<String, String, String> {
        return try {
            val u = userId()
            val prefs = context.dataStore.data.first()
            Triple(
                prefs[keyW(u)] ?: DEFAULT_WELCOME,
                prefs[keyC(u)] ?: DEFAULT_CONFIRM,
                prefs[keyR(u)] ?: DEFAULT_RESCHEDULE
            )
        } catch (e: Exception) { Triple(DEFAULT_WELCOME, DEFAULT_CONFIRM, DEFAULT_RESCHEDULE) }
    }

    // ── CLOUD SYNC ────────────────────────────────────────────────────────────

    private fun userId(): String? =
        WashdogSupabase.client.auth.currentUserOrNull()?.id

    suspend fun syncClientsToCloud(clients: List<Client>) {
        val uid = userId() ?: return
        if (clients.isEmpty()) return
        WashdogSupabase.client.from("clients")
            .upsert(clients.map { it.toRow(uid) }) { onConflict = "id" }
    }

    suspend fun syncAppointmentsToCloud(appointments: List<Appointment>) {
        val uid = userId() ?: return
        if (appointments.isEmpty()) return
        WashdogSupabase.client.from("appointments")
            .upsert(appointments.map { it.toRow(uid) }) { onConflict = "id" }
    }

    suspend fun syncTransactionsToCloud(transactions: List<Transaction>) {
        val uid = userId() ?: return
        if (transactions.isEmpty()) return
        WashdogSupabase.client.from("transactions")
            .upsert(transactions.map { it.toRow(uid) }) { onConflict = "id" }
    }

    suspend fun syncSettingsToCloud(welcome: String, confirm: String, reschedule: String) {
        val uid = userId() ?: return
        WashdogSupabase.client.from("user_settings")
            .upsert(UserSettingsRow(uid, welcome, confirm, reschedule)) { onConflict = "user_id" }
    }

    suspend fun deleteClientFromCloud(id: String) {
        userId() ?: return
        WashdogSupabase.client.from("clients").delete { filter { eq("id", id) } }
    }

    suspend fun deleteAppointmentFromCloud(id: String) {
        userId() ?: return
        WashdogSupabase.client.from("appointments").delete { filter { eq("id", id) } }
    }

    suspend fun deleteTransactionFromCloud(id: String) {
        userId() ?: return
        WashdogSupabase.client.from("transactions").delete { filter { eq("id", id) } }
    }

    suspend fun loadFromCloud(): CloudSnapshot? {
        return try {
            val clients = WashdogSupabase.client.from("clients")
                .select().decodeList<ClientRow>().map { it.toClient() }
            val appointments = WashdogSupabase.client.from("appointments")
                .select().decodeList<AppointmentRow>().map { it.toAppointment() }
            val transactions = WashdogSupabase.client.from("transactions")
                .select().decodeList<TransactionRow>().map { it.toTransaction() }
            val settings = WashdogSupabase.client.from("user_settings")
                .select().decodeList<UserSettingsRow>().firstOrNull()
            CloudSnapshot(clients, appointments, transactions, settings)
        } catch (e: Exception) { null }
    }
}

data class CloudSnapshot(
    val clients:      List<Client>,
    val appointments: List<Appointment>,
    val transactions: List<Transaction>,
    val settings:     UserSettingsRow?
)
