package com.washdog.app.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.washdog.app.data.AppRepository
import com.washdog.app.data.AuthRepository
import com.washdog.app.data.models.Appointment
import com.washdog.app.util.OverlapChecker
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import java.time.LocalDate
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo     = AppRepository(application)
    private val authRepo = AuthRepository()

    val transactions  = mutableStateListOf<Transaction>()
    val clients       = mutableStateListOf<Client>()
    val appointments  = mutableStateListOf<Appointment>()

    var tplWelcome    by mutableStateOf(AppRepository.DEFAULT_WELCOME);    private set
    var tplConfirm    by mutableStateOf(AppRepository.DEFAULT_CONFIRM);    private set
    var tplReschedule by mutableStateOf(AppRepository.DEFAULT_RESCHEDULE); private set

    var overlapConflict by mutableStateOf<Appointment?>(null); private set

    var isLoggedIn     by mutableStateOf(false); private set
    var isLoggingIn    by mutableStateOf(false); private set
    var sessionLoading by mutableStateOf(true);  private set

    init {
        // B1: la sesión persistida se carga de disco de forma asíncrona; esperamos a
        // que supabase-kt termine antes de decidir la pantalla, en vez de leerla
        // síncronamente (que devolvía null por la carrera y forzaba re-login siempre).
        viewModelScope.launch {
            try { authRepo.awaitInitialization() } catch (_: Exception) {}
            isLoggedIn = authRepo.hasActiveSession()
            if (isLoggedIn) loadData()
            sessionLoading = false
        }
    }

    // ── AUTH ──────────────────────────────────────────────────────────────────

    fun login(email: String, password: String, onError: (String) -> Unit) {
        isLoggingIn = true
        viewModelScope.launch {
            try {
                authRepo.login(email, password)
                loadData()
                isLoggedIn = true
            } catch (e: Exception) {
                onError(e.message ?: "Error al iniciar sesión")
            } finally {
                isLoggingIn = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { authRepo.logout() } catch (_: Exception) {}
            transactions.clear()
            clients.clear()
            appointments.clear()
            isLoggedIn = false
        }
    }

    // ── LOAD DATA ─────────────────────────────────────────────────────────────

    private suspend fun loadData() {
        // 0) Migración única: si venimos de una versión con caché local sin namespace,
        //    asignar esos datos al usuario actual antes de leer (cero pérdida; evita que
        //    los datos de una cuenta contaminen otra en el mismo dispositivo).
        repo.migrateLegacyToCurrentUserIfNeeded()

        // 1) Local primero: es la fuente de verdad de lo que el usuario capturó en este
        //    dispositivo (incluye registros que aún no se sincronizaron a la nube).
        val localTx   = repo.loadTransactions()
        val localCl   = repo.loadClients()
        val localAppt = repo.loadAppointments()
        val (localW, localC, localR) = repo.loadTemplates()
        val initialized = repo.isInitialized()

        // 2) Nube (puede ser null si no hay red o falla).
        val cloud = try { repo.loadFromCloud() } catch (_: Exception) { null }

        // 3) Merge no destructivo por id (B2): unión local ∪ nube. En conflicto de id
        //    gana lo LOCAL, para no pisar ediciones recién hechas que aún no subieron.
        //    Garantiza cero pérdida de datos del usuario (rule #3).
        val finalCl   = mergeById(localCl,   cloud?.clients      ?: emptyList()) { it.id }
        val finalAppt = mergeById(localAppt, cloud?.appointments ?: emptyList()) { it.id }
        val finalTx   = mergeById(localTx,   cloud?.transactions ?: emptyList()) { it.id }

        // Plantillas: la nube gana si existe, si no, lo local.
        cloud?.settings?.let {
            tplWelcome = it.tplWelcome; tplConfirm = it.tplConfirm; tplReschedule = it.tplReschedule
        } ?: run { tplWelcome = localW; tplConfirm = localC; tplReschedule = localR }

        val everythingEmpty = finalCl.isEmpty() && finalAppt.isEmpty() && finalTx.isEmpty()

        if (everythingEmpty && !initialized) {
            // Primer arranque real: sembrar demo y subir a la nube.
            loadSeedTransactions(); loadSeedClients(); loadSeedAppointments()
            repo.markInitialized()
            viewModelScope.launch {
                try {
                    repo.syncClientsToCloud(clients.toList())
                    repo.syncAppointmentsToCloud(appointments.toList())
                    repo.syncTransactionsToCloud(transactions.toList())
                } catch (_: Exception) {}
            }
            return
        }

        // 4) Publicar merge en UI y persistir local.
        transactions.clear(); transactions.addAll(finalTx)
        clients.clear();      clients.addAll(finalCl)
        appointments.clear(); appointments.addAll(finalAppt)
        repo.saveTransactions(finalTx)
        repo.saveClients(finalCl)
        repo.saveAppointments(finalAppt)
        repo.saveTemplates(tplWelcome, tplConfirm, tplReschedule)
        repo.markInitialized()

        // 5) Subir el merge a la nube: los registros que estaban SOLO en local se
        //    sincronizan (antes se descartaban silenciosamente al pisar con la nube).
        if (cloud != null) {
            viewModelScope.launch {
                try {
                    repo.syncClientsToCloud(finalCl)
                    repo.syncAppointmentsToCloud(finalAppt)
                    repo.syncTransactionsToCloud(finalTx)
                    repo.syncSettingsToCloud(tplWelcome, tplConfirm, tplReschedule)
                } catch (_: Exception) {}
            }
        }
    }

    /** Unión por id: primero la nube, luego lo local lo sobrescribe (local gana en conflicto). */
    private inline fun <T> mergeById(local: List<T>, cloud: List<T>, id: (T) -> String): List<T> {
        val byId = LinkedHashMap<String, T>()
        cloud.forEach { byId[id(it)] = it }
        local.forEach { byId[id(it)] = it }
        return byId.values.toList()
    }

    // ── MUTATIONS + CLOUD SYNC ────────────────────────────────────────────────

    fun addTransaction(t: Transaction) {
        transactions.add(0, t)
        repo.saveTransactions(transactions.toList())
        viewModelScope.launch { try { repo.syncTransactionsToCloud(transactions.toList()) } catch (_: Exception) {} }
    }

    fun deleteTransaction(transactionId: String) {
        transactions.removeAll { it.id == transactionId }
        repo.saveTransactions(transactions.toList())
        viewModelScope.launch { try { repo.deleteTransactionFromCloud(transactionId) } catch (_: Exception) {} }
    }

    fun addClient(c: Client) {
        clients.add(c)
        repo.saveClients(clients.toList())
        viewModelScope.launch { try { repo.syncClientsToCloud(clients.toList()) } catch (_: Exception) {} }
    }

    fun updateClient(updated: Client) {
        val idx = clients.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            clients[idx] = updated
            repo.saveClients(clients.toList())
            viewModelScope.launch { try { repo.syncClientsToCloud(clients.toList()) } catch (_: Exception) {} }
        }
    }

    fun deleteClient(clientId: String) {
        val name = clients.firstOrNull { it.id == clientId }?.ownerName
        clients.removeAll { it.id == clientId }
        if (name != null) appointments.removeAll { it.clientName == name }
        repo.saveClients(clients.toList())
        repo.saveAppointments(appointments.toList())
        viewModelScope.launch {
            try {
                repo.deleteClientFromCloud(clientId)
                if (name != null) repo.syncAppointmentsToCloud(appointments.toList())
            } catch (_: Exception) {}
        }
    }

    fun addAppointment(a: Appointment) {
        appointments.add(a)
        repo.saveAppointments(appointments.toList())
        viewModelScope.launch { try { repo.syncAppointmentsToCloud(appointments.toList()) } catch (_: Exception) {} }
    }

    fun updateAppointment(updated: Appointment) {
        val idx = appointments.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            appointments[idx] = updated
            repo.saveAppointments(appointments.toList())
            viewModelScope.launch { try { repo.syncAppointmentsToCloud(appointments.toList()) } catch (_: Exception) {} }
        }
    }

    fun deleteAppointment(appointmentId: String) {
        appointments.removeAll { it.id == appointmentId }
        repo.saveAppointments(appointments.toList())
        viewModelScope.launch { try { repo.deleteAppointmentFromCloud(appointmentId) } catch (_: Exception) {} }
    }

    fun updateTemplates(welcome: String, confirm: String, reschedule: String) {
        tplWelcome = welcome; tplConfirm = confirm; tplReschedule = reschedule
        repo.saveTemplates(welcome, confirm, reschedule)
        viewModelScope.launch { try { repo.syncSettingsToCloud(welcome, confirm, reschedule) } catch (_: Exception) {} }
    }

    fun checkOverlap(candidate: Appointment) {
        overlapConflict = if (candidate.time.isBlank()) null
                          else OverlapChecker.findConflict(candidate, appointments)
    }

    // ── COMPUTED ──────────────────────────────────────────────────────────────

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

    // ── SEED DATA ─────────────────────────────────────────────────────────────

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
