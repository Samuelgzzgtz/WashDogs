# Washdog Auth + Cloud Sync + Contact Picker — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar login con Supabase (email/password), sincronización bidireccional DataStore↔Supabase y selector de contactos de Android en las pantallas de cliente.

**Architecture:** DataStore sigue siendo la caché local para soporte offline. En cada mutación, se hace upsert a Supabase en background (fire-and-forget). Al primer login se descarga la data de Supabase si existe, o se sube la data local si la nube está vacía.

**Tech Stack:** Kotlin 1.9.24, Jetpack Compose, Supabase Kotlin SDK 2.5.4 (BOM), Ktor Android 2.3.12, kotlinx.serialization 1.7.1, AndroidManifest runtime permissions.

---

## ⚠️ Task 0 (BLOQUEADO): Crear proyecto Supabase + schema

**Condición:** El usuario debe resolver el límite de 2 proyectos gratuitos en Supabase antes de ejecutar este task. Opciones: pausar/eliminar un proyecto existente O crear una segunda organización.

**Una vez resuelto, ejecutar via MCP:**

- [ ] **Step 0.1: Crear proyecto WashDogs en Supabase**

Via MCP `create_project`:
- name: `WashDogs`
- region: `us-east-1`
- organization_id: `dvmwrlphfhsyztdoigmq`

Esperar status `ACTIVE_HEALTHY` verificando con `get_project`.

- [ ] **Step 0.2: Aplicar migration — tabla clients**

Via MCP `apply_migration` (nombre: `create_clients`):
```sql
create table clients (
  id               text     primary key,
  user_id          uuid     references auth.users not null,
  owner_name       text     not null,
  pet_name         text     not null,
  breed            text     not null,
  last_visit       text     not null,
  needs_reschedule boolean  not null default false,
  pet_type         text     not null,
  phone            text     not null default ''
);
alter table clients enable row level security;
create policy "own_clients" on clients
  for all using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
```

- [ ] **Step 0.3: Aplicar migration — tabla appointments**

Via MCP `apply_migration` (nombre: `create_appointments`):
```sql
create table appointments (
  id          text primary key,
  user_id     uuid references auth.users not null,
  client_name text not null,
  pet_name    text not null,
  service     text not null,
  time        text not null,
  end_time    text not null default '',
  date        text not null,
  status      text not null
);
alter table appointments enable row level security;
create policy "own_appointments" on appointments
  for all using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
```

- [ ] **Step 0.4: Aplicar migration — tabla transactions**

Via MCP `apply_migration` (nombre: `create_transactions`):
```sql
create table transactions (
  id        text             primary key,
  user_id   uuid             references auth.users not null,
  type      text             not null,
  amount    double precision not null,
  concept   text             not null,
  detail    text             not null,
  date_time text             not null
);
alter table transactions enable row level security;
create policy "own_transactions" on transactions
  for all using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
```

- [ ] **Step 0.5: Aplicar migration — tabla user_settings**

Via MCP `apply_migration` (nombre: `create_user_settings`):
```sql
create table user_settings (
  user_id        uuid primary key references auth.users,
  tpl_welcome    text not null,
  tpl_confirm    text not null,
  tpl_reschedule text not null
);
alter table user_settings enable row level security;
create policy "own_settings" on user_settings
  for all using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
```

- [ ] **Step 0.6: Obtener URL y anon key**

Via MCP:
- `get_project_url` → copiar URL (formato: `https://XXXXX.supabase.co`)
- `get_publishable_keys` → copiar `anon` key (empieza con `eyJ...`)

- [ ] **Step 0.7: Agregar credenciales a local.properties**

Abrir `washdog-native/local.properties` y agregar al final (NO commitear este archivo):
```
SUPABASE_URL=https://XXXXX.supabase.co
SUPABASE_ANON_KEY=eyJXXXXX...
```

- [ ] **Step 0.8: Commit task 0**

```bash
git add -A
git commit -m "chore: supabase schema migrations applied (task 0)"
```

---

## Task 1: Configurar dependencias Android

**Files:**
- Modify: `washdog-native/gradle/libs.versions.toml`
- Modify: `washdog-native/app/build.gradle.kts`
- Modify: `washdog-native/app/src/main/AndroidManifest.xml`
- Modify: `washdog-native/app/proguard-rules.pro`

- [ ] **Step 1.1: Agregar plugin de serialización a libs.versions.toml**

Reemplazar el contenido completo de `washdog-native/gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.3.2"
kotlin = "1.9.24"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 1.2: Actualizar app/build.gradle.kts**

Reemplazar el contenido completo de `washdog-native/app/build.gradle.kts`:
```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.washdog.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.washdog.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL",      "\"${localProps["SUPABASE_URL"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["SUPABASE_ANON_KEY"] ?: ""}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        disable += setOf(
            "OldTargetApi", "AndroidGradlePluginVersion", "GradleDependency",
            "DiscouragedApi", "UnusedResources", "MissingApplicationIcon"
        )
        abortOnError = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // Supabase
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:2.5.4")
    implementation(supabaseBom)
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 1.3: Agregar permisos a AndroidManifest.xml**

Reemplazar `washdog-native/app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Washdog">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="unspecified"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 1.4: Agregar reglas ProGuard para Supabase/Ktor/Serialization**

Agregar al final de `washdog-native/app/proguard-rules.pro`:
```
# Ktor
-keep class io.ktor.** { *; }
-keepnames class io.ktor.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.washdog.app.**$$serializer { *; }
-keepclassmembers class com.washdog.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.washdog.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
```

- [ ] **Step 1.5: Verificar que el proyecto compila**

```
cd washdog-native
.\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`. Si falla por Supabase (credenciales vacías), es normal — continúa.

- [ ] **Step 1.6: Commit task 1**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/proguard-rules.pro
git commit -m "build: add Supabase SDK, serialization plugin, INTERNET+READ_CONTACTS permissions"
```

---

## Task 2: DTOs + WashdogSupabase singleton

**Files:**
- Create: `app/src/main/java/com/washdog/app/data/SupabaseModels.kt`
- Create: `app/src/main/java/com/washdog/app/data/WashdogSupabase.kt`
- Create: `app/src/test/java/com/washdog/app/SupabaseModelsTest.kt`

- [ ] **Step 2.1: Crear SupabaseModels.kt**

Crear `app/src/main/java/com/washdog/app/data/SupabaseModels.kt`:
```kotlin
package com.washdog.app.data

import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientRow(
    val id: String,
    @SerialName("user_id")          val userId: String,
    @SerialName("owner_name")       val ownerName: String,
    @SerialName("pet_name")         val petName: String,
    val breed: String,
    @SerialName("last_visit")       val lastVisit: String,
    @SerialName("needs_reschedule") val needsReschedule: Boolean,
    @SerialName("pet_type")         val petType: String,
    val phone: String = ""
)

@Serializable
data class AppointmentRow(
    val id: String,
    @SerialName("user_id")     val userId: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("pet_name")    val petName: String,
    val service: String,
    val time: String,
    @SerialName("end_time")    val endTime: String = "",
    val date: String,
    val status: String
)

@Serializable
data class TransactionRow(
    val id: String,
    @SerialName("user_id")    val userId: String,
    val type: String,
    val amount: Double,
    val concept: String,
    val detail: String,
    @SerialName("date_time")  val dateTime: String
)

@Serializable
data class UserSettingsRow(
    @SerialName("user_id")        val userId: String,
    @SerialName("tpl_welcome")    val tplWelcome: String,
    @SerialName("tpl_confirm")    val tplConfirm: String,
    @SerialName("tpl_reschedule") val tplReschedule: String
)

// ── Mappers ──────────────────────────────────────────────────────────────────

fun Client.toRow(userId: String) = ClientRow(
    id = id, userId = userId, ownerName = ownerName, petName = petName,
    breed = breed, lastVisit = lastVisit, needsReschedule = needsReschedule,
    petType = petType.name, phone = phone
)

fun ClientRow.toClient() = Client(
    id = id, ownerName = ownerName, petName = petName, breed = breed,
    lastVisit = lastVisit, needsReschedule = needsReschedule,
    petType = PetType.valueOf(petType), phone = phone
)

fun Appointment.toRow(userId: String) = AppointmentRow(
    id = id, userId = userId, clientName = clientName, petName = petName,
    service = service, time = time, endTime = endTime, date = date,
    status = status.name
)

fun AppointmentRow.toAppointment() = Appointment(
    id = id, clientName = clientName, petName = petName, service = service,
    time = time, endTime = endTime, date = date,
    status = ApptStatus.valueOf(status)
)

fun Transaction.toRow(userId: String) = TransactionRow(
    id = id, userId = userId, type = type.name,
    amount = amount, concept = concept, detail = detail, dateTime = dateTime
)

fun TransactionRow.toTransaction() = Transaction(
    id = id, type = TxType.valueOf(type), amount = amount,
    concept = concept, detail = detail, dateTime = dateTime
)
```

- [ ] **Step 2.2: Escribir tests de los mappers**

Crear `app/src/test/java/com/washdog/app/SupabaseModelsTest.kt`:
```kotlin
package com.washdog.app

import com.washdog.app.data.ClientRow
import com.washdog.app.data.AppointmentRow
import com.washdog.app.data.TransactionRow
import com.washdog.app.data.toClient
import com.washdog.app.data.toAppointment
import com.washdog.app.data.toTransaction
import com.washdog.app.data.toRow
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseModelsTest {

    @Test
    fun `Client toRow then toClient roundtrip`() {
        val client = Client("1", "Ana López", "Mochi", "Poodle", "2026-05-01",
            false, PetType.DOG, "+525512345678")
        val row = client.toRow("user-abc")
        assertEquals("1", row.id)
        assertEquals("user-abc", row.userId)
        assertEquals("DOG", row.petType)
        val restored = row.toClient()
        assertEquals(client, restored)
    }

    @Test
    fun `Appointment toRow then toAppointment roundtrip`() {
        val appt = Appointment("2", "Ana López", "Mochi", "Baño+Corte",
            "10:00", "2026-05-25", ApptStatus.SCHEDULED, "11:00")
        val row = appt.toRow("user-abc")
        assertEquals("SCHEDULED", row.status)
        assertEquals("11:00", row.endTime)
        val restored = row.toAppointment()
        assertEquals(appt, restored)
    }

    @Test
    fun `Transaction toRow then toTransaction roundtrip`() {
        val tx = Transaction("3", TxType.IN, 450.0, "Baño+Corte", "Luna", "2026-05-25T10:20")
        val row = tx.toRow("user-abc")
        assertEquals("IN", row.type)
        val restored = row.toTransaction()
        assertEquals(tx, restored)
    }
}
```

- [ ] **Step 2.3: Correr tests**

```
.\gradlew test --tests "com.washdog.app.SupabaseModelsTest"
```

Esperado: `3 tests completed, 0 failures`

- [ ] **Step 2.4: Crear WashdogSupabase.kt**

Crear `app/src/main/java/com/washdog/app/data/WashdogSupabase.kt`:
```kotlin
package com.washdog.app.data

import com.washdog.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object WashdogSupabase {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl  = BuildConfig.SUPABASE_URL,
            supabaseKey  = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
```

- [ ] **Step 2.5: Commit task 2**

```bash
git add app/src/main/java/com/washdog/app/data/SupabaseModels.kt \
        app/src/main/java/com/washdog/app/data/WashdogSupabase.kt \
        app/src/test/java/com/washdog/app/SupabaseModelsTest.kt
git commit -m "feat: Supabase DTO models, mappers y singleton client"
```

---

## Task 3: AuthRepository

**Files:**
- Create: `app/src/main/java/com/washdog/app/data/AuthRepository.kt`

- [ ] **Step 3.1: Crear AuthRepository.kt**

Crear `app/src/main/java/com/washdog/app/data/AuthRepository.kt`:
```kotlin
package com.washdog.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class AuthRepository {

    private val auth get() = WashdogSupabase.client.auth

    fun hasActiveSession(): Boolean = auth.currentSessionOrNull() != null

    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    suspend fun login(email: String, password: String) {
        auth.signInWith(Email) {
            this.email    = email
            this.password = password
        }
    }

    suspend fun logout() {
        auth.signOut()
    }
}
```

- [ ] **Step 3.2: Commit task 3**

```bash
git add app/src/main/java/com/washdog/app/data/AuthRepository.kt
git commit -m "feat: AuthRepository — login, logout, session check"
```

---

## Task 4: LoginScreen

**Files:**
- Create: `app/src/main/java/com/washdog/app/ui/screens/LoginScreen.kt`

- [ ] **Step 4.1: Crear LoginScreen.kt**

Crear `app/src/main/java/com/washdog/app/ui/screens/LoginScreen.kt`:
```kotlin
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel) {
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var showPass    by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(WDBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "🐾 WashDog",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = WDPrimary
            )
            Text("Iniciar sesión", fontSize = 18.sp, color = WDInkMute)

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value         = email,
                onValueChange = { email = it; errorMsg = "" },
                label         = { Text("Correo electrónico") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            OutlinedTextField(
                value         = password,
                onValueChange = { password = it; errorMsg = "" },
                label         = { Text("Contraseña") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                visualTransformation = if (showPass) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPass) "Ocultar" else "Mostrar"
                        )
                    }
                }
            )

            if (errorMsg.isNotBlank()) {
                Text(errorMsg, color = Color.Red, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    vm.login(email.trim(), password,
                        onError = { errorMsg = it }
                    )
                },
                enabled  = email.isNotBlank() && password.isNotBlank() && !vm.isLoggingIn,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
            ) {
                if (vm.isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color    = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Iniciar sesión", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
```

- [ ] **Step 4.2: Commit task 4**

```bash
git add app/src/main/java/com/washdog/app/ui/screens/LoginScreen.kt
git commit -m "feat: LoginScreen — email/password con toggle visibilidad y error inline"
```

---

## Task 5: AppRepository — cloud sync

**Files:**
- Modify: `app/src/main/java/com/washdog/app/data/AppRepository.kt`

- [ ] **Step 5.1: Reemplazar AppRepository.kt con versión + cloud sync**

Reemplazar el contenido completo de `app/src/main/java/com/washdog/app/data/AppRepository.kt`:
```kotlin
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

    // ── LOCAL SAVES (fire-and-forget) ─────────────────────────────────────────

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

    // ── CLOUD SYNC ───────────────────────────────────────────────────────────

    private fun userId(): String? =
        WashdogSupabase.client.auth.currentUserOrNull()?.id

    suspend fun syncClientsToCloud(clients: List<Client>) {
        val uid = userId() ?: return
        val rows = clients.map { it.toRow(uid) }
        if (rows.isEmpty()) return
        WashdogSupabase.client.from("clients").upsert(rows) { onConflict = "id" }
    }

    suspend fun syncAppointmentsToCloud(appointments: List<Appointment>) {
        val uid = userId() ?: return
        val rows = appointments.map { it.toRow(uid) }
        if (rows.isEmpty()) return
        WashdogSupabase.client.from("appointments").upsert(rows) { onConflict = "id" }
    }

    suspend fun syncTransactionsToCloud(transactions: List<Transaction>) {
        val uid = userId() ?: return
        val rows = transactions.map { it.toRow(uid) }
        if (rows.isEmpty()) return
        WashdogSupabase.client.from("transactions").upsert(rows) { onConflict = "id" }
    }

    suspend fun syncSettingsToCloud(welcome: String, confirm: String, reschedule: String) {
        val uid = userId() ?: return
        val row = UserSettingsRow(uid, welcome, confirm, reschedule)
        WashdogSupabase.client.from("user_settings").upsert(row) { onConflict = "user_id" }
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
            val settingsRows = WashdogSupabase.client.from("user_settings")
                .select().decodeList<UserSettingsRow>()
            CloudSnapshot(clients, appointments, transactions, settingsRows.firstOrNull())
        } catch (e: Exception) { null }
    }
}

data class CloudSnapshot(
    val clients:      List<Client>,
    val appointments: List<Appointment>,
    val transactions: List<Transaction>,
    val settings:     UserSettingsRow?
)
```

- [ ] **Step 5.2: Compilar para verificar imports**

```
.\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 5.3: Commit task 5**

```bash
git add app/src/main/java/com/washdog/app/data/AppRepository.kt
git commit -m "feat: AppRepository — cloud sync methods con Supabase (upsert, delete, loadFromCloud)"
```

---

## Task 6: AppViewModel — auth state + sync

**Files:**
- Modify: `app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt`

- [ ] **Step 6.1: Reemplazar AppViewModel.kt**

Reemplazar el contenido completo de `app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt`:
```kotlin
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

    var tplWelcome    by mutableStateOf(AppRepository.DEFAULT_WELCOME); private set
    var tplConfirm    by mutableStateOf(AppRepository.DEFAULT_CONFIRM); private set
    var tplReschedule by mutableStateOf(AppRepository.DEFAULT_RESCHEDULE); private set

    var overlapConflict by mutableStateOf<Appointment?>(null); private set

    var isLoggedIn  by mutableStateOf(authRepo.hasActiveSession()); private set
    var isLoggingIn by mutableStateOf(false);                       private set

    init {
        if (isLoggedIn) {
            viewModelScope.launch { loadData() }
        }
    }

    // ── AUTH ──────────────────────────────────────────────────────────────────

    fun login(email: String, password: String, onError: (String) -> Unit) {
        isLoggingIn = true
        viewModelScope.launch {
            try {
                authRepo.login(email, password)
                loadData()
                isLoggedIn  = true
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
        // Intenta cargar desde la nube primero
        val cloud = try { repo.loadFromCloud() } catch (_: Exception) { null }

        if (cloud != null && (cloud.clients.isNotEmpty() || cloud.appointments.isNotEmpty() || cloud.transactions.isNotEmpty())) {
            // La nube tiene data — usarla como fuente de verdad
            transactions.clear(); transactions.addAll(cloud.transactions)
            clients.clear();      clients.addAll(cloud.clients)
            appointments.clear(); appointments.addAll(cloud.appointments)
            repo.saveTransactions(transactions.toList())
            repo.saveClients(clients.toList())
            repo.saveAppointments(appointments.toList())
            if (cloud.settings != null) {
                tplWelcome    = cloud.settings.tplWelcome
                tplConfirm    = cloud.settings.tplConfirm
                tplReschedule = cloud.settings.tplReschedule
                repo.saveTemplates(tplWelcome, tplConfirm, tplReschedule)
            }
            repo.markInitialized()
        } else {
            // Nube vacía o sin conexión — cargar DataStore local
            val initialized = repo.isInitialized()
            val savedTx   = repo.loadTransactions()
            val savedCl   = repo.loadClients()
            val savedAppt = repo.loadAppointments()
            val (w, c, r) = repo.loadTemplates()

            if (!initialized) {
                loadSeedTransactions(); loadSeedClients(); loadSeedAppointments()
                repo.markInitialized()
                // Subir seeds a la nube (primer uso)
                try {
                    repo.syncClientsToCloud(clients.toList())
                    repo.syncAppointmentsToCloud(appointments.toList())
                    repo.syncTransactionsToCloud(transactions.toList())
                } catch (_: Exception) {}
            } else {
                transactions.addAll(savedTx)
                clients.addAll(savedCl)
                appointments.addAll(savedAppt)
            }
            tplWelcome = w; tplConfirm = c; tplReschedule = r
        }
    }

    // ── MUTATIONS + CLOUD SYNC ───────────────────────────────────────────────

    fun addTransaction(t: Transaction) {
        transactions.add(0, t)
        repo.saveTransactions(transactions.toList())
        viewModelScope.launch { try { repo.syncTransactionsToCloud(transactions.toList()) } catch (_: Exception) {} }
    }

    fun deleteTransaction(transactionId: String) {
        transactions.removeAll { it.id == transactionId }
        repo.saveTransactions(transactions.toList())
        viewModelScope.launch {
            try {
                repo.deleteTransactionFromCloud(transactionId)
            } catch (_: Exception) {}
        }
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
        viewModelScope.launch {
            try { repo.deleteAppointmentFromCloud(appointmentId) } catch (_: Exception) {}
        }
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
```

- [ ] **Step 6.2: Compilar**

```
.\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 6.3: Commit task 6**

```bash
git add app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt
git commit -m "feat: AppViewModel — isLoggedIn, isLoggingIn, login(), logout(), cloud sync en cada mutación"
```

---

## Task 7: MainActivity — auth gate

**Files:**
- Modify: `app/src/main/java/com/washdog/app/MainActivity.kt`

- [ ] **Step 7.1: Reemplazar MainActivity.kt**

Reemplazar el contenido completo de `app/src/main/java/com/washdog/app/MainActivity.kt`:
```kotlin
package com.washdog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.washdog.app.ui.components.WDTabBar
import com.washdog.app.ui.screens.AddAppointmentScreen
import com.washdog.app.ui.screens.AddClientScreen
import com.washdog.app.ui.screens.AgendaScreen
import com.washdog.app.ui.screens.ClientsScreen
import com.washdog.app.ui.screens.EditClientScreen
import com.washdog.app.ui.screens.DashboardScreen
import com.washdog.app.ui.screens.ExpenseScreen
import com.washdog.app.ui.screens.IncomeScreen
import com.washdog.app.ui.screens.LoginScreen
import com.washdog.app.ui.screens.ReportsScreen
import com.washdog.app.ui.screens.SettingsScreen
import com.washdog.app.ui.theme.WashdogTheme
import com.washdog.app.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WashdogTheme { WashdogApp(vm) } }
    }
}

@Composable
private fun WashdogApp(vm: AppViewModel) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm = vm)
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
                SettingsScreen(vm) { navController.popBackStack() }
            }
        }
    }
}
```

- [ ] **Step 7.2: Compilar y verificar**

```
.\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 7.3: Commit task 7**

```bash
git add app/src/main/java/com/washdog/app/MainActivity.kt
git commit -m "feat: MainActivity — auth gate, LoginScreen si !isLoggedIn"
```

---

## Task 8: Contact picker — AddClientScreen y EditClientScreen

**Files:**
- Modify: `app/src/main/java/com/washdog/app/ui/screens/AddClientScreen.kt`
- Modify: `app/src/main/java/com/washdog/app/ui/screens/EditClientScreen.kt`

- [ ] **Step 8.1: Leer AddClientScreen completo**

Leer el archivo para ver la estructura exacta antes de modificar:
`app/src/main/java/com/washdog/app/ui/screens/AddClientScreen.kt`

- [ ] **Step 8.2: Agregar contact picker a AddClientScreen**

Agregar los siguientes imports al bloque de imports existente en `AddClientScreen.kt`:
```kotlin
import android.Manifest
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.ui.platform.LocalContext
```

Dentro del composable `AddClientScreen`, después de las declaraciones de `var petType...` y antes del `Box(...)`, agregar:

```kotlin
    val context = LocalContext.current

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val nameCols  = arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID)
        context.contentResolver.query(uri, nameCols, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                val cid  = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                ownerName = name
                val phoneCols = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val phoneSel  = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    phoneCols, phoneSel, arrayOf(cid), null
                )?.use { pc ->
                    if (pc.moveToFirst()) {
                        val raw = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        phoneDigits = raw.filter { it.isDigit() }.takeLast(10)
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) contactPickerLauncher.launch(null)
    }
```

En el campo `ownerName` (`OutlinedTextField`), agregar `trailingIcon`:
```kotlin
trailingIcon = {
    IconButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
        Icon(Icons.Default.Contacts, contentDescription = "Importar contacto")
    }
}
```

- [ ] **Step 8.3: Leer EditClientScreen completo**

Leer el archivo para ver la estructura exacta:
`app/src/main/java/com/washdog/app/ui/screens/EditClientScreen.kt`

- [ ] **Step 8.4: Agregar contact picker a EditClientScreen**

Mismo patrón que AddClientScreen: agregar los mismos imports, los mismos launchers (después de las declaraciones `var ownerName`, `var petName`, etc.), y el mismo `trailingIcon` en el `OutlinedTextField` de `ownerName`.

- [ ] **Step 8.5: Compilar**

```
.\gradlew assembleDebug
```

Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 8.6: Correr todos los tests**

```
.\gradlew test
```

Esperado: todos los tests pasan (incluyendo los de Task 2).

- [ ] **Step 8.7: Commit task 8**

```bash
git add app/src/main/java/com/washdog/app/ui/screens/AddClientScreen.kt \
        app/src/main/java/com/washdog/app/ui/screens/EditClientScreen.kt
git commit -m "feat: contact picker en AddClientScreen y EditClientScreen — importar nombre+teléfono de contactos Android"
```

---

## Credenciales para el login

Una vez el proyecto Supabase esté creado, **crear el usuario** desde Supabase Dashboard:

1. Ir a **Authentication → Users → Add user**
2. Email: el que quieras usar (ej. `washdog@tunegocio.com`)
3. Password: la que elijas

Esas son las credenciales para poner en la pantalla de login de la app.

---

## Resumen de archivos tocados

| Acción  | Archivo |
|---------|---------|
| Create  | `data/SupabaseModels.kt` |
| Create  | `data/WashdogSupabase.kt` |
| Create  | `data/AuthRepository.kt` |
| Create  | `ui/screens/LoginScreen.kt` |
| Create  | `test/.../SupabaseModelsTest.kt` |
| Modify  | `data/AppRepository.kt` |
| Modify  | `viewmodel/AppViewModel.kt` |
| Modify  | `MainActivity.kt` |
| Modify  | `ui/screens/AddClientScreen.kt` |
| Modify  | `ui/screens/EditClientScreen.kt` |
| Modify  | `AndroidManifest.xml` |
| Modify  | `app/build.gradle.kts` |
| Modify  | `gradle/libs.versions.toml` |
| Modify  | `app/proguard-rules.pro` |
