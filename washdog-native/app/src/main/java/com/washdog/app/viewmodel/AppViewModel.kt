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
import com.washdog.app.data.CredentialStore
import com.washdog.app.data.models.Appointment
import com.washdog.app.util.OverlapChecker
import com.washdog.app.util.Recurrence
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo     = AppRepository(application)
    private val authRepo = AuthRepository()
    private val credentialStore = CredentialStore(application)

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

    fun login(email: String, password: String, remember: Boolean = false, onError: (String) -> Unit) {
        isLoggingIn = true
        viewModelScope.launch {
            try {
                authRepo.login(email, password)
                if (remember) credentialStore.save(email, password) else credentialStore.clear()
                loadData()
                isLoggedIn = true
            } catch (e: Exception) {
                onError(e.message ?: "Error al iniciar sesión")
            } finally {
                isLoggingIn = false
            }
        }
    }

    /**
     * Cierra la sesión. Por defecto conserva las credenciales recordadas (para volver a
     * entrar con huella sin reescribir); [clearCredentials] las borra explícitamente.
     */
    fun logout(clearCredentials: Boolean = false) {
        viewModelScope.launch {
            try { authRepo.logout() } catch (_: Exception) {}
            if (clearCredentials) credentialStore.clear()
            transactions.clear()
            clients.clear()
            appointments.clear()
            isLoggedIn = false
        }
    }

    // ── RECORDAR CREDENCIALES ───────────────────────────────────────────────────

    fun hasRememberedCredentials(): Boolean = credentialStore.hasCredentials()

    /** (email, password) guardados, o null. Usar solo tras desbloqueo biométrico. */
    fun rememberedCredentials(): Pair<String, String>? = credentialStore.load()

    fun forgetCredentials() = credentialStore.clear()

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
        val mergedAppt = mergeById(localAppt, cloud?.appointments ?: emptyList()) { it.id }
        // Rellena ocurrencias futuras de series recurrentes para que sigan en el calendario.
        val finalAppt = topUpRecurringSeries(mergedAppt)
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
        val toAdd = expandRecurring(a)
        appointments.addAll(toAdd)
        repo.saveAppointments(appointments.toList())
        viewModelScope.launch { try { repo.syncAppointmentsToCloud(appointments.toList()) } catch (_: Exception) {} }
    }

    /**
     * Si [base] es recurrente ([repeatEveryDays] != null), devuelve la cita base más las
     * ocurrencias futuras dentro del horizonte (mismo [seriesId]), omitiendo las que
     * solapen con citas ya existentes. Si no es recurrente, devuelve solo [base].
     */
    private fun expandRecurring(base: Appointment): List<Appointment> {
        val every = base.repeatEveryDays ?: return listOf(base)
        val seriesId = base.seriesId ?: UUID.randomUUID().toString()
        val head = base.copy(seriesId = seriesId)
        val existing = appointments.toMutableList().apply { add(head) }
        val extra = Recurrence.occurrencesWithinHorizon(base.date, every)
            .mapNotNull { date ->
                val occ = head.copy(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    status = ApptStatus.SCHEDULED
                )
                if (OverlapChecker.findConflict(occ, existing) == null) {
                    existing.add(occ); occ
                } else null
            }
        return listOf(head) + extra
    }

    /** Detiene una serie recurrente: borra sus ocurrencias FUTURAS (fecha >= hoy). */
    fun stopSeries(seriesId: String) {
        val today = LocalDate.now().toString()
        val toRemove = appointments.filter {
            it.seriesId == seriesId && it.date >= today
        }.map { it.id }
        if (toRemove.isEmpty()) return
        appointments.removeAll { it.id in toRemove }
        repo.saveAppointments(appointments.toList())
        viewModelScope.launch {
            try { toRemove.forEach { repo.deleteAppointmentFromCloud(it) } } catch (_: Exception) {}
        }
    }

    /**
     * Rellena las ocurrencias faltantes de cada serie recurrente hasta el horizonte, para
     * que las citas periódicas sigan apareciendo en el calendario en cada arranque.
     * Devuelve la lista completa (existente + nuevas) sin duplicar fechas por serie.
     */
    private fun topUpRecurringSeries(current: List<Appointment>): List<Appointment> {
        val bySeries = current.filter { it.seriesId != null && it.repeatEveryDays != null }
            .groupBy { it.seriesId!! }
        if (bySeries.isEmpty()) return current
        val today = LocalDate.now()
        val generated = mutableListOf<Appointment>()
        val running = current.toMutableList()
        for ((_, occ) in bySeries) {
            val template = occ.minByOrNull { it.date } ?: continue
            val every = template.repeatEveryDays ?: continue
            val existingDates = occ.map { it.date }.toSet()
            val missing = Recurrence.missingOccurrences(
                startDate = template.date,
                everyDays = every,
                existingDates = existingDates,
                today = today
            )
            for (date in missing) {
                val cand = template.copy(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    status = ApptStatus.SCHEDULED
                )
                if (OverlapChecker.findConflict(cand, running) == null) {
                    running.add(cand); generated.add(cand)
                }
            }
        }
        return if (generated.isEmpty()) current else current + generated
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
