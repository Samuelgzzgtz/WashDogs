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
import com.washdog.app.data.models.BusinessHours
import com.washdog.app.data.models.ZonePrices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Top-level: garantiza una sola instancia de DataStore por proceso
private val Context.dataStore by preferencesDataStore(name = "washdog_prefs")

class AppRepository(private val context: Context) {

    private val gson      = Gson()

    // Scope propio, independiente del ViewModel — no se cancela cuando la app se cierra
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val KEY_TX             = stringPreferencesKey("transactions")
    private val KEY_CL             = stringPreferencesKey("clients")
    private val KEY_APPT           = stringPreferencesKey("appointments")
    private val KEY_TPL_WELCOME    = stringPreferencesKey("tpl_welcome")
    private val KEY_TPL_CONFIRM    = stringPreferencesKey("tpl_confirm")
    private val KEY_TPL_RESCHEDULE = stringPreferencesKey("tpl_reschedule")
    private val KEY_INITIALIZED    = booleanPreferencesKey("initialized")
    private val KEY_BUSINESS_HOURS = stringPreferencesKey("business_hours")
    private val KEY_ZONE_PRICES    = stringPreferencesKey("zone_prices")

    companion object {
        const val DEFAULT_WELCOME    = "Hola {{nombre}}, bienvenido/a. Estamos felices de tenerte con nosotros. 😊"
        const val DEFAULT_CONFIRM    = "Hola {{nombre}}, confirmamos tu cita para el {{fecha}} a las {{hora}}. ¡Te esperamos!"
        const val DEFAULT_RESCHEDULE = "Hola {{nombre}}, queremos coordinar una nueva fecha para tu cita. ¿Cuándo te queda bien?"
    }

    // ── SAVES (fire-and-forget en saveScope) ─────────────────────────────────

    fun saveTransactions(list: List<Transaction>) {
        saveScope.launch {
            context.dataStore.edit { it[KEY_TX] = gson.toJson(list) }
        }
    }

    fun saveClients(list: List<Client>) {
        saveScope.launch {
            context.dataStore.edit { it[KEY_CL] = gson.toJson(list) }
        }
    }

    fun saveAppointments(list: List<Appointment>) {
        saveScope.launch {
            context.dataStore.edit { it[KEY_APPT] = gson.toJson(list) }
        }
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
        saveScope.launch {
            context.dataStore.edit { it[KEY_INITIALIZED] = true }
        }
    }

    suspend fun isInitialized(): Boolean {
        return try {
            context.dataStore.data.first()[KEY_INITIALIZED] == true
        } catch (e: Exception) { false }
    }

    // ── LOADS (suspend, solo al arrancar) ────────────────────────────────────

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

    fun saveBusinessHours(bh: BusinessHours) {
        saveScope.launch {
            context.dataStore.edit { it[KEY_BUSINESS_HOURS] = gson.toJson(bh) }
        }
    }

    fun saveZonePrices(zp: ZonePrices) {
        saveScope.launch {
            context.dataStore.edit { it[KEY_ZONE_PRICES] = gson.toJson(zp) }
        }
    }

    suspend fun loadBusinessHours(): BusinessHours {
        return try {
            val json = context.dataStore.data.first()[KEY_BUSINESS_HOURS] ?: return BusinessHours()
            gson.fromJson(json, BusinessHours::class.java)
        } catch (e: Exception) { BusinessHours() }
    }

    suspend fun loadZonePrices(): ZonePrices {
        return try {
            val json = context.dataStore.data.first()[KEY_ZONE_PRICES] ?: return ZonePrices()
            gson.fromJson(json, ZonePrices::class.java)
        } catch (e: Exception) { ZonePrices() }
    }
}
