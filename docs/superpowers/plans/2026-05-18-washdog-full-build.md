# Washdog Full Build — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir Auth persistente (Supabase), barrera de cierre con duración dinámica, importación masiva de contactos WhatsApp Business, y CI/CD con GitHub Actions.

**Architecture:** `AuthRepository` maneja sesión via Supabase Auth REST con tokens en `EncryptedSharedPreferences`. `ClosingBarrierValidator` y `ServiceDurationEstimator` son objects puros (unit-testables). El importador de contactos parsea `.vcf`/`.csv` en `util/` y los carga via `AppViewModel`. El workflow de CI descarga secretos de GitHub Secrets, ejecuta lint+tests y sube el APK como artefacto.

**Tech Stack:** Supabase Auth REST, `androidx.security:security-crypto:1.0.0`, OkHttp 4.12, Compose, JUnit 4, GitHub Actions.

---

## Mapa de archivos

| Acción | Archivo |
|--------|---------|
| Crear | `data/TokenStorage.kt` — interfaz de almacenamiento de tokens |
| Crear | `data/EncryptedTokenStorage.kt` — impl con EncryptedSharedPreferences |
| Crear | `data/AuthRepository.kt` — sign-in, refresh, sign-out |
| Crear | `ui/screens/LoginScreen.kt` |
| Crear | `util/ServiceDurationEstimator.kt` |
| Crear | `util/ClosingBarrierValidator.kt` |
| Crear | `util/PhoneNormalizer.kt` |
| Crear | `util/VcfParser.kt` — incluye `data class RawContact` |
| Crear | `util/CsvParser.kt` |
| Crear | `ui/screens/ContactImportScreen.kt` |
| Crear | `.github/workflows/build.yml` |
| Modificar | `app/build.gradle.kts` — añadir security-crypto |
| Modificar | `data/models/Appointment.kt` — añadir `durationMinutes` |
| Modificar | `data/AppRepository.kt` — `loadAppointments` ApptRow |
| Modificar | `viewmodel/AppViewModel.kt` — auth state + login/logout |
| Modificar | `MainActivity.kt` — auth gate + ruta `contact_import` |
| Modificar | `ui/screens/AddAppointmentScreen.kt` — stepper + closing barrier |
| Modificar | `ui/screens/SettingsScreen.kt` — botón importar |
| Modificar | `util/WhatsAppHelper.kt` — target WhatsApp Business |
| Crear (tests) | `test/.../data/AuthRepositoryTest.kt` |
| Crear (tests) | `test/.../util/ServiceDurationEstimatorTest.kt` |
| Crear (tests) | `test/.../util/ClosingBarrierValidatorTest.kt` |
| Crear (tests) | `test/.../util/PhoneNormalizerTest.kt` |
| Crear (tests) | `test/.../util/VcfParserTest.kt` |
| Crear (tests) | `test/.../util/CsvParserTest.kt` |

Prefijo para todos los paths de Kotlin: `washdog-native/app/src/main/java/com/washdog/app/`
Prefijo para tests: `washdog-native/app/src/test/java/com/washdog/app/`

---

## Task 1: Dependency + estructura de tests

**Files:**
- Modify: `washdog-native/app/build.gradle.kts`
- Create dir: `washdog-native/app/src/test/java/com/washdog/app/`

- [ ] **Step 1: Añadir security-crypto a build.gradle.kts**

En `washdog-native/app/build.gradle.kts`, dentro del bloque `dependencies { }`, agregar después de la línea `implementation("com.squareup.okhttp3:okhttp:4.12.0")`:

```kotlin
implementation("androidx.security:security-crypto:1.0.0")
```

- [ ] **Step 2: Crear estructura de directorios de tests**

```powershell
New-Item -ItemType Directory -Force "washdog-native\app\src\test\java\com\washdog\app\data"
New-Item -ItemType Directory -Force "washdog-native\app\src\test\java\com\washdog\app\util"
```

- [ ] **Step 3: Verificar que compila**

```powershell
cd washdog-native; .\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add washdog-native/app/build.gradle.kts
git commit -m "build: add security-crypto dependency for encrypted token storage"
```

---

## Task 2: TokenStorage + AuthRepository

**Files:**
- Create: `data/TokenStorage.kt`
- Create: `data/EncryptedTokenStorage.kt`
- Create: `data/AuthRepository.kt`
- Create (test): `test/.../data/InMemoryTokenStorage.kt`
- Create (test): `test/.../data/AuthRepositoryTest.kt`

- [ ] **Step 1: Escribir el test que falla**

Crear `washdog-native/app/src/test/java/com/washdog/app/data/InMemoryTokenStorage.kt`:

```kotlin
package com.washdog.app.data

class InMemoryTokenStorage : TokenStorage {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var expiresAt: Long = 0L

    override fun saveTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.expiresAt = expiresAt
    }
    override fun getAccessToken(): String? = accessToken
    override fun getRefreshToken(): String? = refreshToken
    override fun getExpiresAt(): Long = expiresAt
    override fun clear() { accessToken = null; refreshToken = null; expiresAt = 0L }
}
```

Crear `washdog-native/app/src/test/java/com/washdog/app/data/AuthRepositoryTest.kt`:

```kotlin
package com.washdog.app.data

import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {
    private val storage = InMemoryTokenStorage()
    private val repo = AuthRepository("https://test.supabase.co", "testkey", storage)

    @Test fun `isAuthenticated false when no tokens`() {
        assertFalse(repo.isAuthenticated())
    }

    @Test fun `isAuthenticated true when token not expired`() {
        storage.saveTokens("tok", "ref", System.currentTimeMillis() / 1000 + 3600)
        assertTrue(repo.isAuthenticated())
    }

    @Test fun `isAuthenticated false when token expired`() {
        storage.saveTokens("tok", "ref", System.currentTimeMillis() / 1000 - 1)
        assertFalse(repo.isAuthenticated())
    }

    @Test fun `signOut clears tokens and isAuthenticated returns false`() {
        storage.saveTokens("tok", "ref", System.currentTimeMillis() / 1000 + 3600)
        repo.signOut()
        assertFalse(repo.isAuthenticated())
        assertNull(storage.getRefreshToken())
    }

    @Test fun `getAccessToken returns stored token`() {
        storage.saveTokens("my_token", "ref", System.currentTimeMillis() / 1000 + 3600)
        assertEquals("my_token", repo.getAccessToken())
    }
}
```

- [ ] **Step 2: Ejecutar test para confirmar que falla**

```powershell
cd washdog-native; .\gradlew test
```

Esperado: `FAILED` — `AuthRepository` y `TokenStorage` no existen aún.

- [ ] **Step 3: Crear TokenStorage.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/data/TokenStorage.kt`:

```kotlin
package com.washdog.app.data

interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String, expiresAt: Long)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getExpiresAt(): Long
    fun clear()
}
```

- [ ] **Step 4: Crear AuthRepository.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/data/AuthRepository.kt`:

```kotlin
package com.washdog.app.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AuthRepository(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val tokenStorage: TokenStorage
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    private data class AuthResponse(
        val access_token: String?,
        val refresh_token: String?,
        val expires_in: Int?,
        val error_description: String?
    )

    fun isAuthenticated(): Boolean {
        val exp = tokenStorage.getExpiresAt()
        return exp > 0L && System.currentTimeMillis() / 1000 < exp
    }

    fun getAccessToken(): String? = tokenStorage.getAccessToken()

    fun signOut() = tokenStorage.clear()

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = gson.toJson(mapOf("email" to email, "password" to password))
            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON))
                .build()
            val resp = http.newCall(req).execute()
            val json = resp.body?.string() ?: throw Exception("Sin respuesta del servidor")
            val parsed = gson.fromJson(json, AuthResponse::class.java)
            if (!resp.isSuccessful || parsed.access_token == null) {
                throw Exception(parsed.error_description ?: "Credenciales incorrectas")
            }
            tokenStorage.saveTokens(
                parsed.access_token,
                parsed.refresh_token ?: "",
                System.currentTimeMillis() / 1000 + (parsed.expires_in ?: 3600)
            )
        }
    }

    suspend fun refreshIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (isAuthenticated()) return@withContext true
        val refreshToken = tokenStorage.getRefreshToken()?.takeIf { it.isNotBlank() }
            ?: return@withContext false
        runCatching {
            val body = gson.toJson(mapOf("refresh_token" to refreshToken))
            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON))
                .build()
            val resp = http.newCall(req).execute()
            val json = resp.body?.string() ?: return@runCatching false
            val parsed = gson.fromJson(json, AuthResponse::class.java)
            if (!resp.isSuccessful || parsed.access_token == null) return@runCatching false
            tokenStorage.saveTokens(
                parsed.access_token,
                parsed.refresh_token ?: refreshToken,
                System.currentTimeMillis() / 1000 + (parsed.expires_in ?: 3600)
            )
            true
        }.getOrElse { false }
    }
}
```

- [ ] **Step 5: Crear EncryptedTokenStorage.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/data/EncryptedTokenStorage.kt`:

```kotlin
package com.washdog.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class EncryptedTokenStorage(context: Context) : TokenStorage {
    private val prefs = EncryptedSharedPreferences.create(
        "washdog_auth",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", expiresAt)
            .apply()
    }
    override fun getAccessToken(): String?  = prefs.getString("access_token", null)
    override fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    override fun getExpiresAt(): Long        = prefs.getLong("expires_at", 0L)
    override fun clear() = prefs.edit().clear().apply()
}
```

- [ ] **Step 6: Ejecutar tests para confirmar que pasan**

```powershell
cd washdog-native; .\gradlew test
```

Esperado: `AuthRepositoryTest` — 5 tests PASSED.

- [ ] **Step 7: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/data/TokenStorage.kt \
        washdog-native/app/src/main/java/com/washdog/app/data/AuthRepository.kt \
        washdog-native/app/src/main/java/com/washdog/app/data/EncryptedTokenStorage.kt \
        washdog-native/app/src/test/java/com/washdog/app/data/InMemoryTokenStorage.kt \
        washdog-native/app/src/test/java/com/washdog/app/data/AuthRepositoryTest.kt
git commit -m "feat(auth): add AuthRepository with Supabase Auth REST + EncryptedTokenStorage"
```

---

## Task 3: AppViewModel — integración de Auth

**Files:**
- Modify: `viewmodel/AppViewModel.kt`

- [ ] **Step 1: Añadir campos y métodos de auth al AppViewModel**

En `washdog-native/app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt`:

1. Añadir import al inicio:
```kotlin
import com.washdog.app.data.AuthRepository
import com.washdog.app.data.EncryptedTokenStorage
import com.washdog.app.BuildConfig
```

2. Dentro de la clase `AppViewModel`, después de `private val repo = AppRepository(application)`, añadir:
```kotlin
private val authRepo = AuthRepository(
    BuildConfig.SUPABASE_URL,
    BuildConfig.SUPABASE_ANON_KEY,
    EncryptedTokenStorage(application)
)

var isAuthenticated by mutableStateOf(false)
    private set
var isAuthLoading by mutableStateOf(false)
    private set
var authError by mutableStateOf<String?>(null)
    private set
```

3. Reemplazar el bloque `init` existente:
```kotlin
// ANTES:
init { viewModelScope.launch { loadData() } }

// DESPUÉS:
init {
    viewModelScope.launch {
        isAuthenticated = authRepo.refreshIfNeeded()
        if (isAuthenticated) loadData()
    }
}
```

4. Añadir métodos `login` y `logout` antes de `addTransaction`:
```kotlin
fun login(email: String, password: String) {
    viewModelScope.launch {
        isAuthLoading = true
        authError = null
        authRepo.signIn(email, password)
            .onSuccess {
                isAuthenticated = true
                loadData()
            }
            .onFailure { authError = it.message }
        isAuthLoading = false
    }
}

fun logout() {
    authRepo.signOut()
    isAuthenticated = false
    clients.clear()
    appointments.clear()
    transactions.clear()
    tplWelcome    = AppRepository.DEFAULT_WELCOME
    tplConfirm    = AppRepository.DEFAULT_CONFIRM
    tplReschedule = AppRepository.DEFAULT_RESCHEDULE
}
```

- [ ] **Step 2: Verificar compilación**

```powershell
cd washdog-native; .\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt
git commit -m "feat(auth): add login/logout/isAuthenticated to AppViewModel"
```

---

## Task 4: LoginScreen + MainActivity auth gate

**Files:**
- Create: `ui/screens/LoginScreen.kt`
- Modify: `MainActivity.kt`

- [ ] **Step 1: Crear LoginScreen.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/ui/screens/LoginScreen.kt`:

```kotlin
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.ui.theme.*
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(WDBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Washdog",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = WDPrimary
            )
            Text(
                text = "Estética canina",
                fontSize = 14.sp,
                color = WDInkMute
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            vm.authError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = { vm.login(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank() && !vm.isAuthLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WDPrimary)
            ) {
                if (vm.isAuthLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Iniciar sesión", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Añadir auth gate en MainActivity.kt**

En `washdog-native/app/src/main/java/com/washdog/app/MainActivity.kt`, añadir import:
```kotlin
import com.washdog.app.ui.screens.LoginScreen
```

En la función `WashdogApp(vm: AppViewModel)`, envolver el `Scaffold` existente con la condición de auth. Reemplazar el cuerpo completo de `WashdogApp`:

```kotlin
@Composable
private fun WashdogApp(vm: AppViewModel) {
    if (!vm.isAuthenticated) {
        LoginScreen(vm)
        return
    }

    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route ?: "dashboard"
    val tabRoutes     = setOf("dashboard", "agenda", "clients", "reports")

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                WDTabBar(currentRoute = currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo("dashboard") { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = "dashboard",
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    vm                = vm,
                    onAddIncome       = { navController.navigate("income") },
                    onAddExpense      = { navController.navigate("expense") },
                    onNavigateClients = {
                        navController.navigate("clients") {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
            composable("agenda") {
                AgendaScreen(vm = vm, onAddAppointment = { date ->
                    navController.navigate("add_appointment/$date")
                })
            }
            composable("add_appointment/{date}") { backStackEntry ->
                val date = backStackEntry.arguments?.getString("date") ?: ""
                AddAppointmentScreen(vm = vm, initialDate = date) { navController.popBackStack() }
            }
            composable("clients") {
                ClientsScreen(
                    vm                 = vm,
                    onNavigateSettings = { navController.navigate("settings") },
                    onAddClient        = { navController.navigate("add_client") },
                    onEditClient       = { clientId -> navController.navigate("edit_client/$clientId") }
                )
            }
            composable("add_client") {
                AddClientScreen(vm = vm) { navController.popBackStack() }
            }
            composable("edit_client/{clientId}") { backStackEntry ->
                val clientId = backStackEntry.arguments?.getString("clientId") ?: ""
                EditClientScreen(vm = vm, clientId = clientId) { navController.popBackStack() }
            }
            composable("reports")  { ReportsScreen(vm) }
            composable("income")   { IncomeScreen(vm)   { navController.popBackStack() } }
            composable("expense")  { ExpenseScreen(vm)  { navController.popBackStack() } }
            composable("settings") {
                SettingsScreen(vm = vm, onBack = { navController.popBackStack() }) {
                    navController.navigate("contact_import")
                }
            }
            composable("contact_import") {
                ContactImportScreen(vm = vm) { navController.popBackStack() }
            }
        }
    }
}
```

Añadir al bloque de imports de `MainActivity.kt`:
```kotlin
import com.washdog.app.ui.screens.ContactImportScreen
```

- [ ] **Step 3: Verificar compilación** (ContactImportScreen aún no existe, habrá error — es esperado en este paso)

> **Nota:** El error de compilación por `ContactImportScreen` y el cambio de firma de `SettingsScreen` se resuelven en Tasks 12 y 14 respectivamente. Si quieres compilar ahora, crea stubs temporales o implementa en orden Tasks 4→12→14.

- [ ] **Step 4: Añadir botón de logout en alguna pantalla visible**

En `ClientsScreen.kt` o `DashboardScreen.kt`, en el área del header, añadir un `IconButton` de logout. Por ejemplo, en `DashboardScreen.kt`, buscar el Row del header y añadir al final:

```kotlin
// Dentro del Row del header de DashboardScreen, al final:
Spacer(Modifier.weight(1f))
IconButton(onClick = { vm.logout() }) {
    Icon(
        imageVector = androidx.compose.material.icons.Icons.Default.ExitToApp,
        contentDescription = "Cerrar sesión",
        tint = WDInkMute
    )
}
```

- [ ] **Step 5: Commit (después de que compila con Tasks 12+14)**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/LoginScreen.kt \
        washdog-native/app/src/main/java/com/washdog/app/MainActivity.kt
git commit -m "feat(auth): add LoginScreen and auth gate in MainActivity"
```

---

## Task 5: ServiceDurationEstimator

**Files:**
- Create (test): `test/.../util/ServiceDurationEstimatorTest.kt`
- Create: `util/ServiceDurationEstimator.kt`

- [ ] **Step 1: Escribir el test**

Crear `washdog-native/app/src/test/java/com/washdog/app/util/ServiceDurationEstimatorTest.kt`:

```kotlin
package com.washdog.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceDurationEstimatorTest {

    @Test fun `Jumbo returns 360`() =
        assertEquals(360, ServiceDurationEstimator.suggest("Baño Perro Jumbo"))

    @Test fun `Grande returns 240`() =
        assertEquals(240, ServiceDurationEstimator.suggest("Baño Perro Grande"))

    @Test fun `Mediano returns 120`() =
        assertEquals(120, ServiceDurationEstimator.suggest("Baño Perro Mediano"))

    @Test fun `Pequeño returns 90`() =
        assertEquals(90, ServiceDurationEstimator.suggest("Baño Perro Pequeño"))

    @Test fun `Gato returns 90`() =
        assertEquals(90, ServiceDurationEstimator.suggest("Baño Gato Pelo Corto"))

    @Test fun `unknown service returns 60`() =
        assertEquals(60, ServiceDurationEstimator.suggest("Limpieza de Oídos"))

    @Test fun `case insensitive`() =
        assertEquals(360, ServiceDurationEstimator.suggest("baño perro jumbo"))

    @Test fun `pequeno without accent matches`() =
        assertEquals(90, ServiceDurationEstimator.suggest("Baño Perro Pequeño Toy"))
}
```

- [ ] **Step 2: Ejecutar para confirmar FAIL**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.ServiceDurationEstimatorTest"
```

Esperado: FAIL — `ServiceDurationEstimator` no existe.

- [ ] **Step 3: Implementar ServiceDurationEstimator.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/util/ServiceDurationEstimator.kt`:

```kotlin
package com.washdog.app.util

object ServiceDurationEstimator {
    fun suggest(serviceName: String): Int {
        val n = serviceName.lowercase()
            .replace("á","a").replace("é","e").replace("í","i")
            .replace("ó","o").replace("ú","u").replace("ñ","n")
        return when {
            n.contains("jumbo")                        -> 360
            n.contains("grande")                       -> 240
            n.contains("mediano")                      -> 120
            n.contains("pequen") || n.contains("gato") -> 90
            else                                       -> 60
        }
    }
}
```

- [ ] **Step 4: Ejecutar tests para confirmar PASS**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.ServiceDurationEstimatorTest"
```

Esperado: 8 tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/util/ServiceDurationEstimator.kt \
        washdog-native/app/src/test/java/com/washdog/app/util/ServiceDurationEstimatorTest.kt
git commit -m "feat(scheduling): add ServiceDurationEstimator with TDD"
```

---

## Task 6: ClosingBarrierValidator

**Files:**
- Create (test): `test/.../util/ClosingBarrierValidatorTest.kt`
- Create: `util/ClosingBarrierValidator.kt`

- [ ] **Step 1: Escribir el test**

Crear `washdog-native/app/src/test/java/com/washdog/app/util/ClosingBarrierValidatorTest.kt`:

```kotlin
package com.washdog.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClosingBarrierValidatorTest {

    @Test fun `valid appointment returns 0`() =
        // 10:00 + 120min + 0buffer = 12:00 <= 19:00 → ok
        assertEquals(0, ClosingBarrierValidator.minutesExceeded("10:00", 120, 0, "19:00"))

    @Test fun `appointment ending exactly at close returns 0`() =
        // 17:00 + 120min = 19:00 == 19:00 → ok
        assertEquals(0, ClosingBarrierValidator.minutesExceeded("17:00", 120, 0, "19:00"))

    @Test fun `appointment exceeding by 30 min returns 30`() =
        // 18:00 + 90min = 19:30 > 19:00 → 30 min excess
        assertEquals(30, ClosingBarrierValidator.minutesExceeded("18:00", 90, 0, "19:00"))

    @Test fun `travel buffer included in calculation`() =
        // 17:30 + 60min + 40min buffer = 19:10 > 19:00 → 10 min excess
        assertEquals(10, ClosingBarrierValidator.minutesExceeded("17:30", 60, 40, "19:00"))

    @Test fun `blank start time returns 0`() =
        assertEquals(0, ClosingBarrierValidator.minutesExceeded("", 120, 0, "19:00"))

    @Test fun `service and buffer together exceeding by 1h returns 60`() =
        // 18:00 + 60min + 60min buffer = 20:00 > 19:00 → 60 min excess
        assertEquals(60, ClosingBarrierValidator.minutesExceeded("18:00", 60, 60, "19:00"))
}
```

- [ ] **Step 2: Ejecutar para confirmar FAIL**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.ClosingBarrierValidatorTest"
```

Esperado: FAIL.

- [ ] **Step 3: Implementar ClosingBarrierValidator.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/util/ClosingBarrierValidator.kt`:

```kotlin
package com.washdog.app.util

object ClosingBarrierValidator {
    fun minutesExceeded(
        startTime: String,
        durationMinutes: Int,
        travelBuffer: Int,
        closeTime: String
    ): Int {
        val start = toMinutes(startTime) ?: return 0
        val close = toMinutes(closeTime) ?: return 0
        val end   = start + durationMinutes + travelBuffer
        return maxOf(0, end - close)
    }

    private fun toMinutes(time: String): Int? {
        if (time.isBlank()) return null
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 2) parts[0] * 60 + parts[1] else null
    }
}
```

- [ ] **Step 4: Ejecutar tests para confirmar PASS**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.ClosingBarrierValidatorTest"
```

Esperado: 6 tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/util/ClosingBarrierValidator.kt \
        washdog-native/app/src/test/java/com/washdog/app/util/ClosingBarrierValidatorTest.kt
git commit -m "feat(scheduling): add ClosingBarrierValidator with TDD"
```

---

## Task 7: Appointment model + migración Supabase

**Files:**
- Modify: `data/models/Appointment.kt`
- Modify: `data/AppRepository.kt`

- [ ] **Step 1: Añadir columna en Supabase (manual)**

Abrir el dashboard de Supabase → SQL Editor → ejecutar:

```sql
ALTER TABLE appointments
ADD COLUMN IF NOT EXISTS duration_minutes INTEGER DEFAULT 60;
```

- [ ] **Step 2: Añadir `durationMinutes` al modelo Appointment**

En `washdog-native/app/src/main/java/com/washdog/app/data/models/Appointment.kt`, añadir el campo al final de la data class:

```kotlin
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
    val travelBuffer: Int = 0,
    val durationMinutes: Int = 60         // NUEVO
)
```

- [ ] **Step 3: Actualizar `loadAppointments` en AppRepository**

En `AppRepository.kt`, dentro de `loadAppointments()`, añadir `val durationMinutes: Int?` a `ApptRow` y usarlo en el mapeo:

```kotlin
// Reemplazar el data class ApptRow dentro de loadAppointments():
data class ApptRow(
    val id: String,
    val clientName: String?, val petName: String?,
    val service: String?, val time: String?, val date: String?,
    val status: ApptStatus?, val endTime: String?,
    val serviceLocation: ServiceLocation?,
    val travelFee: Double?, val travelBuffer: Int?,
    val durationMinutes: Int?          // NUEVO
)

// Actualizar el mapeo para incluir durationMinutes:
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
        durationMinutes = r.durationMinutes ?: 60,    // NUEVO
    )
}
```

- [ ] **Step 4: Verificar compilación**

```powershell
cd washdog-native; .\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL` (Gson serializa `durationMinutes` → `duration_minutes` automáticamente con `LOWER_CASE_WITH_UNDERSCORES`).

- [ ] **Step 5: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/data/models/Appointment.kt \
        washdog-native/app/src/main/java/com/washdog/app/data/AppRepository.kt
git commit -m "feat(scheduling): add durationMinutes to Appointment model"
```

---

## Task 8: AddAppointmentScreen — stepper de duración + closing barrier

**Files:**
- Modify: `ui/screens/AddAppointmentScreen.kt`

- [ ] **Step 1: Añadir imports necesarios**

Al inicio de `AddAppointmentScreen.kt`, añadir:

```kotlin
import com.washdog.app.util.ClosingBarrierValidator
import com.washdog.app.util.ServiceDurationEstimator
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
```

- [ ] **Step 2: Añadir estado `durationMinutes` y actualización reactiva al servicio**

Dentro de `AddAppointmentScreen`, después de la declaración `var serviceLocation`:

```kotlin
var durationMinutes by remember { mutableStateOf(60) }

LaunchedEffect(service) {
    if (service.isNotBlank()) durationMinutes = ServiceDurationEstimator.suggest(service)
}
```

- [ ] **Step 3: Añadir cómputo de closing barrier**

Después del `val conflict = vm.overlapConflict`, añadir:

```kotlin
val closingExcess = remember(time, durationMinutes, serviceLocation, selectedClient, vm.businessHours, vm.zonePrices) {
    if (time.isBlank()) 0
    else {
        val buffer = if (selectedClient != null)
            ProximityEngine.travelBuffer(selectedClient!!.zone, serviceLocation, vm.zonePrices)
        else 0
        ClosingBarrierValidator.minutesExceeded(
            startTime       = time,
            durationMinutes = durationMinutes,
            travelBuffer    = buffer,
            closeTime       = vm.businessHours.closeTime
        )
    }
}
```

- [ ] **Step 4: Añadir item de stepper de duración en el LazyColumn**

En el `LazyColumn`, después del `item(key = "end_time_row")` y antes del `if (conflict != null)`, añadir:

```kotlin
item(key = "duration_stepper") {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Duración estimada",
            fontSize = 13.sp,
            color = WDInkMute,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { if (durationMinutes > 15) durationMinutes -= 15 },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.Remove, null, tint = WDPrimary)
        }
        Text(
            text = "${durationMinutes}min",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = WDInk,
            modifier = Modifier.widthIn(min = 64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(
            onClick = { durationMinutes += 15 },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.Add, null, tint = WDPrimary)
        }
    }
}
```

- [ ] **Step 5: Añadir tarjeta de error de closing barrier**

Después del `if (conflict != null)` block, añadir:

```kotlin
if (closingExcess > 0) {
    item(key = "closing_barrier_error") {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(10.dp),
            colors   = CardDefaults.cardColors(containerColor = WDExpenseTnt)
        ) {
            Text(
                text = "El servicio excede el horario de cierre (${vm.businessHours.closeTime}) " +
                       "por $closingExcess min. Ajusta la hora o la duración.",
                fontSize = 12.sp,
                color    = WDExpenseDk,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
```

- [ ] **Step 6: Actualizar el botón Guardar para incluir durationMinutes y bloquear si hay closingExcess**

Reemplazar el bloque `item(key = "save_btn")` completo:

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
            if (client != null && service.isNotBlank() && time.isNotBlank() && dateValid
                && conflict == null && closingExcess == 0) {
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
                        travelBuffer    = buffer,
                        durationMinutes = durationMinutes
                    )
                )
                onBack()
            }
        },
        enabled  = conflict == null && closingExcess == 0,
        modifier = Modifier.fillMaxWidth(),
        colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary, contentColor = androidx.compose.ui.graphics.Color.White)
    ) { Text("Guardar cita") }
}
```

- [ ] **Step 7: Actualizar el `LaunchedEffect` para incluir `durationMinutes` en el candidate**

Reemplazar el `LaunchedEffect(time, date, endTime, endTimeEnabled)` existente:

```kotlin
LaunchedEffect(time, date, endTime, endTimeEnabled, durationMinutes) {
    val client = selectedClient ?: vm.clients.firstOrNull {
        "${it.petName} · ${it.ownerName}" == clientQuery
    }
    val candidate = Appointment(
        id              = "",
        clientName      = client?.ownerName ?: "",
        petName         = client?.petName ?: "",
        service         = service,
        time            = time,
        date            = date,
        status          = ApptStatus.SCHEDULED,
        endTime         = if (endTimeEnabled) endTime else "",
        durationMinutes = durationMinutes
    )
    vm.checkOverlap(candidate)
}
```

- [ ] **Step 8: Verificar compilación**

```powershell
cd washdog-native; .\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/AddAppointmentScreen.kt
git commit -m "feat(scheduling): add duration stepper and closing barrier to AddAppointmentScreen"
```

---

## Task 9: PhoneNormalizer

**Files:**
- Create (test): `test/.../util/PhoneNormalizerTest.kt`
- Create: `util/PhoneNormalizer.kt`

- [ ] **Step 1: Escribir el test**

Crear `washdog-native/app/src/test/java/com/washdog/app/util/PhoneNormalizerTest.kt`:

```kotlin
package com.washdog.app.util

import org.junit.Assert.*
import org.junit.Test

class PhoneNormalizerTest {

    @Test fun `10 digits adds +52 prefix`() =
        assertEquals("+525512345678", PhoneNormalizer.normalize("5512345678"))

    @Test fun `12 digits with 52 adds plus`() =
        assertEquals("+525512345678", PhoneNormalizer.normalize("525512345678"))

    @Test fun `plus52 10digit already correct`() =
        assertEquals("+525512345678", PhoneNormalizer.normalize("+525512345678"))

    @Test fun `strips spaces and dashes`() =
        assertEquals("+525512345678", PhoneNormalizer.normalize("+52 55 1234-5678"))

    @Test fun `strips parentheses`() =
        assertEquals("+525512345678", PhoneNormalizer.normalize("(55) 1234 5678"))

    @Test fun `5 digits returns null`() =
        assertNull(PhoneNormalizer.normalize("12345"))

    @Test fun `empty string returns null`() =
        assertNull(PhoneNormalizer.normalize(""))

    @Test fun `9 digits returns null`() =
        assertNull(PhoneNormalizer.normalize("551234567"))
}
```

- [ ] **Step 2: Ejecutar para confirmar FAIL**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.PhoneNormalizerTest"
```

Esperado: FAIL.

- [ ] **Step 3: Implementar PhoneNormalizer.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/util/PhoneNormalizer.kt`:

```kotlin
package com.washdog.app.util

object PhoneNormalizer {
    fun normalize(raw: String): String? {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when (digits.length) {
            10   -> "+52$digits"
            12   -> if (digits.startsWith("52")) "+$digits" else null
            else -> null
        }
    }
}
```

- [ ] **Step 4: Ejecutar tests para confirmar PASS**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.PhoneNormalizerTest"
```

Esperado: 8 tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/util/PhoneNormalizer.kt \
        washdog-native/app/src/test/java/com/washdog/app/util/PhoneNormalizerTest.kt
git commit -m "feat(import): add PhoneNormalizer with TDD"
```

---

## Task 10: VcfParser

**Files:**
- Create (test): `test/.../util/VcfParserTest.kt`
- Create: `util/VcfParser.kt`

- [ ] **Step 1: Escribir el test**

Crear `washdog-native/app/src/test/java/com/washdog/app/util/VcfParserTest.kt`:

```kotlin
package com.washdog.app.util

import org.junit.Assert.*
import org.junit.Test

class VcfParserTest {

    @Test fun `parses single contact`() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Juan Perez
            TEL;TYPE=CELL:5512345678
            END:VCARD
        """.trimIndent()
        val result = VcfParser.parse(vcf)
        assertEquals(1, result.size)
        assertEquals("Juan Perez", result[0].name)
        assertEquals("5512345678", result[0].phone)
    }

    @Test fun `parses multiple contacts`() {
        val vcf = """
            BEGIN:VCARD
            FN:Ana Lopez
            TEL:5511111111
            END:VCARD
            BEGIN:VCARD
            FN:Carlos Rios
            TEL:5522222222
            END:VCARD
        """.trimIndent()
        val result = VcfParser.parse(vcf)
        assertEquals(2, result.size)
        assertEquals("Ana Lopez",   result[0].name)
        assertEquals("Carlos Rios", result[1].name)
    }

    @Test fun `skips contact without name`() {
        val vcf = """
            BEGIN:VCARD
            TEL:5512345678
            END:VCARD
        """.trimIndent()
        assertEquals(0, VcfParser.parse(vcf).size)
    }

    @Test fun `skips contact without phone`() {
        val vcf = """
            BEGIN:VCARD
            FN:Sin Telefono
            END:VCARD
        """.trimIndent()
        assertEquals(0, VcfParser.parse(vcf).size)
    }

    @Test fun `trims whitespace from fields`() {
        val vcf = "BEGIN:VCARD\nFN: Maria García \nTEL: +525512345678 \nEND:VCARD"
        val result = VcfParser.parse(vcf)
        assertEquals("Maria García", result[0].name)
        assertEquals("+525512345678", result[0].phone)
    }
}
```

- [ ] **Step 2: Ejecutar para confirmar FAIL**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.VcfParserTest"
```

Esperado: FAIL.

- [ ] **Step 3: Implementar VcfParser.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/util/VcfParser.kt`:

```kotlin
package com.washdog.app.util

data class RawContact(val name: String, val phone: String)

object VcfParser {
    fun parse(text: String): List<RawContact> {
        val contacts = mutableListOf<RawContact>()
        var name  = ""
        var phone = ""
        for (line in text.lines()) {
            when {
                line.startsWith("FN:")  -> name  = line.removePrefix("FN:").trim()
                line.startsWith("TEL")  -> phone = line.substringAfter(":").trim()
                line.startsWith("END:VCARD") -> {
                    if (name.isNotBlank() && phone.isNotBlank())
                        contacts += RawContact(name, phone)
                    name = ""; phone = ""
                }
            }
        }
        return contacts
    }
}
```

- [ ] **Step 4: Ejecutar tests para confirmar PASS**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.VcfParserTest"
```

Esperado: 5 tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/util/VcfParser.kt \
        washdog-native/app/src/test/java/com/washdog/app/util/VcfParserTest.kt
git commit -m "feat(import): add VcfParser with TDD"
```

---

## Task 11: CsvParser

**Files:**
- Create (test): `test/.../util/CsvParserTest.kt`
- Create: `util/CsvParser.kt`

- [ ] **Step 1: Escribir el test**

Crear `washdog-native/app/src/test/java/com/washdog/app/util/CsvParserTest.kt`:

```kotlin
package com.washdog.app.util

import org.junit.Assert.*
import org.junit.Test

class CsvParserTest {

    @Test fun `parses english headers name and phone`() {
        val csv = "name,phone\nJuan Perez,5512345678"
        val result = CsvParser.parse(csv)
        assertEquals(1, result.size)
        assertEquals("Juan Perez", result[0].name)
        assertEquals("5512345678", result[0].phone)
    }

    @Test fun `parses spanish headers nombre and telefono`() {
        val csv = "nombre,telefono\nAna Lopez,5511111111"
        val result = CsvParser.parse(csv)
        assertEquals(1, result.size)
        assertEquals("Ana Lopez", result[0].name)
    }

    @Test fun `returns empty when headers not found`() {
        assertEquals(0, CsvParser.parse("col1,col2\nfoo,bar").size)
    }

    @Test fun `skips blank rows`() {
        val csv = "name,phone\nJuan,5512345678\n\nAna,5511111111"
        assertEquals(2, CsvParser.parse(csv).size)
    }

    @Test fun `header lookup is case insensitive`() {
        val csv = "Name,Phone\nCarlos,5533333333"
        assertEquals(1, CsvParser.parse(csv).size)
    }

    @Test fun `returns empty for empty string`() {
        assertEquals(0, CsvParser.parse("").size)
    }
}
```

- [ ] **Step 2: Ejecutar para confirmar FAIL**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.CsvParserTest"
```

Esperado: FAIL.

- [ ] **Step 3: Implementar CsvParser.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/util/CsvParser.kt`:

```kotlin
package com.washdog.app.util

object CsvParser {
    private val NAME_HEADERS  = setOf("name","nombre","full name","nombre completo")
    private val PHONE_HEADERS = setOf("phone","telefono","teléfono","tel","mobile","celular","movil","móvil")

    fun parse(text: String): List<RawContact> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header  = lines.first().split(",").map { it.trim().lowercase() }
        val nameIdx  = header.indexOfFirst { it in NAME_HEADERS }
        val phoneIdx = header.indexOfFirst { it in PHONE_HEADERS }
        if (nameIdx < 0 || phoneIdx < 0) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val cols  = line.split(",")
            val name  = cols.getOrNull(nameIdx)?.trim()  ?: return@mapNotNull null
            val phone = cols.getOrNull(phoneIdx)?.trim() ?: return@mapNotNull null
            if (name.isBlank() || phone.isBlank()) null else RawContact(name, phone)
        }
    }
}
```

- [ ] **Step 4: Ejecutar tests para confirmar PASS**

```powershell
cd washdog-native; .\gradlew test --tests "com.washdog.app.util.CsvParserTest"
```

Esperado: 6 tests PASSED.

- [ ] **Step 5: Ejecutar todos los tests**

```powershell
cd washdog-native; .\gradlew test
```

Esperado: Todos los tests PASSED (AuthRepository×5, ServiceDurationEstimator×8, ClosingBarrierValidator×6, PhoneNormalizer×8, VcfParser×5, CsvParser×6 = 38 tests).

- [ ] **Step 6: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/util/CsvParser.kt \
        washdog-native/app/src/test/java/com/washdog/app/util/CsvParserTest.kt
git commit -m "feat(import): add CsvParser with TDD"
```

---

## Task 12: ContactImportScreen

**Files:**
- Create: `ui/screens/ContactImportScreen.kt`

- [ ] **Step 1: Crear ContactImportScreen.kt**

Crear `washdog-native/app/src/main/java/com/washdog/app/ui/screens/ContactImportScreen.kt`:

```kotlin
package com.washdog.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.*
import com.washdog.app.ui.theme.*
import com.washdog.app.util.*
import com.washdog.app.viewmodel.AppViewModel
import java.util.UUID

private data class ParsedContact(
    val name: String,
    val phone: String,
    val isDuplicate: Boolean
)

@Composable
fun ContactImportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var parsed  by remember { mutableStateOf<List<ParsedContact>>(emptyList()) }
    var isParsed by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    val existingPhones = remember(vm.clients) { vm.clients.map { it.phone }.toSet() }

    val launcher = rememberLauncherForActivityResult(GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        parseError = null
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return@rememberLauncherForActivityResult
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val path     = uri.lastPathSegment ?: ""
            val raw = if (mimeType.contains("vcard") || path.endsWith(".vcf", true))
                VcfParser.parse(text) else CsvParser.parse(text)
            if (raw.isEmpty()) {
                parseError = "No se encontraron contactos en el archivo. Verifica el formato."
            } else {
                parsed = raw.mapNotNull { rc ->
                    val phone = PhoneNormalizer.normalize(rc.phone) ?: return@mapNotNull null
                    ParsedContact(rc.name, phone, phone in existingPhones)
                }
                isParsed = true
            }
        } catch (e: Exception) {
            parseError = "Error al leer el archivo: ${e.message}"
        }
    }

    val newContacts = remember(parsed) { parsed.filter { !it.isDuplicate } }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier       = Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null, tint = WDInk)
                    }
                    Text(
                        "Importar contactos",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = WDInk
                    )
                }
            }

            item {
                Button(
                    onClick  = { launcher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
                ) {
                    Icon(Icons.Filled.Upload, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Seleccionar archivo (.vcf / .csv)", color = Color.White)
                }
            }

            parseError?.let { err ->
                item {
                    Card(
                        shape  = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = WDExpenseTnt)
                    ) {
                        Text(err, modifier = Modifier.padding(12.dp), fontSize = 13.sp, color = WDExpenseDk)
                    }
                }
            }

            if (isParsed) {
                item {
                    Card(
                        shape  = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = WDPrimaryTnt)
                    ) {
                        Text(
                            text     = "${newContacts.size} nuevos · ${parsed.size - newContacts.size} duplicados",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color    = WDPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(parsed) { contact ->
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(contact.name,  fontSize = 14.sp, color = WDInk, fontWeight = FontWeight.Medium)
                            Text(contact.phone, fontSize = 12.sp, color = WDInkMute)
                        }
                        if (contact.isDuplicate) {
                            Card(
                                shape  = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = WDBgAlt)
                            ) {
                                Text(
                                    "Ya existe",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color    = WDInkMute
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = WDBgAlt)
                }

                if (newContacts.isNotEmpty()) {
                    item {
                        Button(
                            onClick = {
                                newContacts.forEach { c ->
                                    vm.addClient(
                                        Client(
                                            id              = UUID.randomUUID().toString(),
                                            ownerName       = c.name,
                                            petName         = "",
                                            breed           = "",
                                            phone           = c.phone,
                                            address         = "",
                                            zone            = ServiceZone.FAR,
                                            petType         = PetType.DOG,
                                            lastVisit       = "",
                                            needsReschedule = false
                                        )
                                    )
                                }
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
                        ) {
                            Text(
                                "Importar ${newContacts.size} contactos",
                                color      = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verificar compilación**

```powershell
cd washdog-native; .\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL` (asumiendo que los otros tasks de esta fase están completos).

- [ ] **Step 3: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/ContactImportScreen.kt
git commit -m "feat(import): add ContactImportScreen with vcf/csv picker and preview"
```

---

## Task 13: WhatsApp Business como target de mensajes

**Files:**
- Modify: `util/WhatsAppHelper.kt`

- [ ] **Step 1: Actualizar WhatsAppHelper.send() para target WhatsApp Business**

Reemplazar el contenido completo de `washdog-native/app/src/main/java/com/washdog/app/util/WhatsAppHelper.kt`:

```kotlin
package com.washdog.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object WhatsAppHelper {
    private const val WA_BUSINESS = "com.whatsapp.w4b"
    private const val WA_REGULAR  = "com.whatsapp"

    fun send(context: Context, phone: String, message: String) {
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
        val uri    = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
        val pkg    = if (context.packageManager.getLaunchIntentForPackage(WA_BUSINESS) != null)
                         WA_BUSINESS else WA_REGULAR
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun interpolate(template: String, name: String, fecha: String = "", hora: String = ""): String =
        template
            .replace("{{nombre}}", name)
            .replace("{{fecha}}", fecha)
            .replace("{{hora}}", hora)
}
```

- [ ] **Step 2: Verificar compilación**

```powershell
cd washdog-native; .\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/util/WhatsAppHelper.kt
git commit -m "feat(whatsapp): target WhatsApp Business (com.whatsapp.w4b) with fallback"
```

---

## Task 14: SettingsScreen — botón importar + firma actualizada

**Files:**
- Modify: `ui/screens/SettingsScreen.kt`

- [ ] **Step 1: Actualizar la firma de SettingsScreen para recibir onNavigateImport**

En `washdog-native/app/src/main/java/com/washdog/app/ui/screens/SettingsScreen.kt`:

Reemplazar la firma de la función:
```kotlin
// ANTES:
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {

// DESPUÉS:
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onNavigateImport: () -> Unit = {}) {
```

- [ ] **Step 2: Añadir botón de importar contactos antes del Spacer final**

Dentro del `Column` principal de `SettingsScreen`, antes de `Spacer(Modifier.height(80.dp))`, añadir:

```kotlin
// ── Importar contactos ────────────────────────────────────────────
Text(
    "Contactos",
    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WDInk
)
OutlinedButton(
    onClick  = onNavigateImport,
    modifier = Modifier.fillMaxWidth().height(52.dp),
    shape    = RoundedCornerShape(14.dp)
) {
    Text("Importar desde WhatsApp Business (.vcf / .csv)", color = WDPrimary)
}
```

Añadir import si falta:
```kotlin
import androidx.compose.material3.OutlinedButton
```

- [ ] **Step 3: Verificar compilación completa**

```powershell
cd washdog-native; .\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 4: Ejecutar todos los tests**

```powershell
cd washdog-native; .\gradlew test
```

Esperado: 38 tests PASSED, `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/ui/screens/SettingsScreen.kt
git commit -m "feat(import): add import contacts button to SettingsScreen"
```

---

## Task 15: GitHub Actions — workflow de CI/CD

**Files:**
- Create: `.github/workflows/build.yml`

- [ ] **Step 1: Crear el workflow**

Crear `washdog-native/.github/workflows/build.yml` (el workflow debe estar dentro del módulo porque es donde está el proyecto Gradle):

Realmente, el workflow debe ir en la raíz del repositorio. Crear `.github/workflows/build.yml` en la raíz `C:\Users\PC\Desktop\Regalo mi novia\`:

```powershell
New-Item -ItemType Directory -Force ".github\workflows"
```

Crear `.github/workflows/build.yml`:

```yaml
name: Build APK

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Write local.properties
        run: |
          echo "supabase.url=${{ secrets.SUPABASE_URL }}" > washdog-native/local.properties
          echo "supabase.anonKey=${{ secrets.SUPABASE_ANON_KEY }}" >> washdog-native/local.properties

      - name: Grant execute permission for gradlew
        run: chmod +x washdog-native/gradlew

      - name: Run lint (fail-fast)
        working-directory: washdog-native
        run: ./gradlew lint

      - name: Run unit tests (fail-fast)
        working-directory: washdog-native
        run: ./gradlew test

      - name: Build Debug APK
        working-directory: washdog-native
        run: ./gradlew assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: washdog-debug-${{ github.sha }}
          path: washdog-native/app/build/outputs/apk/debug/app-debug.apk
          retention-days: 7
```

- [ ] **Step 2: Configurar GitHub Secrets**

En el navegador, ir a `https://github.com/Samuelgzzgtz/WashDogs/settings/secrets/actions` y añadir:

| Secret | Valor |
|--------|-------|
| `SUPABASE_URL` | URL del proyecto Supabase (ej. `https://xxxx.supabase.co`) |
| `SUPABASE_ANON_KEY` | Anon key del proyecto Supabase |

- [ ] **Step 3: Verificar que .gitignore incluye local.properties**

El archivo `.gitignore` en la raíz ya incluye `washdog-native/local.properties`. ✅ No requiere cambio.

- [ ] **Step 4: Commit y push**

```bash
git add .github/workflows/build.yml
git commit -m "ci: add GitHub Actions workflow for lint, tests, and APK build"
git push origin main
```

- [ ] **Step 5: Verificar que el workflow corre**

```bash
gh run list --limit 5
```

Esperado: ver el run iniciado. Para ver los logs:
```bash
gh run watch
```

---

## Task 16: Push final y verificación

- [ ] **Step 1: Verificar estado del repo**

```bash
git status
git log --oneline -10
```

Esperado: working tree limpio, 10+ commits en main.

- [ ] **Step 2: Push de todos los cambios**

```bash
git push origin main
```

- [ ] **Step 3: Verificar build exitoso en GitHub Actions**

```bash
gh run list --limit 3
```

Esperado: el último run en `main` con status `completed` y conclusion `success`.

Para descargar el APK generado:
```bash
gh run download --name "washdog-debug-$(git rev-parse HEAD)"
```

---

## Notas de integración

1. **Crear usuario admin en Supabase:** Dashboard → Authentication → Users → "Add user" con email y password. La app no tiene registro en pantalla — el admin es un único usuario creado manualmente.

2. **Columna `duration_minutes` en Supabase:** Ejecutar el SQL de Task 7 antes de instalar la APK en la tablet, o los upserts de appointments fallarán.

3. **Lint disabled rules:** El `build.gradle.kts` ya tiene `abortOnError = true` con reglas deshabilitadas selectivamente. El workflow de CI usará esa misma configuración.

4. **WhatsApp Business en tablet:** La detección usa `getLaunchIntentForPackage("com.whatsapp.w4b")`. Si la tablet tiene WhatsApp Business instalado, todos los mensajes van a ese app. Si no, cae al WhatsApp regular.
