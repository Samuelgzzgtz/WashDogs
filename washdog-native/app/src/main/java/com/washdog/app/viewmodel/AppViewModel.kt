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
import com.washdog.app.util.ClientLink
import com.washdog.app.util.RemindLogic
import com.washdog.app.util.SyncMerge
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.Tombstone
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import java.time.Instant
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
    private var templatesUpdatedAt = ""
    var tplConfirm    by mutableStateOf(AppRepository.DEFAULT_CONFIRM);    private set
    var tplReschedule by mutableStateOf(AppRepository.DEFAULT_RESCHEDULE); private set

    var overlapConflict by mutableStateOf<Appointment?>(null); private set

    var isLoggedIn     by mutableStateOf(false); private set
    var isLoggingIn    by mutableStateOf(false); private set
    var sessionLoading by mutableStateOf(true);  private set

    /** true si hay cambios locales que no se han podido subir a la nube todavía. */
    var pendingSync    by mutableStateOf(false); private set

    // Tombstones (borrados) conocidos localmente, para propagarlos y filtrar el merge.
    private val tombstones = mutableListOf<Tombstone>()

    private fun now(): String = Instant.now().toString()

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
            tombstones.clear()
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
        val localSettings = repo.loadTemplates()
        val initialized = repo.isInitialized()

        val localTombstones = repo.loadTombstones()
        pendingSync = repo.isUnsynced()

        // 2) Nube (puede ser null si no hay red o falla).
        val cloud = try { repo.loadFromCloud() } catch (_: Exception) { null }

        // 3) Tombstones: unión local ∪ nube, quedándose con la fecha de borrado más
        //    reciente por (entidad, id). Se propagan los borrados entre dispositivos.
        val allTombstones = mergeTombstones(localTombstones, cloud?.tombstones ?: emptyList())
        tombstones.clear(); tombstones.addAll(allTombstones)

        // 4) Merge last-write-wins por `updatedAt` y aplicación de tombstones (v1.3.0):
        //    el registro más recientemente editado gana; los borrados se respetan salvo
        //    que el registro se haya editado después de borrarse.
        val finalCl = SyncMerge.applyTombstones(
            SyncMerge.mergeByUpdatedAt(localCl, cloud?.clients ?: emptyList(), { it.id }, { it.updatedAt }),
            tombstoneMap("clients"), { it.id }, { it.updatedAt }
        )
        val mergedAppt = SyncMerge.applyTombstones(
            SyncMerge.mergeByUpdatedAt(localAppt, cloud?.appointments ?: emptyList(), { it.id }, { it.updatedAt }),
            tombstoneMap("appointments"), { it.id }, { it.updatedAt }
        )
        // Migración v1.3.0: rellenar clientId faltante de citas legacy por nombre.
        val stoppedAppt = allTombstones.filter { it.entity == "appointment_series" }
            .fold(mergedAppt) { current, stop -> Recurrence.stopSeries(current, stop.entityId, stop.deletedAt) }
        val toppedAppt = topUpRecurringSeries(stoppedAppt)
        val finalAppt = ClientLink.backfill(toppedAppt, finalCl)
        val finalTx = SyncMerge.applyTombstones(
            SyncMerge.mergeByUpdatedAt(localTx, cloud?.transactions ?: emptyList(), { it.id }, { it.updatedAt }),
            tombstoneMap("transactions"), { it.id }, { it.updatedAt }
        )

        val settings = if (localSettings.updatedAt.isEmpty()) cloud?.settings ?: localSettings else SyncMerge.mergeByUpdatedAt(
            listOf(localSettings), listOfNotNull(cloud?.settings), { it.userId }, { it.updatedAt }
        ).first()
        tplWelcome = settings.tplWelcome; tplConfirm = settings.tplConfirm; tplReschedule = settings.tplReschedule
        templatesUpdatedAt = settings.updatedAt

        val everythingEmpty = finalCl.isEmpty() && finalAppt.isEmpty() && finalTx.isEmpty()

        if (everythingEmpty && !initialized && allTombstones.isEmpty()) {
            // Primer arranque real: sembrar demo y subir a la nube.
            loadSeedTransactions(); loadSeedClients(); loadSeedAppointments()
            repo.markInitialized()
            pushToCloud()
            return
        }

        // 5) Publicar merge en UI y persistir local.
        transactions.clear(); transactions.addAll(finalTx)
        clients.clear();      clients.addAll(finalCl)
        appointments.clear(); appointments.addAll(finalAppt)
        repo.saveTransactions(finalTx)
        repo.saveClients(finalCl)
        repo.saveAppointments(finalAppt)
        repo.saveTemplates(tplWelcome, tplConfirm, tplReschedule, templatesUpdatedAt)
        repo.saveTombstones(allTombstones)
        repo.markInitialized()

        // 6) Subir el merge a la nube. Si no había conexión (cloud == null) o falla,
        //    se marca pendiente de sincronizar para el indicador de la UI.
        if (cloud != null) pushToCloud() else { pendingSync = true; repo.setUnsynced(true) }
    }

    /** Sube el estado local completo a la nube; ajusta [pendingSync] según el resultado. */
    private fun pushToCloud() {
        viewModelScope.launch {
            try {
                repo.syncTombstonesToCloud(tombstones.toList())
                repo.syncClientsToCloud(clients.toList())
                repo.syncAppointmentsToCloud(appointments.toList())
                repo.syncTransactionsToCloud(transactions.toList())
                repo.syncSettingsToCloud(tplWelcome, tplConfirm, tplReschedule, templatesUpdatedAt)
                pendingSync = false; repo.setUnsynced(false)
            } catch (_: Exception) {
                pendingSync = true; repo.setUnsynced(true)
            }
        }
    }

    /** Reintenta subir los cambios pendientes (para el botón "reintentar" del indicador). */
    fun retrySync() = pushToCloud()

    // Unión de tombstones: mayor deletedAt por (entidad, id).
    private fun mergeTombstones(a: List<Tombstone>, b: List<Tombstone>): List<Tombstone> {
        val byKey = LinkedHashMap<String, Tombstone>()
        (a + b).forEach { t ->
            val key = "${t.entity}|${t.entityId}"
            val cur = byKey[key]
            if (cur == null || t.deletedAt > cur.deletedAt) byKey[key] = t
        }
        return byKey.values.toList()
    }

    private fun tombstoneMap(entity: String): Map<String, String> =
        tombstones.filter { it.entity == entity }.associate { it.entityId to it.deletedAt }

    // ── MUTATIONS + CLOUD SYNC ────────────────────────────────────────────────

    /**
     * Lanza un sync a la nube: al lograrlo limpia el indicador de pendientes; si falla lo
     * marca. Cada mutación sube su lista completa, así que un éxito refleja que esa
     * entidad quedó al día; el sync completo de `loadData` es la limpieza autoritativa.
     */
    private fun fireSync(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                pendingSync = false; repo.setUnsynced(false)
            } catch (_: Exception) {
                pendingSync = true; repo.setUnsynced(true)
            }
        }
    }

    /** Registra un borrado como tombstone (local + nube) para propagarlo entre dispositivos. */
    private fun addTombstone(entity: String, id: String) {
        tombstones.removeAll { it.entity == entity && it.entityId == id }
        tombstones.add(Tombstone(entity, id, now()))
        repo.saveTombstones(tombstones.toList())
    }

    fun addTransaction(t: Transaction) {
        val stamped = t.copy(updatedAt = now())
        transactions.add(0, stamped)
        repo.saveTransactions(transactions.toList())
        fireSync { repo.syncTransactionsToCloud(transactions.toList()) }
    }

    fun deleteTransaction(transactionId: String) {
        transactions.removeAll { it.id == transactionId }
        repo.saveTransactions(transactions.toList())
        addTombstone("transactions", transactionId)
        fireSync {
            repo.deleteTransactionFromCloud(transactionId)
            repo.syncTombstonesToCloud(tombstones.toList())
        }
    }

    fun addClient(c: Client) {
        val stamped = c.copy(updatedAt = now())
        clients.add(stamped)
        repo.saveClients(clients.toList())
        fireSync { repo.syncClientsToCloud(clients.toList()) }
    }

    fun updateClient(updated: Client) {
        val idx = clients.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            clients[idx] = updated.copy(updatedAt = now())
            repo.saveClients(clients.toList())
            fireSync { repo.syncClientsToCloud(clients.toList()) }
        }
    }

    fun deleteClient(clientId: String) {
        val name = clients.firstOrNull { it.id == clientId }?.ownerName
        clients.removeAll { it.id == clientId }
        addTombstone("clients", clientId)
        // Cascada por clientId (v1.3.0); para citas legacy sin clientId, cae al nombre.
        fun belongs(a: Appointment) =
            a.clientId == clientId || (a.clientId.isBlank() && name != null && a.clientName == name)
        val removedApptIds = appointments.filter { belongs(it) }.map { it.id }
        appointments.removeAll { belongs(it) }
        removedApptIds.forEach { addTombstone("appointments", it) }
        repo.saveClients(clients.toList())
        repo.saveAppointments(appointments.toList())
        fireSync {
            repo.syncTombstonesToCloud(tombstones.toList())
        }
    }

    fun addAppointment(a: Appointment) {
        val toAdd = expandRecurring(a).map { it.copy(updatedAt = now()) }
        appointments.addAll(toAdd)
        repo.saveAppointments(appointments.toList())
        fireSync {
            repo.syncClientsToCloud(clients.toList())
            repo.syncAppointmentsToCloud(appointments.toList())
        }
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
                    id = Recurrence.occurrenceId(seriesId, date),
                    date = date,
                    status = ApptStatus.SCHEDULED
                )
                if (OverlapChecker.findConflict(occ, existing) == null) {
                    existing.add(occ); occ
                } else null
            }
        return listOf(head) + extra
    }

    /** Detiene una serie de forma duradera; conserva historial y visitas completadas. */
    fun stopSeries(seriesId: String) {
        addTombstone("appointment_series", seriesId)
        val stoppedAt = tombstones.first { it.entity == "appointment_series" && it.entityId == seriesId }.deletedAt
        val remaining = Recurrence.stopSeries(appointments.toList(), seriesId, stoppedAt)
        val toRemove = appointments.map { it.id }.toSet() - remaining.map { it.id }.toSet()
        appointments.clear(); appointments.addAll(remaining)
        toRemove.forEach { addTombstone("appointments", it) }
        repo.saveAppointments(appointments.toList())
        fireSync {
            repo.syncTombstonesToCloud(tombstones.toList())
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
                    id = Recurrence.occurrenceId(template.seriesId!!, date),
                    date = date,
                    status = ApptStatus.SCHEDULED,
                    updatedAt = now()
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
            appointments[idx] = updated.copy(updatedAt = now())
            repo.saveAppointments(appointments.toList())
            fireSync {
                repo.syncClientsToCloud(clients.toList())
                repo.syncAppointmentsToCloud(appointments.toList())
            }
        }
    }

    fun deleteAppointment(appointmentId: String) {
        appointments.removeAll { it.id == appointmentId }
        repo.saveAppointments(appointments.toList())
        addTombstone("appointments", appointmentId)
        fireSync {
            repo.deleteAppointmentFromCloud(appointmentId)
            repo.syncTombstonesToCloud(tombstones.toList())
        }
    }

    fun updateTemplates(welcome: String, confirm: String, reschedule: String) {
        tplWelcome = welcome; tplConfirm = confirm; tplReschedule = reschedule
        val updatedAt = now()
        templatesUpdatedAt = updatedAt
        repo.saveTemplates(welcome, confirm, reschedule, updatedAt)
        fireSync { repo.syncSettingsToCloud(welcome, confirm, reschedule, updatedAt) }
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
        get() = RemindLogic.clientsNeedingReminder(clients, appointments)

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
            Appointment(id = "1", clientId = "2", clientName = "Carla Mendoza",  petName = "Luna",     service = "Baño+Corte", time = "10:20", date = LocalDate.now().minusDays(1).toString(), status = ApptStatus.DONE),
            Appointment(id = "2", clientId = "4", clientName = "Luis Hernández", petName = "Firulais", service = "Corte uñas", time = "09:00", date = LocalDate.now().minusDays(1).toString(), status = ApptStatus.DONE),
            Appointment(id = "3", clientId = "1", clientName = "Camila Rivas",   petName = "Mochi",    service = "Baño+Corte", time = "14:00", date = LocalDate.now().plusDays(2).toString(),  status = ApptStatus.NEXT),
            Appointment(id = "4", clientId = "3", clientName = "Ana Valdés",     petName = "Niebla",   service = "Baño gato",  time = "16:00", date = LocalDate.now().plusDays(3).toString(),  status = ApptStatus.SCHEDULED),
        ))
        repo.saveAppointments(appointments.toList())
    }
}
