package com.washdog.app.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.washdog.app.data.AppRepository
import com.washdog.app.data.models.Appointment
import com.washdog.app.util.OverlapChecker
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import com.washdog.app.data.models.BusinessHours
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.data.models.ZonePrices
import com.washdog.app.util.ProximityEngine
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository(application)

    val transactions  = mutableStateListOf<Transaction>()
    val clients       = mutableStateListOf<Client>()
    val appointments  = mutableStateListOf<Appointment>()

    var tplWelcome    by mutableStateOf(AppRepository.DEFAULT_WELCOME)
        private set
    var tplConfirm    by mutableStateOf(AppRepository.DEFAULT_CONFIRM)
        private set
    var tplReschedule by mutableStateOf(AppRepository.DEFAULT_RESCHEDULE)
        private set

    var overlapConflict by mutableStateOf<Appointment?>(null)
        private set
    var businessHours by mutableStateOf(BusinessHours())
        private set
    var zonePrices by mutableStateOf(ZonePrices())
        private set

    init { viewModelScope.launch { loadData() } }

    // Runs once: reads old DataStore data and uploads it to Supabase.
    // Returns immediately if DataStore is empty or Supabase already has data.
    private suspend fun migrateFromDataStore() {
        val oldStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { getApplication<Application>().preferencesDataStoreFile("washdog_prefs") }
        )
        val prefs = try { oldStore.data.first() } catch (e: Exception) { return }

        val rawClients = prefs[stringPreferencesKey("clients")]
        if (rawClients.isNullOrBlank()) return

        if (repo.loadClients().isNotEmpty()) {
            repo.markInitialized()
            return
        }

        val gson = Gson()
        val oldClients: List<Client>     = try { gson.fromJson(rawClients,
            object : TypeToken<List<Client>>() {}.type) } catch (e: Exception) { emptyList() }
        val oldAppts: List<Appointment>  = try { gson.fromJson(
            prefs[stringPreferencesKey("appointments")] ?: "",
            object : TypeToken<List<Appointment>>() {}.type) } catch (e: Exception) { emptyList() }
        val oldTx: List<Transaction>     = try { gson.fromJson(
            prefs[stringPreferencesKey("transactions")] ?: "",
            object : TypeToken<List<Transaction>>() {}.type) } catch (e: Exception) { emptyList() }

        if (oldClients.isNotEmpty()) repo.saveClients(oldClients)
        if (oldAppts.isNotEmpty())   repo.saveAppointments(oldAppts)
        if (oldTx.isNotEmpty())      repo.saveTransactions(oldTx)

        repo.markInitialized()
    }

    private suspend fun loadData() {
        if (!repo.isInitialized()) migrateFromDataStore()
        val initialized = repo.isInitialized()
        val savedTx     = repo.loadTransactions()
        val savedCl     = repo.loadClients()
        val savedAppt   = repo.loadAppointments()
        val (w, c, r)   = repo.loadTemplates()
        val savedBh     = repo.loadBusinessHours()
        val savedZp     = repo.loadZonePrices()

        if (!initialized) {
            loadSeedTransactions()
            loadSeedClients()
            loadSeedAppointments()
            repo.markInitialized()
        } else {
            transactions.addAll(savedTx)
            clients.addAll(savedCl)
            appointments.addAll(savedAppt)
        }

        tplWelcome    = w
        tplConfirm    = c
        tplReschedule = r
        businessHours = savedBh
        zonePrices    = savedZp
    }

    // repo.save*() ya no es suspend — lanza en su propio saveScope (no en viewModelScope)

    fun addTransaction(t: Transaction) {
        transactions.add(0, t)
        repo.saveTransactions(transactions.toList())
    }

    fun deleteTransaction(transactionId: String) {
        transactions.removeAll { it.id == transactionId }
        repo.saveTransactions(transactions.toList())
    }

    fun addClient(c: Client) {
        val withZone = c.copy(zone = ProximityEngine.detectZone(c.address))
        clients.add(withZone)
        repo.saveClients(clients.toList())
    }

    fun updateClient(updated: Client) {
        val withZone = updated.copy(zone = ProximityEngine.detectZone(updated.address))
        val idx = clients.indexOfFirst { it.id == withZone.id }
        if (idx >= 0) {
            clients[idx] = withZone
            repo.saveClients(clients.toList())
        }
    }

    fun deleteClient(clientId: String) {
        val name = clients.firstOrNull { it.id == clientId }?.ownerName
        clients.removeAll { it.id == clientId }
        if (name != null) appointments.removeAll { it.clientName == name }
        repo.saveClients(clients.toList())
        repo.saveAppointments(appointments.toList())
    }

    fun addAppointment(a: Appointment) {
        appointments.add(a)
        repo.saveAppointments(appointments.toList())
    }

    fun updateAppointment(updated: Appointment) {
        val idx = appointments.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            appointments[idx] = updated
            repo.saveAppointments(appointments.toList())
        }
    }

    fun deleteAppointment(appointmentId: String) {
        appointments.removeAll { it.id == appointmentId }
        repo.saveAppointments(appointments.toList())
    }

    fun updateTemplates(welcome: String, confirm: String, reschedule: String) {
        tplWelcome    = welcome
        tplConfirm    = confirm
        tplReschedule = reschedule
        repo.saveTemplates(welcome, confirm, reschedule)
    }

    fun updateBusinessHours(bh: BusinessHours) {
        businessHours = bh
        repo.saveBusinessHours(bh)
    }

    fun updateZonePrices(zp: ZonePrices) {
        zonePrices = zp
        repo.saveZonePrices(zp)
    }

    fun checkOverlap(candidate: Appointment) {
        overlapConflict = if (candidate.time.isBlank()) null
                          else OverlapChecker.findConflict(candidate, appointments)
    }

    fun isWithinBusinessHours(time: String, date: String): Boolean {
        return try {
            val dow = LocalDate.parse(date).dayOfWeek.value % 7   // 0=Dom
            if (dow in businessHours.closedDays) return false
            time >= businessHours.openTime && time <= businessHours.closeTime
        } catch (e: Exception) { true }
    }

    // ── Computed ─────────────────────────────────────────────────────────────

    val totalIncome:  Double get() = transactions.filter { it.type == TxType.IN  }.sumOf { it.amount }
    val totalExpense: Double get() = transactions.filter { it.type == TxType.OUT }.sumOf { it.amount }
    val netBalance:   Double get() = totalIncome - totalExpense

    val clientsToRemind: List<Client>
        get() = clients.filter { daysSince(it.lastVisit) >= 30 }

    internal fun daysSince(dateStr: String): Long {
        return try {
            val date = LocalDate.parse(dateStr)
            LocalDate.now().toEpochDay() - date.toEpochDay()
        } catch (e: Exception) { 0L }
    }

    // ── Seed data (primer arranque) ───────────────────────────────────────────

    private fun loadSeedTransactions() {
        transactions.addAll(listOf(
            Transaction("1", TxType.IN,  450.0, "Baño+Corte", "Luna · Carla M.",    LocalDate.now().minusDays(1).toString() + "T10:20"),
            Transaction("2", TxType.IN,  150.0, "Corte uñas", "Firulais · Luis H.", LocalDate.now().minusDays(1).toString() + "T09:00"),
            Transaction("3", TxType.OUT, 380.0, "Insumos",    "Shampoo 2L",         LocalDate.now().minusDays(2).toString() + "T15:00"),
            Transaction("4", TxType.IN,  550.0, "Baño gato",  "Niebla · Ana V.",    LocalDate.now().minusDays(2).toString() + "T11:00"),
        ))
        repo.saveTransactions(transactions.toList())
    }

    private fun loadSeedClients() {
        clients.addAll(listOf(
            Client("1", "Camila Rivas",   "Mochi",    "Poodle toy", LocalDate.now().minusDays(35).toString(), true,  PetType.DOG, "+525512345678"),
            Client("2", "Carla Mendoza",  "Luna",     "Poodle",     LocalDate.now().minusDays(1).toString(),  false, PetType.DOG, "+525587654321"),
            Client("3", "Ana Valdés",     "Niebla",   "Persa",      LocalDate.now().minusDays(2).toString(),  false, PetType.CAT, "+525511223344"),
            Client("4", "Luis Hernández", "Firulais", "Schnauzer",  LocalDate.now().minusDays(1).toString(),  false, PetType.DOG, "+525544332211"),
        ))
        repo.saveClients(clients.toList())
    }

    private fun loadSeedAppointments() {
        appointments.addAll(listOf(
            Appointment(id = "1", clientName = "Carla Mendoza",  petName = "Luna",     service = "Baño+Corte", time = "10:20", date = LocalDate.now().minusDays(1).toString(), status = ApptStatus.DONE),
            Appointment(id = "2", clientName = "Luis Hernández", petName = "Firulais", service = "Corte uñas", time = "09:00", date = LocalDate.now().minusDays(1).toString(), status = ApptStatus.DONE),
            Appointment(id = "3", clientName = "Camila Rivas",   petName = "Mochi",    service = "Baño+Corte", time = "14:00", date = LocalDate.now().plusDays(2).toString(),  status = ApptStatus.NEXT),
            Appointment(id = "4", clientName = "Ana Valdés",     petName = "Niebla",   service = "Baño gato",  time = "16:00", date = LocalDate.now().plusDays(3).toString(),  status = ApptStatus.SCHEDULED),
        ))
        repo.saveAppointments(appointments.toList())
    }
}
