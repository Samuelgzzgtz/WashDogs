# Proximity Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir detección automática de zona geográfica del cliente, cargo de traslado, buffer de tiempo y configuración de horarios de negocio a la app Android Washdog.

**Architecture:** 4 zonas (NEAR/CLOSE/MEDIUM/FAR) detectadas por keywords en la dirección del cliente. `ProximityEngine` es un objeto puro sin dependencias Android, fácil de testear. Los modelos `Client` y `Appointment` reciben campos nuevos con defaults para mantener retrocompatibilidad con JSON en DataStore.

**Tech Stack:** Kotlin · Jetpack Compose · DataStore + Gson · JUnit 4 (unit tests) · Android minSdk 26

---

## File Map

| Acción | Archivo |
|--------|---------|
| Crear | `data/models/ServiceZone.kt` |
| Crear | `data/models/ServiceLocation.kt` |
| Crear | `data/models/BusinessHours.kt` |
| Crear | `data/models/ZonePrices.kt` |
| Modificar | `data/models/Client.kt` |
| Modificar | `data/models/Appointment.kt` |
| Crear | `util/ProximityEngine.kt` |
| Crear | `app/src/test/java/com/washdog/app/util/ProximityEngineTest.kt` |
| Modificar | `data/AppRepository.kt` |
| Modificar | `viewmodel/AppViewModel.kt` |
| Crear | `ui/components/WDZonePill.kt` |
| Modificar | `ui/screens/AddClientScreen.kt` |
| Modificar | `ui/screens/EditClientScreen.kt` |
| Modificar | `ui/screens/AddAppointmentScreen.kt` |
| Modificar | `ui/screens/SettingsScreen.kt` |
| Modificar | `ui/screens/AgendaScreen.kt` |

Todos los paths son relativos a `washdog-native/app/src/main/java/com/washdog/app/` salvo los de test y plan.

---

### Task 1: Nuevos enums y data classes de modelos

**Files:**
- Create: `washdog-native/app/src/main/java/com/washdog/app/data/models/ServiceZone.kt`
- Create: `washdog-native/app/src/main/java/com/washdog/app/data/models/ServiceLocation.kt`
- Create: `washdog-native/app/src/main/java/com/washdog/app/data/models/BusinessHours.kt`
- Create: `washdog-native/app/src/main/java/com/washdog/app/data/models/ZonePrices.kt`

- [ ] **Step 1: Crear ServiceZone.kt**

```kotlin
package com.washdog.app.data.models

enum class ServiceZone { NEAR, CLOSE, MEDIUM, FAR }
```

- [ ] **Step 2: Crear ServiceLocation.kt**

```kotlin
package com.washdog.app.data.models

enum class ServiceLocation { HOME, BASE }
```

- [ ] **Step 3: Crear BusinessHours.kt**

```kotlin
package com.washdog.app.data.models

data class BusinessHours(
    val openTime: String   = "09:00",
    val closeTime: String  = "19:00",
    val closedDays: List<Int> = listOf(0)   // 0=Dom, 1=Lun … 6=Sáb
)
```

- [ ] **Step 4: Crear ZonePrices.kt**

```kotlin
package com.washdog.app.data.models

data class ZonePrices(
    val nearFee:    Double = 0.0,    val nearBuffer:   Int = 0,
    val closeFee:   Double = 50.0,   val closeBuffer:  Int = 10,
    val mediumFee:  Double = 90.0,   val mediumBuffer: Int = 25,
    val farFee:     Double = 120.0,  val farBuffer:    Int = 40
)
```

- [ ] **Step 5: Compilar para verificar que no hay errores de sintaxis**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/data/models/ServiceZone.kt
git add washdog-native/app/src/main/java/com/washdog/app/data/models/ServiceLocation.kt
git add washdog-native/app/src/main/java/com/washdog/app/data/models/BusinessHours.kt
git add washdog-native/app/src/main/java/com/washdog/app/data/models/ZonePrices.kt
git commit -m "feat: add ServiceZone, ServiceLocation, BusinessHours, ZonePrices models"
```

---

### Task 2: Actualizar Client y Appointment con campos nuevos

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/data/models/Client.kt`
- Modify: `washdog-native/app/src/main/java/com/washdog/app/data/models/Appointment.kt`

- [ ] **Step 1: Reemplazar Client.kt completo**

```kotlin
package com.washdog.app.data.models

enum class PetType { DOG, CAT }

data class Client(
    val id: String,
    val ownerName: String,
    val petName: String,
    val breed: String,
    val lastVisit: String,
    val needsReschedule: Boolean,
    val petType: PetType,
    val phone: String = "",
    val address: String = "",
    val zone: ServiceZone = ServiceZone.FAR
)
```

- [ ] **Step 2: Reemplazar Appointment.kt completo**

```kotlin
package com.washdog.app.data.models

enum class ApptStatus { DONE, NEXT, REMINDER, SCHEDULED }

data class Appointment(
    val id: String,
    val clientName: String,
    val petName: String,
    val service: String,
    val time: String,
    val date: String,
    val status: ApptStatus,
    val endTime: String = "",
    val serviceLocation: ServiceLocation = ServiceLocation.HOME,
    val travelFee: Double = 0.0,
    val travelBuffer: Int = 0
)
```

- [ ] **Step 3: Compilar — los campos tienen defaults, no debe haber errores**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/data/models/Client.kt
git add washdog-native/app/src/main/java/com/washdog/app/data/models/Appointment.kt
git commit -m "feat: add address/zone to Client and serviceLocation/travelFee/travelBuffer to Appointment"
```

---

### Task 3: ProximityEngine — TDD

**Files:**
- Create: `washdog-native/app/src/test/java/com/washdog/app/util/ProximityEngineTest.kt`
- Create: `washdog-native/app/src/main/java/com/washdog/app/util/ProximityEngine.kt`

- [ ] **Step 1: Crear directorio de tests si no existe**

```powershell
New-Item -ItemType Directory -Force -Path "washdog-native\app\src\test\java\com\washdog\app\util"
```

- [ ] **Step 2: Escribir los tests fallidos**

Crear `washdog-native/app/src/test/java/com/washdog/app/util/ProximityEngineTest.kt`:

```kotlin
package com.washdog.app.util

import com.washdog.app.data.models.ServiceLocation
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.data.models.ZonePrices
import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityEngineTest {

    // ── detectZone ────────────────────────────────────────────────────────────

    @Test
    fun `detectZone returns NEAR for granada keyword`() {
        assertEquals(ServiceZone.NEAR, ProximityEngine.detectZone("Privada Granada 45"))
    }

    @Test
    fun `detectZone is case insensitive`() {
        assertEquals(ServiceZone.NEAR, ProximityEngine.detectZone("privada GRANADA 12, Mitras Poniente"))
    }

    @Test
    fun `detectZone returns CLOSE for ensenada`() {
        assertEquals(ServiceZone.CLOSE, ProximityEngine.detectZone("Privada Ensenada 10"))
    }

    @Test
    fun `detectZone returns CLOSE for cedral`() {
        assertEquals(ServiceZone.CLOSE, ProximityEngine.detectZone("Privada Cedral 5, Mitras Poniente"))
    }

    @Test
    fun `detectZone returns CLOSE for ajusco`() {
        assertEquals(ServiceZone.CLOSE, ProximityEngine.detectZone("Privada Ajusco 3"))
    }

    @Test
    fun `detectZone returns MEDIUM for mitras poniente without near keywords`() {
        assertEquals(ServiceZone.MEDIUM, ProximityEngine.detectZone("Calle Hidalgo 200, Mitras Poniente"))
    }

    @Test
    fun `detectZone returns FAR for unrelated address`() {
        assertEquals(ServiceZone.FAR, ProximityEngine.detectZone("Av. Constitución 500, Centro"))
    }

    @Test
    fun `detectZone returns FAR for empty address`() {
        assertEquals(ServiceZone.FAR, ProximityEngine.detectZone(""))
    }

    // ── travelFee ─────────────────────────────────────────────────────────────

    @Test
    fun `travelFee returns 0 for BASE regardless of zone`() {
        val prices = ZonePrices()
        assertEquals(0.0, ProximityEngine.travelFee(ServiceZone.FAR,    ServiceLocation.BASE, prices), 0.01)
        assertEquals(0.0, ProximityEngine.travelFee(ServiceZone.MEDIUM, ServiceLocation.BASE, prices), 0.01)
        assertEquals(0.0, ProximityEngine.travelFee(ServiceZone.CLOSE,  ServiceLocation.BASE, prices), 0.01)
        assertEquals(0.0, ProximityEngine.travelFee(ServiceZone.NEAR,   ServiceLocation.BASE, prices), 0.01)
    }

    @Test
    fun `travelFee returns correct fee per zone for HOME`() {
        val prices = ZonePrices()
        assertEquals(0.0,   ProximityEngine.travelFee(ServiceZone.NEAR,   ServiceLocation.HOME, prices), 0.01)
        assertEquals(50.0,  ProximityEngine.travelFee(ServiceZone.CLOSE,  ServiceLocation.HOME, prices), 0.01)
        assertEquals(90.0,  ProximityEngine.travelFee(ServiceZone.MEDIUM, ServiceLocation.HOME, prices), 0.01)
        assertEquals(120.0, ProximityEngine.travelFee(ServiceZone.FAR,    ServiceLocation.HOME, prices), 0.01)
    }

    @Test
    fun `travelFee respects custom ZonePrices`() {
        val prices = ZonePrices(farFee = 200.0)
        assertEquals(200.0, ProximityEngine.travelFee(ServiceZone.FAR, ServiceLocation.HOME, prices), 0.01)
    }

    // ── travelBuffer ──────────────────────────────────────────────────────────

    @Test
    fun `travelBuffer returns 0 for BASE regardless of zone`() {
        val prices = ZonePrices()
        assertEquals(0, ProximityEngine.travelBuffer(ServiceZone.FAR,    ServiceLocation.BASE, prices))
        assertEquals(0, ProximityEngine.travelBuffer(ServiceZone.MEDIUM, ServiceLocation.BASE, prices))
    }

    @Test
    fun `travelBuffer returns correct buffer per zone for HOME`() {
        val prices = ZonePrices()
        assertEquals(0,  ProximityEngine.travelBuffer(ServiceZone.NEAR,   ServiceLocation.HOME, prices))
        assertEquals(10, ProximityEngine.travelBuffer(ServiceZone.CLOSE,  ServiceLocation.HOME, prices))
        assertEquals(25, ProximityEngine.travelBuffer(ServiceZone.MEDIUM, ServiceLocation.HOME, prices))
        assertEquals(40, ProximityEngine.travelBuffer(ServiceZone.FAR,    ServiceLocation.HOME, prices))
    }
}
```

- [ ] **Step 3: Correr los tests — deben FALLAR (ProximityEngine no existe aún)**

```powershell
cd washdog-native
.\gradlew test --tests "com.washdog.app.util.ProximityEngineTest"
```

Expected: `BUILD SUCCESSFUL` pero con mensajes `FAILED` en los tests individuales (unresolved reference: ProximityEngine).

- [ ] **Step 4: Crear la implementación mínima**

Crear `washdog-native/app/src/main/java/com/washdog/app/util/ProximityEngine.kt`:

```kotlin
package com.washdog.app.util

import com.washdog.app.data.models.ServiceLocation
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.data.models.ZonePrices

object ProximityEngine {

    private val NEAR_KEYWORDS  = listOf("granada")
    private val CLOSE_KEYWORDS = listOf("ensenada", "cedral", "ajusco")

    fun detectZone(address: String): ServiceZone {
        val n = address.lowercase()
        return when {
            NEAR_KEYWORDS.any  { n.contains(it) } -> ServiceZone.NEAR
            CLOSE_KEYWORDS.any { n.contains(it) } -> ServiceZone.CLOSE
            n.contains("mitras poniente")          -> ServiceZone.MEDIUM
            else                                   -> ServiceZone.FAR
        }
    }

    fun travelFee(zone: ServiceZone, location: ServiceLocation, prices: ZonePrices): Double =
        if (location == ServiceLocation.BASE) 0.0
        else when (zone) {
            ServiceZone.NEAR   -> prices.nearFee
            ServiceZone.CLOSE  -> prices.closeFee
            ServiceZone.MEDIUM -> prices.mediumFee
            ServiceZone.FAR    -> prices.farFee
        }

    fun travelBuffer(zone: ServiceZone, location: ServiceLocation, prices: ZonePrices): Int =
        if (location == ServiceLocation.BASE) 0
        else when (zone) {
            ServiceZone.NEAR   -> prices.nearBuffer
            ServiceZone.CLOSE  -> prices.closeBuffer
            ServiceZone.MEDIUM -> prices.mediumBuffer
            ServiceZone.FAR    -> prices.farBuffer
        }
}
```

- [ ] **Step 5: Correr los tests — deben PASAR todos**

```powershell
cd washdog-native
.\gradlew test --tests "com.washdog.app.util.ProximityEngineTest"
```

Expected output (todos en verde):
```
> Task :app:testDebugUnitTest
com.washdog.app.util.ProximityEngineTest > detectZone returns NEAR for granada keyword PASSED
com.washdog.app.util.ProximityEngineTest > detectZone is case insensitive PASSED
...
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add washdog-native/app/src/test/java/com/washdog/app/util/ProximityEngineTest.kt
git add washdog-native/app/src/main/java/com/washdog/app/util/ProximityEngine.kt
git commit -m "feat: add ProximityEngine with full test coverage"
```

---

### Task 4: AppRepository — persistencia de BusinessHours y ZonePrices

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/data/AppRepository.kt`

- [ ] **Step 1: Agregar imports y claves nuevas**

Reemplazar el bloque de claves privadas en `AppRepository.kt` (líneas 29–35) con:

```kotlin
    private val KEY_TX             = stringPreferencesKey("transactions")
    private val KEY_CL             = stringPreferencesKey("clients")
    private val KEY_APPT           = stringPreferencesKey("appointments")
    private val KEY_TPL_WELCOME    = stringPreferencesKey("tpl_welcome")
    private val KEY_TPL_CONFIRM    = stringPreferencesKey("tpl_confirm")
    private val KEY_TPL_RESCHEDULE = stringPreferencesKey("tpl_reschedule")
    private val KEY_INITIALIZED    = booleanPreferencesKey("initialized")
    private val KEY_BUSINESS_HOURS = stringPreferencesKey("business_hours")
    private val KEY_ZONE_PRICES    = stringPreferencesKey("zone_prices")
```

Agregar al bloque de imports (después de `import com.washdog.app.data.models.Transaction`):

```kotlin
import com.washdog.app.data.models.BusinessHours
import com.washdog.app.data.models.ZonePrices
```

- [ ] **Step 2: Agregar métodos save y load — insertar antes del cierre de clase (`}`)**

```kotlin
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
```

- [ ] **Step 3: Compilar**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/data/AppRepository.kt
git commit -m "feat: persist BusinessHours and ZonePrices in DataStore"
```

---

### Task 5: AppViewModel — estado, métodos y auto-detección de zona

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt`

- [ ] **Step 1: Agregar imports al inicio del archivo (después de los imports existentes)**

```kotlin
import com.washdog.app.data.models.BusinessHours
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.data.models.ZonePrices
import com.washdog.app.util.ProximityEngine
```

- [ ] **Step 2: Agregar estados nuevos — después de `var overlapConflict`**

```kotlin
    var businessHours by mutableStateOf(BusinessHours())
        private set
    var zonePrices by mutableStateOf(ZonePrices())
        private set
```

- [ ] **Step 3: Cargar en loadData() — agregar al final del bloque `try` de loadData(), antes de las asignaciones de templates**

Reemplazar el bloque de `loadData()` completo con:

```kotlin
    private suspend fun loadData() {
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
```

- [ ] **Step 4: Agregar métodos de actualización — después de `updateTemplates()`**

```kotlin
    fun updateBusinessHours(bh: BusinessHours) {
        businessHours = bh
        repo.saveBusinessHours(bh)
    }

    fun updateZonePrices(zp: ZonePrices) {
        zonePrices = zp
        repo.saveZonePrices(zp)
    }
```

- [ ] **Step 5: Agregar validación de horario — después de `checkOverlap()`**

```kotlin
    fun isWithinBusinessHours(time: String, date: String): Boolean {
        return try {
            val dow = java.time.LocalDate.parse(date).dayOfWeek.value % 7   // 0=Dom
            if (dow in businessHours.closedDays) return false
            time >= businessHours.openTime && time <= businessHours.closeTime
        } catch (e: Exception) { true }
    }
```

- [ ] **Step 6: Auto-detectar zona en addClient() y updateClient()**

Reemplazar `addClient()`:

```kotlin
    fun addClient(c: Client) {
        val withZone = c.copy(zone = ProximityEngine.detectZone(c.address))
        clients.add(withZone)
        repo.saveClients(clients.toList())
    }
```

Reemplazar `updateClient()`:

```kotlin
    fun updateClient(updated: Client) {
        val withZone = updated.copy(zone = ProximityEngine.detectZone(updated.address))
        val idx = clients.indexOfFirst { it.id == withZone.id }
        if (idx >= 0) {
            clients[idx] = withZone
            repo.saveClients(clients.toList())
        }
    }
```

- [ ] **Step 7: Compilar y correr todos los tests**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, todos los tests PASSED.

- [ ] **Step 8: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt
git commit -m "feat: add businessHours/zonePrices state, isWithinBusinessHours, auto-detect zone on save"
```

---

### Task 6: WDZonePill — componente reutilizable

**Files:**
- Create: `washdog-native/app/src/main/java/com/washdog/app/ui/components/WDZonePill.kt`

- [ ] **Step 1: Crear WDZonePill.kt**

```kotlin
package com.washdog.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.ServiceZone

@Composable
fun WDZonePill(
    zone: ServiceZone,
    fee: Double,
    onZoneChange: (ServiceZone) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = zone.pillColor,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = zone.pillLabel(fee),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ServiceZone.entries.forEach { z ->
                DropdownMenuItem(
                    text = { Text(z.dropdownLabel) },
                    onClick = { onZoneChange(z); expanded = false }
                )
            }
        }
    }
}

val ServiceZone.pillColor: Color
    get() = when (this) {
        ServiceZone.NEAR   -> Color(0xFF4CAF50)
        ServiceZone.CLOSE  -> Color(0xFF2196F3)
        ServiceZone.MEDIUM -> Color(0xFFFFC107)
        ServiceZone.FAR    -> Color(0xFFF44336)
    }

fun ServiceZone.pillLabel(fee: Double): String = when (this) {
    ServiceZone.NEAR   -> "Granada · $0"
    ServiceZone.CLOSE  -> "Próxima · $${ fee.toInt() }"
    ServiceZone.MEDIUM -> "Media · $${ fee.toInt() }"
    ServiceZone.FAR    -> "Lejos · $${ fee.toInt() }"
}

val ServiceZone.dropdownLabel: String
    get() = when (this) {
        ServiceZone.NEAR   -> "NEAR — Privada Granada"
        ServiceZone.CLOSE  -> "CLOSE — Ensenada / Cedral / Ajusco"
        ServiceZone.MEDIUM -> "MEDIUM — Mitras Poniente"
        ServiceZone.FAR    -> "FAR — Fuera de zona"
    }
```

- [ ] **Step 2: Compilar**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/components/WDZonePill.kt
git commit -m "feat: add WDZonePill component with zone override dropdown"
```

---

### Task 7: AddClientScreen y EditClientScreen — dirección + zona

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/ui/screens/AddClientScreen.kt`
- Modify: `washdog-native/app/src/main/java/com/washdog/app/ui/screens/EditClientScreen.kt`

- [ ] **Step 1: Actualizar AddClientScreen.kt completo**

```kotlin
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.ui.components.WDPhoneField
import com.washdog.app.ui.components.WDZonePill
import com.washdog.app.ui.components.digitsToPhone
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.util.ProximityEngine
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun AddClientScreen(vm: AppViewModel, onBack: () -> Unit) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 600.dp else Dp.Unspecified

    var ownerName   by remember { mutableStateOf("") }
    var petName     by remember { mutableStateOf("") }
    var breed       by remember { mutableStateOf("") }
    var phoneDigits by remember { mutableStateOf("") }
    var petType     by remember { mutableStateOf(PetType.DOG) }
    var address     by remember { mutableStateOf("") }
    val detectedZone by remember { derivedStateOf { ProximityEngine.detectZone(address) } }
    var zoneOverride by remember { mutableStateOf<ServiceZone?>(null) }
    val zone = zoneOverride ?: detectedZone

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier.widthIn(max = maxW).align(Alignment.TopCenter),
            contentPadding      = androidx.compose.foundation.layout.PaddingValues(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = WDInkMute)
                    }
                    Text("Nuevo cliente", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WDInk)
                }
            }
            item(key = "ownerName") {
                OutlinedTextField(
                    value = ownerName, onValueChange = { ownerName = it },
                    label = { Text("Nombre del dueño") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            item(key = "petName") {
                OutlinedTextField(
                    value = petName, onValueChange = { petName = it },
                    label = { Text("Nombre de la mascota") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            item(key = "breed") {
                OutlinedTextField(
                    value = breed, onValueChange = { breed = it },
                    label = { Text("Raza") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            item(key = "phone") {
                WDPhoneField(digits = phoneDigits, onDigitsChange = { phoneDigits = it }, modifier = Modifier.fillMaxWidth())
            }
            item(key = "petType") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tipo de mascota", fontSize = 13.sp, color = WDInkMute)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { petType = PetType.DOG }, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (petType == PetType.DOG) WDPrimary else WDInkMute),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (petType == PetType.DOG) WDPrimary else WDInkMute)
                        ) { Text("Perro 🐶") }
                        OutlinedButton(
                            onClick = { petType = PetType.CAT }, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (petType == PetType.CAT) WDPrimary else WDInkMute),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (petType == PetType.CAT) WDPrimary else WDInkMute)
                        ) { Text("Gato 🐱") }
                    }
                }
            }
            item(key = "address") {
                OutlinedTextField(
                    value = address, onValueChange = { address = it; zoneOverride = null },
                    label = { Text("Dirección") },
                    placeholder = { Text("Ej. Privada Ensenada 45, Mitras Poniente") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            item(key = "zone_pill") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zona detectada:", fontSize = 13.sp, color = WDInkMute)
                    WDZonePill(
                        zone = zone,
                        fee  = ProximityEngine.travelFee(zone, com.washdog.app.data.models.ServiceLocation.HOME, vm.zonePrices),
                        onZoneChange = { zoneOverride = it }
                    )
                }
            }
            item(key = "save") {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (ownerName.isBlank() || petName.isBlank()) return@Button
                        vm.addClient(
                            Client(
                                id              = java.util.UUID.randomUUID().toString(),
                                ownerName       = ownerName.trim(),
                                petName         = petName.trim(),
                                breed           = breed.trim(),
                                lastVisit       = java.time.LocalDate.now().toString(),
                                needsReschedule = false,
                                petType         = petType,
                                phone           = digitsToPhone(phoneDigits),
                                address         = address.trim(),
                                zone            = zone
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
                ) { Text("Guardar cliente", color = Color.White) }
            }
        }
    }
}
```

- [ ] **Step 2: Actualizar EditClientScreen.kt completo**

```kotlin
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.ServiceLocation
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.ui.components.WDPhoneField
import com.washdog.app.ui.components.WDZonePill
import com.washdog.app.ui.components.digitsToPhone
import com.washdog.app.ui.components.phoneToDigits
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDExpenseDk
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.util.ProximityEngine
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun EditClientScreen(vm: AppViewModel, clientId: String, onBack: () -> Unit) {
    val client = vm.clients.firstOrNull { it.id == clientId } ?: return

    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 600.dp else Dp.Unspecified

    var ownerName       by remember { mutableStateOf(client.ownerName) }
    var petName         by remember { mutableStateOf(client.petName) }
    var breed           by remember { mutableStateOf(client.breed) }
    var phoneDigits     by remember { mutableStateOf(phoneToDigits(client.phone)) }
    var petType         by remember { mutableStateOf(client.petType) }
    var needsReschedule by remember { mutableStateOf(client.needsReschedule) }
    var address         by remember { mutableStateOf(client.address) }
    val detectedZone    by remember { derivedStateOf { ProximityEngine.detectZone(address) } }
    var zoneOverride    by remember { mutableStateOf<ServiceZone?>(client.zone.takeIf { it != detectedZone }) }
    val zone = zoneOverride ?: detectedZone
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar cliente") },
            text  = { Text("¿Eliminar a ${client.ownerName}? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteClient(clientId); onBack() }) {
                    Text("Eliminar", color = WDExpenseDk)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier.widthIn(max = maxW).align(Alignment.TopCenter),
            contentPadding      = androidx.compose.foundation.layout.PaddingValues(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = WDInkMute)
                    }
                    Text("Editar cliente", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WDInk)
                }
            }
            item(key = "ownerName") {
                OutlinedTextField(value = ownerName, onValueChange = { ownerName = it },
                    label = { Text("Nombre del dueño") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item(key = "petName") {
                OutlinedTextField(value = petName, onValueChange = { petName = it },
                    label = { Text("Nombre de la mascota") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item(key = "breed") {
                OutlinedTextField(value = breed, onValueChange = { breed = it },
                    label = { Text("Raza") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item(key = "phone") {
                WDPhoneField(digits = phoneDigits, onDigitsChange = { phoneDigits = it }, modifier = Modifier.fillMaxWidth())
            }
            item(key = "petType") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tipo de mascota", fontSize = 13.sp, color = WDInkMute)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { petType = PetType.DOG }, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (petType == PetType.DOG) WDPrimary else WDInkMute),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (petType == PetType.DOG) WDPrimary else WDInkMute)
                        ) { Text("Perro 🐶") }
                        OutlinedButton(
                            onClick = { petType = PetType.CAT }, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (petType == PetType.CAT) WDPrimary else WDInkMute),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (petType == PetType.CAT) WDPrimary else WDInkMute)
                        ) { Text("Gato 🐱") }
                    }
                }
            }
            item(key = "reschedule") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Estado", fontSize = 13.sp, color = WDInkMute)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { needsReschedule = false }, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (!needsReschedule) WDPrimary else WDInkMute),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (!needsReschedule) WDPrimary else WDInkMute)
                        ) { Text("Normal") }
                        OutlinedButton(
                            onClick = { needsReschedule = true }, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (needsReschedule) WDExpenseDk else WDInkMute),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (needsReschedule) WDExpenseDk else WDInkMute)
                        ) { Text("Reagendar") }
                    }
                }
            }
            item(key = "address") {
                OutlinedTextField(
                    value = address, onValueChange = { address = it; zoneOverride = null },
                    label = { Text("Dirección") },
                    placeholder = { Text("Ej. Privada Ensenada 45, Mitras Poniente") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            item(key = "zone_pill") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zona:", fontSize = 13.sp, color = WDInkMute)
                    WDZonePill(
                        zone = zone,
                        fee  = ProximityEngine.travelFee(zone, ServiceLocation.HOME, vm.zonePrices),
                        onZoneChange = { zoneOverride = it }
                    )
                }
            }
            item(key = "save") {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (ownerName.isBlank() || petName.isBlank()) return@Button
                        vm.updateClient(
                            Client(
                                id              = clientId,
                                ownerName       = ownerName.trim(),
                                petName         = petName.trim(),
                                breed           = breed.trim(),
                                lastVisit       = client.lastVisit,
                                needsReschedule = needsReschedule,
                                petType         = petType,
                                phone           = digitsToPhone(phoneDigits),
                                address         = address.trim(),
                                zone            = zone
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
                ) { Text("Guardar cambios", color = Color.White) }
            }
            item(key = "delete") {
                OutlinedButton(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, WDExpenseDk),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = WDExpenseDk)
                ) { Text("Eliminar cliente") }
            }
        }
    }
}
```

- [ ] **Step 3: Compilar**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/AddClientScreen.kt
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/EditClientScreen.kt
git commit -m "feat: add address field and zone pill to client forms"
```

---

### Task 8: AddAppointmentScreen — card de zona + HOME/BASE + validación de horario

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/ui/screens/AddAppointmentScreen.kt`

- [ ] **Step 1: Agregar imports nuevos al inicio del archivo**

Después de los imports existentes, agregar:

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import com.washdog.app.data.models.ServiceLocation
import com.washdog.app.ui.components.pillColor
import com.washdog.app.ui.theme.WDBgAlt
import com.washdog.app.ui.theme.WDPrimaryTnt
import com.washdog.app.util.ProximityEngine
```

- [ ] **Step 2: Agregar estado de serviceLocation después del estado de `endTimeEnabled`**

```kotlin
    var serviceLocation by remember { mutableStateOf(ServiceLocation.HOME) }
```

- [ ] **Step 3: Agregar item de ZoneContextCard después del item `client_dropdown` (después de la sincronización de selected)**

Insertar después de `item(key = "client_dropdown")` y antes de `item(key = "pet_field")`:

```kotlin
            // Zona del cliente y selector HOME/BASE
            if (selectedClient != null) {
                item(key = "zone_card") {
                    ZoneContextCard(
                        zone            = selectedClient!!.zone,
                        zonePrices      = vm.zonePrices,
                        serviceLocation = serviceLocation,
                        onLocationChange = { serviceLocation = it }
                    )
                }
            }
```

- [ ] **Step 4: Agregar warning de horario después del item `time_field`**

Insertar después del bloque `item(key = "time_field")`:

```kotlin
            // Advertencia de horario
            if (time.isNotBlank() && date.isNotBlank()) {
                val dateOk = remember(date) {
                    try { java.time.LocalDate.parse(date); true } catch (e: Exception) { false }
                }
                if (dateOk && !vm.isWithinBusinessHours(time, date)) {
                    item(key = "hours_warning") {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = CardDefaults.cardColors(containerColor = WDExpenseTnt)
                        ) {
                            Text(
                                text     = "Fuera del horario de negocio (${vm.businessHours.openTime}–${vm.businessHours.closeTime})",
                                fontSize = 12.sp,
                                color    = WDExpenseDk,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
```

- [ ] **Step 5: Actualizar el bloque de guardado para incluir serviceLocation, travelFee y travelBuffer**

Reemplazar el `Button` de guardar (item `save_btn`) completo:

```kotlin
            item(key = "save_btn") {
                Spacer(Modifier.height(4.dp))
                val dateValid = remember(date) {
                    try { java.time.LocalDate.parse(date); true } catch (e: Exception) { false }
                }
                val fee    = if (selectedClient != null) ProximityEngine.travelFee(selectedClient!!.zone, serviceLocation, vm.zonePrices) else 0.0
                val buffer = if (selectedClient != null) ProximityEngine.travelBuffer(selectedClient!!.zone, serviceLocation, vm.zonePrices) else 0
                Button(
                    onClick = {
                        val client = selectedClient ?: vm.clients.firstOrNull {
                            "${it.petName} · ${it.ownerName}" == clientQuery
                        }
                        if (client != null && service.isNotBlank() && time.isNotBlank() && dateValid && conflict == null) {
                            vm.addAppointment(
                                Appointment(
                                    id              = java.util.UUID.randomUUID().toString(),
                                    clientName      = client.ownerName,
                                    petName         = client.petName,
                                    service         = service,
                                    time            = time,
                                    date            = date,
                                    status          = ApptStatus.SCHEDULED,
                                    endTime         = if (endTimeEnabled) endTime else "",
                                    serviceLocation = serviceLocation,
                                    travelFee       = fee,
                                    travelBuffer    = buffer
                                )
                            )
                            onBack()
                        }
                    },
                    enabled  = conflict == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary, contentColor = Color.White)
                ) { Text("Guardar cita") }
            }
```

- [ ] **Step 6: Agregar los composables privados al final del archivo (antes del cierre del archivo)**

```kotlin
@Composable
private fun ZoneContextCard(
    zone: com.washdog.app.data.models.ServiceZone,
    zonePrices: com.washdog.app.data.models.ZonePrices,
    serviceLocation: ServiceLocation,
    onLocationChange: (ServiceLocation) -> Unit
) {
    val borderColor = zone.pillColor
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = borderColor.copy(alpha = 0.08f)),
        border   = BorderStroke(1.dp, borderColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val homeFee    = ProximityEngine.travelFee(zone, ServiceLocation.HOME, zonePrices)
            val homeBuffer = ProximityEngine.travelBuffer(zone, ServiceLocation.HOME, zonePrices)
            Text(
                text = when (zone) {
                    com.washdog.app.data.models.ServiceZone.NEAR   -> "Privada Granada · Sin cargo de traslado"
                    com.washdog.app.data.models.ServiceZone.CLOSE  -> "Zona próxima · ${homeFee.toInt()} MXN traslado"
                    com.washdog.app.data.models.ServiceZone.MEDIUM -> "Zona media · ${homeFee.toInt()} MXN traslado"
                    com.washdog.app.data.models.ServiceZone.FAR    -> "Zona lejana · ${homeFee.toInt()} MXN traslado"
                },
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = WDInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocationOption(
                    selected  = serviceLocation == ServiceLocation.HOME,
                    icon      = "🏠",
                    label     = "A domicilio",
                    feeText   = if (homeFee > 0) "+$${homeFee.toInt()} · +${homeBuffer}min" else "Sin cargo",
                    onClick   = { onLocationChange(ServiceLocation.HOME) },
                    modifier  = Modifier.weight(1f)
                )
                LocationOption(
                    selected  = serviceLocation == ServiceLocation.BASE,
                    icon      = "🏪",
                    label     = "Trae a la base",
                    feeText   = "Sin cargo",
                    onClick   = { onLocationChange(ServiceLocation.BASE) },
                    modifier  = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LocationOption(
    selected: Boolean,
    icon: String,
    label: String,
    feeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape    = RoundedCornerShape(10.dp),
        color    = if (selected) WDPrimaryTnt else WDBgAlt,
        border   = BorderStroke(1.dp, if (selected) WDPrimary else WDInkMute)
    ) {
        Column(
            modifier              = Modifier.padding(10.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(label,   fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WDInk)
            Text(feeText, fontSize = 10.sp, color = if (selected) WDPrimary else WDInkMute)
        }
    }
}

- [ ] **Step 7: Compilar**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
```

Si hay errores de import o clickable, ajustar los imports según el error. Expected final: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/AddAppointmentScreen.kt
git commit -m "feat: add zone context card, HOME/BASE selector and business hours warning to appointment form"
```

---

### Task 9: SettingsScreen — horarios de negocio y precios por zona

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/ui/screens/SettingsScreen.kt`

- [ ] **Step 1: Agregar imports nuevos**

Después de los imports existentes:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.washdog.app.data.models.BusinessHours
import com.washdog.app.data.models.ZonePrices
import com.washdog.app.ui.components.WDTimePickerDialog
```

- [ ] **Step 2: Agregar estados nuevos en SettingsScreen — después de `var reschedule`**

```kotlin
    // Horarios
    var openTime   by remember { mutableStateOf(vm.businessHours.openTime) }
    var closeTime  by remember { mutableStateOf(vm.businessHours.closeTime) }
    var closedDays by remember { mutableStateOf(vm.businessHours.closedDays.toSet()) }
    var showOpenPicker  by remember { mutableStateOf(false) }
    var showClosePicker by remember { mutableStateOf(false) }

    // Precios por zona
    var nearFee      by remember { mutableStateOf(vm.zonePrices.nearFee.toInt().toString()) }
    var nearBuffer   by remember { mutableStateOf(vm.zonePrices.nearBuffer.toString()) }
    var closeFee     by remember { mutableStateOf(vm.zonePrices.closeFee.toInt().toString()) }
    var closeBuffer  by remember { mutableStateOf(vm.zonePrices.closeBuffer.toString()) }
    var mediumFee    by remember { mutableStateOf(vm.zonePrices.mediumFee.toInt().toString()) }
    var mediumBuffer by remember { mutableStateOf(vm.zonePrices.mediumBuffer.toString()) }
    var farFee       by remember { mutableStateOf(vm.zonePrices.farFee.toInt().toString()) }
    var farBuffer    by remember { mutableStateOf(vm.zonePrices.farBuffer.toString()) }
```

- [ ] **Step 3: Agregar diálogos de tiempo — después de los `remember` y antes del `Box`**

```kotlin
    if (showOpenPicker) {
        WDTimePickerDialog(
            initialTime = openTime,
            onConfirm   = { openTime = it; showOpenPicker = false },
            onDismiss   = { showOpenPicker = false }
        )
    }
    if (showClosePicker) {
        WDTimePickerDialog(
            initialTime = closeTime,
            onConfirm   = { closeTime = it; showClosePicker = false },
            onDismiss   = { showClosePicker = false }
        )
    }
```

- [ ] **Step 4: Agregar sección de horarios — después del último `TemplateField` y antes del `Button` de guardar**

```kotlin
            // ── Horario de negocio ────────────────────────────────────────────
            Text(
                "Horario de negocio",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WDInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Apertura", fontSize = 12.sp, color = WDInkMute)
                    Box {
                        OutlinedTextField(
                            value = openTime, onValueChange = {},
                            readOnly = true, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Filled.Schedule, null, tint = WDInkMute) }
                        )
                        Box(Modifier.matchParentSize().clickable { showOpenPicker = true })
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cierre", fontSize = 12.sp, color = WDInkMute)
                    Box {
                        OutlinedTextField(
                            value = closeTime, onValueChange = {},
                            readOnly = true, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Filled.Schedule, null, tint = WDInkMute) }
                        )
                        Box(Modifier.matchParentSize().clickable { showClosePicker = true })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Días cerrados", fontSize = 12.sp, color = WDInkMute)
                val dayLabels = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayLabels.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = idx in closedDays,
                            onClick  = {
                                closedDays = if (idx in closedDays)
                                    closedDays - idx else closedDays + idx
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WDPrimary,
                                selectedLabelColor     = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                }
            }

            // ── Precios por zona ──────────────────────────────────────────────
            Text(
                "Precios por zona",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WDInk
            )
            ZonePriceRow("Granada (NEAR)",  nearFee,   { nearFee   = it }, nearBuffer,   { nearBuffer   = it })
            ZonePriceRow("Próxima (CLOSE)", closeFee,  { closeFee  = it }, closeBuffer,  { closeBuffer  = it })
            ZonePriceRow("Media (MEDIUM)",  mediumFee, { mediumFee = it }, mediumBuffer, { mediumBuffer = it })
            ZonePriceRow("Lejos (FAR)",     farFee,    { farFee    = it }, farBuffer,    { farBuffer    = it })
```

Agregar al final del archivo (fuera del `SettingsScreen` pero dentro del mismo archivo, después de `TemplateField`):

```kotlin
@Composable
private fun ZonePriceRow(
    label: String,
    fee: String,   onFeeChange: (String) -> Unit,
    buf: String,   onBufChange: (String) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontSize = 12.sp, color = WDInkMute, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = fee, onValueChange = onFeeChange,
            label = { Text("$") },
            modifier = Modifier.widthIn(min = 80.dp, max = 80.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        )
        OutlinedTextField(
            value = buf, onValueChange = onBufChange,
            label = { Text("min") },
            modifier = Modifier.widthIn(min = 70.dp, max = 70.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        )
    }
}

- [ ] **Step 5: Actualizar el Button de guardar para incluir businessHours y zonePrices**

Reemplazar `onClick` del botón existente:

```kotlin
                    onClick = {
                        vm.updateTemplates(welcome, confirm, reschedule)
                        vm.updateBusinessHours(
                            BusinessHours(
                                openTime   = openTime,
                                closeTime  = closeTime,
                                closedDays = closedDays.toList()
                            )
                        )
                        vm.updateZonePrices(
                            ZonePrices(
                                nearFee      = nearFee.toDoubleOrNull()   ?: 0.0,
                                nearBuffer   = nearBuffer.toIntOrNull()   ?: 0,
                                closeFee     = closeFee.toDoubleOrNull()  ?: 50.0,
                                closeBuffer  = closeBuffer.toIntOrNull()  ?: 10,
                                mediumFee    = mediumFee.toDoubleOrNull() ?: 90.0,
                                mediumBuffer = mediumBuffer.toIntOrNull() ?: 25,
                                farFee       = farFee.toDoubleOrNull()    ?: 120.0,
                                farBuffer    = farBuffer.toIntOrNull()    ?: 40
                            )
                        )
                        onBack()
                    },
```

- [ ] **Step 6: Compilar**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
```

Corregir cualquier import faltante según los mensajes de error. Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/SettingsScreen.kt
git commit -m "feat: add business hours and zone prices configuration to Settings"
```

---

### Task 10: AgendaScreen — iconos HOME/BASE y cargo de traslado en citas

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/ui/screens/AgendaScreen.kt`

- [ ] **Step 1: Agregar import de ServiceLocation**

```kotlin
import com.washdog.app.data.models.ServiceLocation
```

- [ ] **Step 2: Actualizar el subtítulo en AppointmentRow**

Reemplazar el `Text` del subtítulo (el que muestra `"${appt.petName} · ${appt.service}"`) con:

```kotlin
                Text(
                    text = buildString {
                        append(appt.petName)
                        append(" · ")
                        append(appt.service)
                        append("  ")
                        append(if (appt.serviceLocation == ServiceLocation.HOME) "🏠" else "🏪")
                        if (appt.travelFee > 0) append(" +\$${appt.travelFee.toInt()}")
                    },
                    fontSize = 12.sp,
                    color    = WDInkMute
                )
```

- [ ] **Step 3: Compilar y correr todos los tests**

```powershell
cd washdog-native
.\gradlew compileDebugKotlin
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`, todos los tests PASSED.

- [ ] **Step 4: Build APK final de verificación**

```powershell
cd washdog-native
.\gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` y APK generado en `app/build/outputs/apk/debug/`.

- [ ] **Step 5: Commit final**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/AgendaScreen.kt
git commit -m "feat: show HOME/BASE icon and travel fee in agenda appointment rows"
```

---

## Verificación post-implementación

| Escenario | Pasos | Resultado esperado |
|-----------|-------|-------------------|
| Cliente con "Privada Granada" | Crear cliente con esa dirección | Pill verde "Granada · $0" |
| Cliente con "Privada Ensenada" | Crear cliente | Pill azul "Próxima · $50" |
| Crear cita cliente lejos | Seleccionar cliente FAR | Card roja con opciones 🏠/$120 y 🏪/$0 |
| Seleccionar BASE | Tap en "Trae a la base" | Fee = $0, buffer = 0 en cita guardada |
| Hora fuera de horario | Ingresar hora 22:00 | Warning amarillo visible |
| Cita en agenda | Ver lista de citas | Subtítulo muestra 🏠 o 🏪 + cargo |
| Cambiar precios en Settings | Guardar $200 para FAR | Nueva cita FAR muestra $200 |
