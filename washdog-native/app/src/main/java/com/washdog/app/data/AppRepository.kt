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

    private val KEY_TX             = stringPreferencesKey("transactions")
    private val KEY_CL             = stringPreferencesKey("clients")
    private val KEY_APPT           = stringPreferencesKey("appointments")
    private val KEY_TPL_WELCOME    = stringPreferencesKey("tpl_welcome")
    private val KEY_TPL_CONFIRM    = stringPreferencesKey("tpl_confirm")
    private val KEY_TPL_RESCHEDULE = stringPreferencesKey("tpl_reschedule")
    private val KEY_INITIALIZED    = booleanPreferencesKey("initialized")

    companion object {
        const val DEFAULT_WELCOME    = "Hola {{nombre}}, bienvenido/a. Estamos felices de tenerte con nosotros. 😊"
        const val DEFAULT_CONFIRM    = "Hola {{nombre}}, confirmamos tu cita para el {{fecha}} a las {{hora}}. ¡Te esperamos!"
        const val DEFAULT_RESCHEDULE = "Hola {{nombre}}, queremos coordinar una nueva fecha para tu cita. ¿Cuándo te queda bien?"
    }

    // ── LOCAL SAVES ───────────────────────────────────────────────────────────

    fun saveTransactions(list: List<Transaction>) {
        saveScope.launch { context.dataStore.edit { it[KEY_TX] = gson.toJson(list) } }
    }

    fun saveClients(list: List<Client>) {
        saveScope.launch { context.dataStore.edit { it[KEY_CL] = gson.toJson(list) } }
    }

    fun saveAppointments(list: List<Appointment>) {
        saveScope.launch { context.dataStore.edit { it[KEY_APPT] = gson.toJson(list) } }
    }

    fun saveTemplates(welcome: String, confirm: String, reschedule: String) {
        saveScope.launch {
            context.dataStore.edit { prefs ->
                prefs[KEY_TPL_WELCOME]    = welcome
                prefs[KEY_TPL_CONFIRM]    = confirm
                prefs[KEY_TPL_RESCHEDULE] = reschedule
            }
        }
    }

    fun markInitialized() {
        saveScope.launch { context.dataStore.edit { it[KEY_INITIALIZED] = true } }
    }

    // ── LOCAL LOADS ───────────────────────────────────────────────────────────

    suspend fun isInitialized(): Boolean {
        return try { context.dataStore.data.first()[KEY_INITIALIZED] == true }
        catch (e: Exception) { false }
    }

    suspend fun loadTransactions(): List<Transaction> {
        return try {
            val json = context.dataStore.data.first()[KEY_TX] ?: return emptyList()
            gson.fromJson(json, object : TypeToken<List<Transaction>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadClients(): List<Client> {
        return try {
            val json = context.dataStore.data.first()[KEY_CL] ?: return emptyList()
            gson.fromJson(json, object : TypeToken<List<Client>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadAppointments(): List<Appointment> {
        return try {
            val json = context.dataStore.data.first()[KEY_APPT] ?: return emptyList()
            gson.fromJson(json, object : TypeToken<List<Appointment>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadTemplates(): Triple<String, String, String> {
        return try {
            val prefs = context.dataStore.data.first()
            Triple(
                prefs[KEY_TPL_WELCOME]    ?: DEFAULT_WELCOME,
                prefs[KEY_TPL_CONFIRM]    ?: DEFAULT_CONFIRM,
                prefs[KEY_TPL_RESCHEDULE] ?: DEFAULT_RESCHEDULE
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
