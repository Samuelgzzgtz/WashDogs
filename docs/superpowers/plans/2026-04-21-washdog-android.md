# Washdog Android (Kotlin + Jetpack Compose) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Washdog — a pet-grooming business management Android app — in native Kotlin with Jetpack Compose, targeting Samsung Galaxy Tab (sw600dp+), compilable to `.apk`/`.aab` with `./gradlew assembleRelease`.

**Architecture:** MVVM with a single `AppViewModel` holding in-memory state (`mutableStateListOf`), persisted via DataStore + Gson. Navigation is handled by a `NavHost` in `MainActivity`, with a bottom `WDTabBar`. Each screen receives the ViewModel by reference.

**Tech Stack:** Kotlin 1.9.24 · AGP 8.3.2 · Gradle 8.6 · Jetpack Compose BOM 2024.05.00 · Material3 · Navigation-Compose 2.7.7 · DataStore 1.1.1 · Gson 2.10.1

**Project root:** `C:\Users\PC\Desktop\Regalo mi novia\washdog-native\`

---

## File Map

```
washdog-native/
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar          (binary — must be downloaded)
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml              ← version catalog
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/washdog/app/
│       │   ├── MainActivity.kt          ← NavHost + Scaffold
│       │   ├── ui/theme/
│       │   │   ├── Color.kt             ← WD design tokens
│       │   │   ├── Type.kt              ← typography
│       │   │   └── Theme.kt             ← MaterialTheme wrapper
│       │   ├── ui/components/
│       │   │   ├── WDTabBar.kt          ← bottom navigation
│       │   │   ├── WDIcon.kt            ← icon helpers
│       │   │   ├── WDBalanceCard.kt     ← hero balance card
│       │   │   ├── WDQuickActions.kt    ← income/expense buttons
│       │   │   ├── WDReminderStrip.kt   ← warning strip
│       │   │   └── WDKeypad.kt          ← numeric keypad grid
│       │   ├── ui/screens/
│       │   │   ├── DashboardScreen.kt
│       │   │   ├── AgendaScreen.kt
│       │   │   ├── ClientsScreen.kt
│       │   │   ├── ReportsScreen.kt
│       │   │   ├── IncomeScreen.kt
│       │   │   └── ExpenseScreen.kt
│       │   ├── data/models/
│       │   │   ├── Transaction.kt       ← TxType enum + data class
│       │   │   ├── Client.kt            ← PetType enum + data class
│       │   │   └── Appointment.kt       ← ApptStatus enum + data class
│       │   ├── data/AppRepository.kt    ← DataStore persistence
│       │   └── viewmodel/AppViewModel.kt
│       └── res/
│           ├── values/colors.xml
│           ├── values/strings.xml
│           └── values/themes.xml
├── build.gradle.kts                     ← root build file
└── settings.gradle.kts
```

---

## Task 1: Gradle wrapper + root build files

**Files:**
- Create: `washdog-native/gradle/wrapper/gradle-wrapper.properties`
- Create: `washdog-native/gradle/libs.versions.toml`
- Create: `washdog-native/build.gradle.kts`
- Create: `washdog-native/settings.gradle.kts`

- [ ] **Step 1: Create gradle-wrapper.properties**

```
# washdog-native/gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: Download gradle wrapper jar**

The `gradle-wrapper.jar` binary must exist for `./gradlew` to work. Run from `washdog-native/`:

```bash
# Option A — if gradle is installed on the system:
gradle wrapper --gradle-version 8.6

# Option B — copy from the existing Expo android project:
cp "../washdog/android/gradle/wrapper/gradle-wrapper.jar" gradle/wrapper/gradle-wrapper.jar

# Option C — download via curl:
curl -L "https://github.com/gradle/gradle/raw/v8.6.0/gradle/wrapper/gradle-wrapper.jar" \
     -o gradle/wrapper/gradle-wrapper.jar
```

Verify: `ls -lh gradle/wrapper/gradle-wrapper.jar` → file size ~60 KB.

- [ ] **Step 3: Create libs.versions.toml (version catalog)**

```toml
# washdog-native/gradle/libs.versions.toml
[versions]
agp = "8.3.2"
kotlin = "1.9.24"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 4: Create root build.gradle.kts**

```kotlin
// washdog-native/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 5: Create settings.gradle.kts**

```kotlin
// washdog-native/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "washdog"
include(":app")
```

---

## Task 2: app/build.gradle.kts + proguard

**Files:**
- Create: `washdog-native/app/build.gradle.kts`
- Create: `washdog-native/app/proguard-rules.pro`

- [ ] **Step 1: Create app/build.gradle.kts**

```kotlin
// washdog-native/app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    }
    buildFeatures { compose = true }
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.code.gson:gson:2.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 2: Create proguard-rules.pro**

```pro
# washdog-native/app/proguard-rules.pro
-keep class com.washdog.app.data.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
```

---

## Task 3: AndroidManifest.xml + res/values files

**Files:**
- Create: `washdog-native/app/src/main/AndroidManifest.xml`
- Create: `washdog-native/app/src/main/res/values/colors.xml`
- Create: `washdog-native/app/src/main/res/values/strings.xml`
- Create: `washdog-native/app/src/main/res/values/themes.xml`

- [ ] **Step 1: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- washdog-native/app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
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

- [ ] **Step 2: Create res/values/colors.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- washdog-native/app/src/main/res/values/colors.xml -->
<resources>
    <color name="wd_bg">#FFFAF6F0</color>
    <color name="wd_primary">#FF2D4A3E</color>
    <color name="wd_ink">#FF1F2A24</color>
</resources>
```

- [ ] **Step 3: Create res/values/strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- washdog-native/app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">Washdog</string>
    <string name="tab_dashboard">Inicio</string>
    <string name="tab_agenda">Agenda</string>
    <string name="tab_clients">Clientes</string>
    <string name="tab_reports">Reportes</string>
    <string name="greeting">Hola, Alma</string>
    <string name="balance_title">Balance del mes</string>
    <string name="income_label">Ingresos</string>
    <string name="expense_label">Gastos</string>
    <string name="net_label">Neto</string>
    <string name="add_income">+ Ingreso</string>
    <string name="add_expense">+ Gasto</string>
    <string name="reminder_title">Recordatorio inteligente</string>
    <string name="reminder_text">Camila Rivas lleva 32 días sin visitar · Mochi</string>
    <string name="recent_tx">Transacciones recientes</string>
    <string name="agenda_title">Agenda</string>
    <string name="clients_title">Clientes</string>
    <string name="reports_title">Reportes</string>
    <string name="income_title">Nuevo Ingreso</string>
    <string name="expense_title">Nuevo Gasto</string>
    <string name="search_hint">Buscar cliente…</string>
    <string name="reschedule_section">A reagendar</string>
    <string name="all_clients_section">Todos los clientes</string>
    <string name="reschedule_badge">REAGENDAR</string>
    <string name="save_income">Guardar Ingreso</string>
    <string name="save_expense">Guardar Gasto</string>
    <string name="period_week">Semana</string>
    <string name="period_month">Mes</string>
    <string name="period_year">Año</string>
    <string name="top_services">Servicios más solicitados</string>
    <string name="smart_reminder_badge">✦ Recordatorio inteligente</string>
    <string name="note_hint">Nota (opcional)…</string>
    <string name="new_appointment">Nueva cita</string>
    <string name="today">Hoy</string>
</resources>
```

- [ ] **Step 4: Create res/values/themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- washdog-native/app/src/main/res/values/themes.xml -->
<resources>
    <style name="Theme.Washdog" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

---

## Task 4: Data models

**Files:**
- Create: `…/java/com/washdog/app/data/models/Transaction.kt`
- Create: `…/java/com/washdog/app/data/models/Client.kt`
- Create: `…/java/com/washdog/app/data/models/Appointment.kt`

- [ ] **Step 1: Create Transaction.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/data/models/Transaction.kt
package com.washdog.app.data.models

enum class TxType { IN, OUT }

data class Transaction(
    val id: String,
    val type: TxType,
    val amount: Double,
    val concept: String,
    val detail: String,
    val dateTime: String
)
```

- [ ] **Step 2: Create Client.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/data/models/Client.kt
package com.washdog.app.data.models

enum class PetType { DOG, CAT }

data class Client(
    val id: String,
    val ownerName: String,
    val petName: String,
    val breed: String,
    val lastVisit: String,
    val needsReschedule: Boolean,
    val petType: PetType
)
```

- [ ] **Step 3: Create Appointment.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/data/models/Appointment.kt
package com.washdog.app.data.models

enum class ApptStatus { DONE, NEXT, REMINDER, SCHEDULED }

data class Appointment(
    val id: String,
    val clientName: String,
    val petName: String,
    val service: String,
    val time: String,
    val date: String,
    val status: ApptStatus
)
```

---

## Task 5: AppRepository (DataStore persistence)

**Files:**
- Create: `…/java/com/washdog/app/data/AppRepository.kt`

- [ ] **Step 1: Create AppRepository.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/data/AppRepository.kt
package com.washdog.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.Appointment
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "washdog_prefs")

class AppRepository(private val context: Context) {
    private val gson = Gson()
    private val KEY_TX    = stringPreferencesKey("transactions")
    private val KEY_CL    = stringPreferencesKey("clients")
    private val KEY_APPT  = stringPreferencesKey("appointments")

    suspend fun saveTransactions(list: List<Transaction>) {
        context.dataStore.edit { it[KEY_TX] = gson.toJson(list) }
    }

    suspend fun loadTransactions(): List<Transaction> {
        val prefs = context.dataStore.data.first()
        val json  = prefs[KEY_TX] ?: return emptyList()
        return gson.fromJson(json, object : TypeToken<List<Transaction>>() {}.type)
    }

    suspend fun saveClients(list: List<Client>) {
        context.dataStore.edit { it[KEY_CL] = gson.toJson(list) }
    }

    suspend fun loadClients(): List<Client> {
        val prefs = context.dataStore.data.first()
        val json  = prefs[KEY_CL] ?: return emptyList()
        return gson.fromJson(json, object : TypeToken<List<Client>>() {}.type)
    }

    suspend fun saveAppointments(list: List<Appointment>) {
        context.dataStore.edit { it[KEY_APPT] = gson.toJson(list) }
    }

    suspend fun loadAppointments(): List<Appointment> {
        val prefs = context.dataStore.data.first()
        val json  = prefs[KEY_APPT] ?: return emptyList()
        return gson.fromJson(json, object : TypeToken<List<Appointment>>() {}.type)
    }
}
```

---

## Task 6: AppViewModel

**Files:**
- Create: `…/java/com/washdog/app/viewmodel/AppViewModel.kt`

- [ ] **Step 1: Create AppViewModel.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt
package com.washdog.app.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.washdog.app.data.AppRepository
import com.washdog.app.data.models.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository(application)

    val transactions  = mutableStateListOf<Transaction>()
    val clients       = mutableStateListOf<Client>()
    val appointments  = mutableStateListOf<Appointment>()

    init { viewModelScope.launch { loadData() } }

    private suspend fun loadData() {
        val savedTx    = repo.loadTransactions()
        val savedCl    = repo.loadClients()
        val savedAppt  = repo.loadAppointments()

        if (savedTx.isEmpty()) {
            loadSeedTransactions()
        } else {
            transactions.addAll(savedTx)
        }
        if (savedCl.isEmpty()) {
            loadSeedClients()
        } else {
            clients.addAll(savedCl)
        }
        if (savedAppt.isEmpty()) {
            loadSeedAppointments()
        } else {
            appointments.addAll(savedAppt)
        }
    }

    fun addTransaction(t: Transaction) {
        transactions.add(0, t)
        viewModelScope.launch { repo.saveTransactions(transactions.toList()) }
    }

    fun addClient(c: Client) {
        clients.add(c)
        viewModelScope.launch { repo.saveClients(clients.toList()) }
    }

    fun addAppointment(a: Appointment) {
        appointments.add(a)
        viewModelScope.launch { repo.saveAppointments(appointments.toList()) }
    }

    private fun loadSeedTransactions() {
        transactions.addAll(listOf(
            Transaction("1", TxType.IN,  450.0, "Baño+Corte",  "Luna · Carla M.",    "2026-04-19T10:20"),
            Transaction("2", TxType.IN,  150.0, "Corte uñas",  "Firulais · Luis H.", "2026-04-19T09:00"),
            Transaction("3", TxType.OUT, 380.0, "Insumos",     "Shampoo 2L",         "2026-04-18T15:00"),
            Transaction("4", TxType.IN,  550.0, "Baño gato",   "Niebla · Ana V.",    "2026-04-18T11:00"),
        ))
        viewModelScope.launch { repo.saveTransactions(transactions.toList()) }
    }

    private fun loadSeedClients() {
        clients.addAll(listOf(
            Client("1", "Camila Rivas",     "Mochi",    "Poodle toy", "2026-03-20", true,  PetType.DOG),
            Client("2", "Carla Mendoza",    "Luna",     "Poodle",     "2026-04-19", false, PetType.DOG),
            Client("3", "Ana Valdés",       "Niebla",   "Persa",      "2026-04-18", false, PetType.CAT),
            Client("4", "Luis Hernández",   "Firulais", "Schnauzer",  "2026-04-19", false, PetType.DOG),
        ))
        viewModelScope.launch { repo.saveClients(clients.toList()) }
    }

    private fun loadSeedAppointments() {
        appointments.addAll(listOf(
            Appointment("1", "Carla Mendoza",  "Luna",     "Baño+Corte",  "10:20", "2026-04-19", ApptStatus.DONE),
            Appointment("2", "Luis Hernández", "Firulais", "Corte uñas",  "09:00", "2026-04-19", ApptStatus.DONE),
            Appointment("3", "Camila Rivas",   "Mochi",    "Baño+Corte",  "14:00", "2026-04-21", ApptStatus.NEXT),
            Appointment("4", "Ana Valdés",     "Niebla",   "Baño gato",   "16:00", "2026-04-22", ApptStatus.SCHEDULED),
        ))
        viewModelScope.launch { repo.saveAppointments(appointments.toList()) }
    }

    val totalIncome: Double get() = transactions.filter { it.type == TxType.IN }.sumOf { it.amount }
    val totalExpense: Double get() = transactions.filter { it.type == TxType.OUT }.sumOf { it.amount }
    val netBalance: Double get() = totalIncome - totalExpense
}
```

---

## Task 7: Theme files (Color.kt, Type.kt, Theme.kt)

**Files:**
- Create: `…/java/com/washdog/app/ui/theme/Color.kt`
- Create: `…/java/com/washdog/app/ui/theme/Type.kt`
- Create: `…/java/com/washdog/app/ui/theme/Theme.kt`

- [ ] **Step 1: Create Color.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/theme/Color.kt
package com.washdog.app.ui.theme

import androidx.compose.ui.graphics.Color

val WDBg         = Color(0xFFFAF6F0)
val WDBgAlt      = Color(0xFFF2EDE4)
val WDCard       = Color(0xFFFFFFFF)
val WDInk        = Color(0xFF1F2A24)
val WDInkSoft    = Color(0xFF4B584F)
val WDInkMute    = Color(0xFF8B948E)
val WDInkFaint   = Color(0xFFC8CEC8)
val WDHairline   = Color(0x141F2A24)
val WDPrimary    = Color(0xFF2D4A3E)
val WDPrimaryDk  = Color(0xFF1F352C)
val WDPrimaryTnt = Color(0xFFE7EFE9)
val WDIncome     = Color(0xFFE8A87C)
val WDIncomeDk   = Color(0xFFC78560)
val WDIncomeTnt  = Color(0xFFFBEFE4)
val WDExpense    = Color(0xFFC38D9E)
val WDExpenseDk  = Color(0xFF9F6E7E)
val WDExpenseTnt = Color(0xFFF5E8EC)
val WDWarn       = Color(0xFFD9A441)
val WDWarnTnt    = Color(0xFFF6EAD1)
```

- [ ] **Step 2: Create Type.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/theme/Type.kt
package com.washdog.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WDTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)
```

- [ ] **Step 3: Create Theme.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/theme/Theme.kt
package com.washdog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val WDColorScheme = lightColorScheme(
    primary          = WDPrimary,
    onPrimary        = WDCard,
    primaryContainer = WDPrimaryTnt,
    background       = WDBg,
    surface          = WDCard,
    onBackground     = WDInk,
    onSurface        = WDInk,
    secondary        = WDIncome,
    tertiary         = WDExpense,
    outline          = WDHairline
)

@Composable
fun WashdogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WDColorScheme,
        typography  = WDTypography,
        content     = content
    )
}
```

---

## Task 8: Shared components

**Files:**
- Create: `…/ui/components/WDTabBar.kt`
- Create: `…/ui/components/WDIcon.kt`
- Create: `…/ui/components/WDBalanceCard.kt`
- Create: `…/ui/components/WDQuickActions.kt`
- Create: `…/ui/components/WDReminderStrip.kt`
- Create: `…/ui/components/WDKeypad.kt`

- [ ] **Step 1: Create WDTabBar.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/components/WDTabBar.kt
package com.washdog.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.washdog.app.R
import com.washdog.app.ui.theme.*

data class WDTab(val route: String, val icon: ImageVector, val labelRes: Int)

val wdTabs = listOf(
    WDTab("dashboard", Icons.Filled.Home,        R.string.tab_dashboard),
    WDTab("agenda",    Icons.Filled.DateRange,   R.string.tab_agenda),
    WDTab("clients",   Icons.Filled.People,      R.string.tab_clients),
    WDTab("reports",   Icons.Filled.BarChart,    R.string.tab_reports),
)

@Composable
fun WDTabBar(currentRoute: String, onTabSelected: (String) -> Unit) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val barHeight = if (isTablet) 64.dp else 56.dp
    NavigationBar(
        modifier        = Modifier.height(barHeight),
        containerColor  = WDCard,
        tonalElevation  = 8.dp
    ) {
        wdTabs.forEach { tab ->
            NavigationBarItem(
                selected  = currentRoute == tab.route,
                onClick   = { onTabSelected(tab.route) },
                icon      = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label     = { Text(stringResource(tab.labelRes)) },
                colors    = NavigationBarItemDefaults.colors(
                    selectedIconColor   = WDPrimary,
                    selectedTextColor   = WDPrimary,
                    indicatorColor      = WDPrimaryTnt,
                    unselectedIconColor = WDInkMute,
                    unselectedTextColor = WDInkMute
                )
            )
        }
    }
}
```

- [ ] **Step 2: Create WDIcon.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/components/WDIcon.kt
package com.washdog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WDIconBox(
    icon: ImageVector,
    tint: Color,
    background: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    Box(
        modifier        = Modifier
            .size(size)
            .background(background, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(iconSize)
        )
    }
}
```

- [ ] **Step 3: Create WDBalanceCard.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/components/WDBalanceCard.kt
package com.washdog.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.ui.theme.*

@Composable
fun WDBalanceCard(
    net: Double,
    income: Double,
    expense: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(28.dp),
        colors    = CardDefaults.cardColors(containerColor = WDPrimary),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Icon(
                imageVector        = Icons.Filled.Pets,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.08f),
                modifier           = Modifier
                    .size(140.dp)
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
            Column(Modifier.padding(24.dp)) {
                Text(
                    text       = stringResource(R.string.balance_title),
                    color      = Color.White.copy(alpha = 0.72f),
                    fontSize   = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text       = "$${"%,.2f".format(net)}",
                    color      = Color.White,
                    fontSize   = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    BalanceSubItem(
                        label  = stringResource(R.string.income_label),
                        amount = income,
                        color  = WDIncome
                    )
                    BalanceSubItem(
                        label  = stringResource(R.string.expense_label),
                        amount = expense,
                        color  = WDExpense
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceSubItem(label: String, amount: Double, color: Color) {
    Column {
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(
            text       = "$${"%,.2f".format(amount)}",
            color      = color,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
```

- [ ] **Step 4: Create WDQuickActions.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/components/WDQuickActions.kt
package com.washdog.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.washdog.app.R
import com.washdog.app.ui.theme.*

@Composable
fun WDQuickActions(
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit
) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick  = onAddIncome,
            modifier = Modifier.weight(1f).height(48.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = WDIncome)
        ) { Text(stringResource(R.string.add_income), color = WDCard) }

        Button(
            onClick  = onAddExpense,
            modifier = Modifier.weight(1f).height(48.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = WDExpense)
        ) { Text(stringResource(R.string.add_expense), color = WDCard) }
    }
}
```

- [ ] **Step 5: Create WDReminderStrip.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/components/WDReminderStrip.kt
package com.washdog.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.ui.theme.*

@Composable
fun WDReminderStrip(onClick: () -> Unit = {}) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WDWarnTnt),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Notifications, null, tint = WDWarn, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text     = stringResource(R.string.reminder_title),
                    fontSize = 12.sp,
                    color    = WDWarn
                )
                Text(
                    text     = stringResource(R.string.reminder_text),
                    fontSize = 13.sp,
                    color    = WDInk
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = WDInkMute, modifier = Modifier.size(18.dp))
        }
    }
}
```

- [ ] **Step 6: Create WDKeypad.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/components/WDKeypad.kt
package com.washdog.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val keypadKeys = listOf("1","2","3","4","5","6","7","8","9",".","0","⌫")

@Composable
fun WDKeypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns           = GridCells.Fixed(3),
        modifier          = modifier.height(220.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(keypadKeys, key = { it }) { key ->
            ElevatedButton(
                onClick  = { onKey(key) },
                modifier = Modifier.height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text(key, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
```

---

## Task 9: DashboardScreen

**Files:**
- Create: `…/ui/screens/DashboardScreen.kt`

- [ ] **Step 1: Create DashboardScreen.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/screens/DashboardScreen.kt
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import com.washdog.app.ui.components.*
import com.washdog.app.ui.theme.*
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(vm: AppViewModel, onAddIncome: () -> Unit, onAddExpense: () -> Unit) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 720.dp else Dp.Unspecified

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter),
            contentPadding      = PaddingValues(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") { DashboardHeader(isTablet) }
            item(key = "balance") {
                WDBalanceCard(
                    net     = vm.netBalance,
                    income  = vm.totalIncome,
                    expense = vm.totalExpense
                )
            }
            item(key = "actions") {
                WDQuickActions(onAddIncome = onAddIncome, onAddExpense = onAddExpense)
            }
            item(key = "reminder") { WDReminderStrip() }
            item(key = "tx_header") {
                Text(
                    text       = stringResource(R.string.recent_tx),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = WDInk
                )
            }
            items(vm.transactions, key = { it.id }) { tx ->
                TxRow(tx)
            }
            item(key = "bottom_space") { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DashboardHeader(isTablet: Boolean) {
    val today = LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es")))
        .replaceFirstChar { it.uppercase() }
    Column {
        Text(
            text       = stringResource(R.string.greeting),
            fontSize   = if (isTablet) 26.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            color      = WDInk
        )
        Text(text = today, fontSize = 13.sp, color = WDInkMute)
    }
}

@Composable
private fun TxRow(tx: Transaction) {
    val isIncome = tx.type == TxType.IN
    val icon     = if (isIncome) Icons.Filled.ContentCut else Icons.Filled.Receipt
    val iconBg   = if (isIncome) WDIncomeTnt else WDExpenseTnt
    val iconTint = if (isIncome) WDIncomeDk  else WDExpenseDk
    val amtColor = if (isIncome) WDPrimary   else WDExpenseDk
    val amtSign  = if (isIncome) "+" else "-"

    Column {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WDIconBox(icon = icon, tint = iconTint, background = iconBg)
            Column(Modifier.weight(1f)) {
                Text(tx.concept, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WDInk)
                Text(tx.detail,  fontSize = 12.sp, color = WDInkMute)
            }
            Text(
                text       = "$amtSign$${"%.2f".format(tx.amount)}",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = amtColor
            )
        }
        HorizontalDivider(color = WDHairline, thickness = 1.dp)
    }
}
```

---

## Task 10: AgendaScreen

**Files:**
- Create: `…/ui/screens/AgendaScreen.kt`

- [ ] **Step 1: Create AgendaScreen.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/screens/AgendaScreen.kt
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.ui.theme.*
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDate

@Composable
fun AgendaScreen(vm: AppViewModel) {
    val config      = LocalConfiguration.current
    val isTablet    = config.screenWidthDp >= 600
    val ph          = if (isTablet) 24.dp else 16.dp
    val maxW        = if (isTablet) 720.dp else Dp.Unspecified
    val today       = LocalDate.now()
    var selectedDay by remember { mutableStateOf(today) }

    val days = (-2..10).map { today.plusDays(it.toLong()) }
    val filtered = vm.appointments.filter { it.date == selectedDay.toString() }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter),
            contentPadding      = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "agenda_header") { AgendaHeader(ph = ph) }
            item(key = "day_strip") {
                DayStrip(
                    days        = days,
                    selectedDay = selectedDay,
                    today       = today,
                    ph          = ph,
                    onSelect    = { selectedDay = it }
                )
            }
            item(key = "reminder_card") { AgendaReminderCard(ph = ph) }
            items(filtered, key = { it.id }) { appt ->
                AppointmentRow(appt = appt, ph = ph)
            }
            item(key = "bottom_space") { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AgendaHeader(ph: Dp) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = ph),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text       = stringResource(R.string.agenda_title),
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = WDInk
        )
        FloatingActionButton(
            onClick            = {},
            containerColor     = WDPrimary,
            contentColor       = Color.White,
            modifier           = Modifier.size(42.dp)
        ) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun DayStrip(
    days: List<LocalDate>,
    selectedDay: LocalDate,
    today: LocalDate,
    ph: Dp,
    onSelect: (LocalDate) -> Unit
) {
    LazyRow(
        contentPadding      = PaddingValues(horizontal = ph),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days, key = { it.toString() }) { day ->
            val selected = day == selectedDay
            val isToday  = day == today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    onClick = { onSelect(day) },
                    shape   = RoundedCornerShape(12.dp),
                    color   = if (selected) WDPrimary else Color.Transparent,
                    modifier = Modifier.width(44.dp)
                ) {
                    Column(
                        modifier            = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text     = day.dayOfWeek.name.take(2).lowercase()
                                .replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            color    = if (selected) Color.White else WDInkMute
                        )
                        Text(
                            text       = day.dayOfMonth.toString(),
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (selected) Color.White else WDInk
                        )
                    }
                }
                if (isToday && !selected) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(WDPrimary)
                    )
                } else {
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun AgendaReminderCard(ph: Dp) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = ph),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WDWarnTnt),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier  = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Notifications, null, tint = WDWarn, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.smart_reminder_badge),
                    fontSize = 11.sp, color = WDWarn
                )
                Text(
                    "Camila Rivas · Mochi lleva 32 días sin visitar",
                    fontSize = 13.sp, color = WDInk
                )
            }
        }
        Row(
            modifier              = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Llamar", "WhatsApp", "Agendar").forEach { action ->
                OutlinedButton(
                    onClick  = {},
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(50)
                ) { Text(action, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun AppointmentRow(appt: Appointment, ph: Dp) {
    val borderColor = when (appt.status) {
        ApptStatus.DONE      -> WDPrimary
        ApptStatus.NEXT      -> WDIncome
        ApptStatus.REMINDER  -> WDWarn
        ApptStatus.SCHEDULED -> WDInkFaint
    }
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = ph),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WDCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(borderColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(Modifier.padding(12.dp)) {
                Text(
                    text       = "${appt.time} · ${appt.clientName}",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = WDInk
                )
                Text(
                    text     = "${appt.petName} · ${appt.service}",
                    fontSize = 12.sp,
                    color    = WDInkMute
                )
            }
        }
    }
}
```

---

## Task 11: ClientsScreen

**Files:**
- Create: `…/ui/screens/ClientsScreen.kt`

- [ ] **Step 1: Create ClientsScreen.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/screens/ClientsScreen.kt
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.ui.components.WDIconBox
import com.washdog.app.ui.theme.*
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun ClientsScreen(vm: AppViewModel) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 720.dp else Dp.Unspecified
    var query    by remember { mutableStateOf("") }

    val filtered   = vm.clients.filter {
        it.ownerName.contains(query, ignoreCase = true) ||
        it.petName.contains(query, ignoreCase = true)
    }
    val reschedule = filtered.filter { it.needsReschedule }
    val rest       = filtered.filter { !it.needsReschedule }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter),
            contentPadding      = PaddingValues(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "clients_header") {
                Text(
                    stringResource(R.string.clients_title),
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = WDInk
                )
            }
            item(key = "search") {
                OutlinedTextField(
                    value         = query,
                    onValueChange = { query = it },
                    placeholder   = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon   = { Icon(Icons.Filled.Search, null) },
                    trailingIcon  = { Icon(Icons.Filled.FilterList, null) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(16.dp),
                    singleLine    = true
                )
            }
            if (reschedule.isNotEmpty()) {
                item(key = "reschedule_label") {
                    Text(
                        stringResource(R.string.reschedule_section),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = WDInkMute
                    )
                }
                items(reschedule, key = { "r_${it.id}" }) { client ->
                    ClientRow(client)
                }
            }
            item(key = "all_label") {
                Text(
                    stringResource(R.string.all_clients_section),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = WDInkMute
                )
            }
            items(rest, key = { "a_${it.id}" }) { client ->
                ClientRow(client)
            }
            item(key = "bottom_space") { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ClientRow(client: Client) {
    val petIcon = if (client.petType == PetType.DOG) Icons.Filled.Pets else Icons.Filled.Favorite
    val iconBg  = if (client.petType == PetType.DOG) WDPrimaryTnt else WDExpenseTnt
    val iconTint = if (client.petType == PetType.DOG) WDPrimary   else WDExpenseDk

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WDCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WDIconBox(icon = petIcon, tint = iconTint, background = iconBg)
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = client.petName,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = WDInk
                    )
                    if (client.needsReschedule) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = WDExpenseTnt
                        ) {
                            Text(
                                text     = stringResource(R.string.reschedule_badge),
                                fontSize = 9.sp,
                                color    = WDExpenseDk,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text     = "${client.ownerName} · ${client.breed}",
                    fontSize = 12.sp,
                    color    = WDInkMute
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = WDInkFaint, modifier = Modifier.size(18.dp))
        }
    }
}
```

---

## Task 12: ReportsScreen

**Files:**
- Create: `…/ui/screens/ReportsScreen.kt`

- [ ] **Step 1: Create ReportsScreen.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/screens/ReportsScreen.kt
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import com.washdog.app.R
import com.washdog.app.ui.theme.*
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun ReportsScreen(vm: AppViewModel) {
    val config      = LocalConfiguration.current
    val isTablet    = config.screenWidthDp >= 600
    val ph          = if (isTablet) 24.dp else 16.dp
    val maxW        = if (isTablet) 720.dp else Dp.Unspecified
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs        = listOf(
        stringResource(R.string.period_week),
        stringResource(R.string.period_month),
        stringResource(R.string.period_year)
    )

    Box(Modifier.fillMaxSize().background(WDBg)) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.reports_title),
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = WDInk
            )
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = WDBgAlt,
                contentColor     = WDPrimary,
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = WDPrimary
                    )
                }
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = idx == selectedTab,
                        onClick  = { selectedTab = idx },
                        text     = {
                            Text(
                                title,
                                color = if (idx == selectedTab) WDPrimary else WDInkMute
                            )
                        }
                    )
                }
            }
            HeroReportCard(income = vm.totalIncome, expense = vm.totalExpense)
            TopServicesCard(vm = vm)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HeroReportCard(income: Double, expense: Double) {
    val weekIncome  = listOf(0.45f, 0.70f, 0.55f, 1.0f)
    val weekExpense = listOf(0.30f, 0.40f, 0.50f, 0.60f)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(22.dp),
        colors    = CardDefaults.cardColors(containerColor = WDCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text       = "$${"%,.2f".format(income - expense)}",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = WDPrimary
            )
            Text(text = "Neto del mes", fontSize = 13.sp, color = WDInkMute)
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                drawBarChart(weekIncome, weekExpense)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(color = WDIncome,  label = "Ingresos")
                LegendItem(color = WDExpense, label = "Gastos")
            }
        }
    }
}

private fun DrawScope.drawBarChart(income: List<Float>, expense: List<Float>) {
    val barW    = size.width / (income.size * 3f)
    val gap     = barW * 0.3f
    val maxH    = size.height - 16f
    val labels  = listOf("S1","S2","S3","S4")
    income.forEachIndexed { i, v ->
        val x = i * (barW * 2f + gap * 2f) + gap
        drawRect(
            color   = android.graphics.Color.parseColor("#E8A87C").let { Color(it) },
            topLeft = Offset(x, maxH * (1 - v)),
            size    = androidx.compose.ui.geometry.Size(barW, maxH * v)
        )
        drawRect(
            color   = android.graphics.Color.parseColor("#C38D9E").let { Color(it) },
            topLeft = Offset(x + barW + gap * 0.5f, maxH * (1 - expense[i])),
            size    = androidx.compose.ui.geometry.Size(barW, maxH * expense[i])
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 11.sp, color = WDInkMute)
    }
}

@Composable
private fun TopServicesCard(vm: AppViewModel) {
    val services = listOf(
        "Baño + Corte" to vm.appointments.count { it.service == "Baño+Corte" }.coerceAtLeast(2),
        "Baño gato"    to vm.appointments.count { it.service == "Baño gato" }.coerceAtLeast(1),
        "Corte uñas"   to vm.appointments.count { it.service == "Corte uñas" }.coerceAtLeast(1),
    )
    val max = services.maxOf { it.second }.coerceAtLeast(1)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(22.dp),
        colors    = CardDefaults.cardColors(containerColor = WDCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.top_services), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WDInk)
            services.forEach { (name, count) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, fontSize = 13.sp, color = WDInkSoft)
                        Text("$count citas", fontSize = 12.sp, color = WDInkMute)
                    }
                    LinearProgressIndicator(
                        progress  = { count.toFloat() / max.toFloat() },
                        modifier  = Modifier.fillMaxWidth().height(6.dp),
                        color     = WDIncome,
                        trackColor = WDBgAlt
                    )
                }
            }
        }
    }
}
```

---

## Task 13: IncomeScreen

**Files:**
- Create: `…/ui/screens/IncomeScreen.kt`

- [ ] **Step 1: Create IncomeScreen.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/screens/IncomeScreen.kt
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import com.washdog.app.ui.components.WDKeypad
import com.washdog.app.ui.theme.*
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDateTime
import java.util.UUID

private val incomeCategories = listOf("Baño+Corte","Baño","Corte","Uñas","Tintura","Otro")

@Composable
fun IncomeScreen(vm: AppViewModel, onBack: () -> Unit) {
    val config      = LocalConfiguration.current
    val isTablet    = config.screenWidthDp >= 600
    val ph          = if (isTablet) 24.dp else 16.dp
    val fs          = if (isTablet) 2.sp else 0.sp
    val maxW        = if (isTablet) 720.dp else Dp.Unspecified
    var amountStr   by remember { mutableStateOf("0") }
    var selectedCat by remember { mutableStateOf(incomeCategories.first()) }
    var note        by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, null, tint = WDInk)
                }
                Text(
                    stringResource(R.string.income_title),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = WDInk
                )
            }
            Text(
                text       = "$${amountStr}",
                fontSize   = (64 + fs.value).sp,
                fontWeight = FontWeight.Bold,
                color      = WDPrimary,
                modifier   = Modifier.align(Alignment.CenterHorizontally)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(incomeCategories, key = { it }) { cat ->
                    FilterChip(
                        selected = cat == selectedCat,
                        onClick  = { selectedCat = cat },
                        label    = { Text(cat) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WDIncome,
                            selectedLabelColor     = Color.White
                        ),
                        shape    = RoundedCornerShape(50)
                    )
                }
            }
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                placeholder   = { Text(stringResource(R.string.note_hint)) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(14.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = WDBgAlt,
                    focusedContainerColor   = WDBgAlt
                )
            )
            WDKeypad(onKey = { key ->
                amountStr = handleKeyInput(amountStr, key)
            })
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            Button(
                onClick  = {
                    if (amount > 0) {
                        vm.addTransaction(
                            Transaction(
                                id       = UUID.randomUUID().toString(),
                                type     = TxType.IN,
                                amount   = amount,
                                concept  = selectedCat,
                                detail   = note.ifBlank { selectedCat },
                                dateTime = LocalDateTime.now().toString()
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = WDIncome)
            ) {
                Text(
                    "${stringResource(R.string.save_income)} · $${"%.2f".format(amount)}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun handleKeyInput(current: String, key: String): String {
    if (key == "⌫") return if (current.length <= 1) "0" else current.dropLast(1)
    if (key == "." && current.contains(".")) return current
    if (current == "0" && key != ".") return key
    return current + key
}
```

---

## Task 14: ExpenseScreen

**Files:**
- Create: `…/ui/screens/ExpenseScreen.kt`

- [ ] **Step 1: Create ExpenseScreen.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/ui/screens/ExpenseScreen.kt
package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import com.washdog.app.ui.components.WDKeypad
import com.washdog.app.ui.theme.*
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDateTime
import java.util.UUID

private val expenseCategories = listOf("Insumos","Equipos","Arriendo","Servicios","Nómina","Otro")

@Composable
fun ExpenseScreen(vm: AppViewModel, onBack: () -> Unit) {
    val config      = LocalConfiguration.current
    val isTablet    = config.screenWidthDp >= 600
    val ph          = if (isTablet) 24.dp else 16.dp
    val fs          = if (isTablet) 2.sp else 0.sp
    val maxW        = if (isTablet) 720.dp else Dp.Unspecified
    var amountStr   by remember { mutableStateOf("0") }
    var selectedCat by remember { mutableStateOf(expenseCategories.first()) }
    var note        by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, null, tint = WDInk)
                }
                Text(
                    stringResource(R.string.expense_title),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = WDInk
                )
            }
            Text(
                text       = "$${amountStr}",
                fontSize   = (64 + fs.value).sp,
                fontWeight = FontWeight.Bold,
                color      = WDExpenseDk,
                modifier   = Modifier.align(Alignment.CenterHorizontally)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(expenseCategories, key = { it }) { cat ->
                    FilterChip(
                        selected = cat == selectedCat,
                        onClick  = { selectedCat = cat },
                        label    = { Text(cat) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WDExpense,
                            selectedLabelColor     = Color.White
                        ),
                        shape    = RoundedCornerShape(50)
                    )
                }
            }
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                placeholder   = { Text(stringResource(R.string.note_hint)) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(14.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = WDBgAlt,
                    focusedContainerColor   = WDBgAlt
                )
            )
            WDKeypad(onKey = { key ->
                amountStr = handleKeyInput(amountStr, key)
            })
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            Button(
                onClick  = {
                    if (amount > 0) {
                        vm.addTransaction(
                            Transaction(
                                id       = UUID.randomUUID().toString(),
                                type     = TxType.OUT,
                                amount   = amount,
                                concept  = selectedCat,
                                detail   = note.ifBlank { selectedCat },
                                dateTime = LocalDateTime.now().toString()
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = WDExpense)
            ) {
                Text(
                    "${stringResource(R.string.save_expense)} · $${"%.2f".format(amount)}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun handleKeyInput(current: String, key: String): String {
    if (key == "⌫") return if (current.length <= 1) "0" else current.dropLast(1)
    if (key == "." && current.contains(".")) return current
    if (current == "0" && key != ".") return key
    return current + key
}
```

---

## Task 15: MainActivity (NavHost + Scaffold)

**Files:**
- Create: `…/java/com/washdog/app/MainActivity.kt`

- [ ] **Step 1: Create MainActivity.kt**

```kotlin
// washdog-native/app/src/main/java/com/washdog/app/MainActivity.kt
package com.washdog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.Scaffold
import com.washdog.app.ui.components.WDTabBar
import com.washdog.app.ui.screens.*
import com.washdog.app.ui.theme.WashdogTheme
import com.washdog.app.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WashdogTheme {
                WashdogApp(vm)
            }
        }
    }
}

@Composable
private fun WashdogApp(vm: AppViewModel) {
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
    ) { _ ->
        NavHost(navController = navController, startDestination = "dashboard") {
            composable("dashboard") {
                DashboardScreen(
                    vm           = vm,
                    onAddIncome  = { navController.navigate("income") },
                    onAddExpense = { navController.navigate("expense") }
                )
            }
            composable("agenda")  { AgendaScreen(vm) }
            composable("clients") { ClientsScreen(vm) }
            composable("reports") { ReportsScreen(vm) }
            composable("income")  { IncomeScreen(vm)  { navController.popBackStack() } }
            composable("expense") { ExpenseScreen(vm) { navController.popBackStack() } }
        }
    }
}
```

---

## Task 16: Gradlew wrapper script + final build

- [ ] **Step 1: Create gradlew shell script** (in `washdog-native/`)

```bash
#!/usr/bin/env sh
# washdog-native/gradlew (Unix/Mac)
APP_NAME="Gradle"
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVACMD" -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
```

- [ ] **Step 2: Copy full gradlew from reference**

The Expo project's Android folder has a valid `gradlew`. Copy it:

```bash
cd "/c/Users/PC/Desktop/Regalo mi novia"
cp washdog/android/gradlew washdog-native/gradlew
cp washdog/android/gradlew.bat washdog-native/gradlew.bat
cp washdog/android/gradle/wrapper/gradle-wrapper.jar washdog-native/gradle/wrapper/gradle-wrapper.jar
chmod +x washdog-native/gradlew
```

- [ ] **Step 3: Compile check**

```bash
cd "/c/Users/PC/Desktop/Regalo mi novia/washdog-native"
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "^.*error:|^.*warning:" | head -30
```

Expected: zero lines output (no errors, no warnings). Fix any error before proceeding.

- [ ] **Step 4: Debug APK build**

```bash
./gradlew assembleDebug 2>&1 | tail -15
```

Expected last line: `BUILD SUCCESSFUL in Xs`

- [ ] **Step 5: Lint**

```bash
./gradlew lint 2>&1 | grep -E "Error|Warning" | head -30
```

- [ ] **Step 6: Release APK**

```bash
./gradlew assembleRelease 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. APK at `app/build/outputs/apk/release/app-release-unsigned.apk`.

- [ ] **Step 7: Print final status**

```
COMPILE:  0 errors  0 warnings
LINT:     0 errors  0 warnings
APK:      app/build/outputs/apk/release/app-release-unsigned.apk ✓
```

---

## Self-Review Checklist

### Spec coverage
- [x] Dashboard: TopAppBar greeting, WDBalanceCard, WDQuickActions, WDReminderStrip, tx list — **Task 9**
- [x] Agenda: DayStrip, ReminderCard with 3 buttons, AppointmentList with status border — **Task 10**
- [x] Clients: SearchBar, reschedule section, all clients, dog/cat icon, badge — **Task 11**
- [x] Reports: TabRow, Hero card with Canvas bar chart, LinearProgressIndicator services — **Task 12**
- [x] Income/Expense: 64sp amount, FilterChips, note field, WDKeypad, CTA button — **Tasks 13-14**
- [x] WDTabBar height 64dp tablet / 56dp phone — **Task 8**
- [x] All colors from Color.kt constants — no hex literals in composables
- [x] maxWidth 720dp + align TopCenter on every screen — Tasks 9–14
- [x] isTablet controls ph and fs — Tasks 9–14
- [x] LazyColumn/LazyRow with key={} — all list items have keys
- [x] Seed data loaded only if storage empty — Task 6
- [x] DataStore + Gson persistence — Task 5
- [x] minSdk 26, targetSdk 34, compileSdk 34 — Task 2
- [x] Version catalog libs.versions.toml — Task 1
- [x] gradle-wrapper.jar copied from Expo android folder — Task 16

### Known edge cases to watch
- `ReportsScreen` uses `android.graphics.Color.parseColor` inside a `DrawScope` — valid since it's called at draw time, not composition time.
- `LinearProgressIndicator` lambda form `progress = { … }` is used (API 26+ safe).
- `handleKeyInput` is defined as a private top-level function in both Income/Expense — no shared state, no duplication issue since each screen file is independent.
- `Dp.Unspecified` used instead of `Dp.Infinity` when not on tablet — `widthIn(max = Dp.Unspecified)` applies no constraint, which is correct.
