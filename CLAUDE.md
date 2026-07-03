# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**WashDogs** — App Android de gestión para peluquerías caninas: clientes, citas, ingresos/gastos y mensajes de WhatsApp con plantillas. Kotlin + Jetpack Compose, arquitectura single-activity. Versión actual `1.2.0` (versionCode 2).

El módulo de código está en `washdog-native/` (paquete `com.washdog.app`). La raíz del repo solo contiene documentación (`SECURITY.md`, `docs/`).

## Setup (obligatorio antes de compilar)

Las credenciales de Supabase NO están en git. Crear `washdog-native/local.properties` con:

```properties
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
```

Se inyectan como `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY` (ver `app/build.gradle.kts`). En CI llegan vía GitHub Secrets. Sin ellas la app compila pero no autentica.

**Activar el pre-commit hook en cada clon** (bloquea fugas de URLs `*.supabase.co`, JWTs `eyJ...` y project IDs):

```sh
git config core.hooksPath .githooks
```

## Build / Test

JDK fijado en `gradle.properties` (`org.gradle.java.home`). No tocar el `JAVA_HOME` del sistema.

```powershell
# Desde washdog-native/
.\gradlew assembleDebug        # APK debug
.\gradlew assembleRelease      # APK release (minificado; requiere keystore)
.\gradlew lint                 # Lint — abortOnError=true
.\gradlew test                 # Unit tests JVM
.\gradlew connectedAndroidTest # Instrumentados (requiere emulador/dispositivo)
```

**⚠️ El test-worker de Gradle está roto en el entorno de desarrollo local de Windows** (`./gradlew test` falla al lanzar el worker; NO es el código — el CI en Linux corre bien). Los tests son JVM puros (sin framework Android). Workaround local:

```sh
./gradlew compileDebugUnitTestKotlin
# luego correr las clases con java -cp <build/tmp/kotlin-classes + jars del cache> org.junit.runner.JUnitCore <clase>
```

Tests en `app/src/test/`: `LocalKeysTest`, `SupabaseModelsTest`, `OverlapDetectionTest`, `PhoneFormatTest`, `StringUtilsTest`.

## Arquitectura

Single-activity. No hay Room: la persistencia local es **DataStore + Gson (JSON plano)** y la nube es **Supabase** (Postgres + Auth).

```
MainActivity (NavHost)
  └── AppViewModel (AndroidViewModel)  ← estado reactivo (mutableStateListOf / mutableStateOf)
        ├── AppRepository   ← DataStore local (caché) + PostgREST cloud sync
        └── AuthRepository  ← Supabase Auth (email/password, sesión persistida)

WashdogApplication.onCreate() fija WashdogSupabase.appContext (necesario para el EncryptedSessionManager).
WashdogSupabase  ← singleton `object`, cliente Supabase creado lazy con Auth + Postgrest.
```

Modelos en `data/models/`: `Client`, `Appointment`, `Transaction`. Enums: `PetType` (DOG/CAT), `ApptStatus` (DONE/NEXT/REMINDER/SCHEDULED), `TxType` (IN/OUT). DTOs `@Serializable` (`ClientRow`, `AppointmentRow`, `TransactionRow`, `UserSettingsRow`) con mappers en `data/SupabaseModels.kt`.

### Modelo local-first con cloud sync (lo más importante de entender)

**DataStore es la fuente de verdad de lo capturado en el dispositivo; Supabase es sync fire-and-forget.**

- **Auth gate:** al arrancar, `AppViewModel.init` llama `authRepo.awaitInitialization()` (B1: espera a que supabase-kt cargue la sesión cifrada de disco antes de decidir pantalla — leerla síncronamente devolvía `null` por la carrera y forzaba re-login). Si hay sesión, `loadData()`; si no, `LoginScreen`. `sessionLoading` controla el splash.
- **`loadData()` — merge no destructivo (B2):** carga local + nube y hace **unión por `id`** (`mergeById`). **En conflicto de id gana lo LOCAL** para no pisar ediciones que aún no subieron. Luego publica en UI, persiste local y re-sube el merge (los registros que estaban solo en local antes se descartaban). En primer arranque real (todo vacío y no inicializado) siembra demo data.
- **Mutaciones:** cada `add/update/delete` en `AppViewModel` actualiza la lista Compose, llama `repo.save*` (persist local, no-suspend, en `saveScope` propio) y lanza el `repo.sync*ToCloud` (upsert con `onConflict = "id"`) envuelto en try/catch — la nube nunca bloquea ni rompe la UI.

### Caché local particionada por usuario

`washdog_prefs` (DataStore) es compartido por todas las cuentas del dispositivo. Las claves se **namespacean por uid** vía `LocalKeys.scoped(base, uid)` → `"clients_<uid>"` (funciones puras y testeadas en `LocalKeysTest`). Al cambiar de cuenta no hay contaminación cruzada.

`AppRepository.migrateLegacyToCurrentUserIfNeeded()` (llamada al inicio de `loadData()`) migra **una sola vez** los datos legacy sin namespace (pre-v1.1.x) al uid actual y borra las claves legacy; idempotente vía flag global `legacy_migrated`. Garantiza cero pérdida al actualizar.

### Sesión cifrada

`EncryptedSessionManager` (usa `androidx.security:security-crypto` / EncryptedSharedPreferences) persiste el token de Supabase cifrado en disco. Se instala en `WashdogSupabase` solo si `appContext` está fijado; si no, cae al SessionManager por defecto.

### Supabase (backend)

Tablas con RLS por `auth.uid() = user_id`: `clients`, `appointments`, `transactions`, `user_settings`. `AppRepository` habla PostgREST directo (`.from(tabla).upsert/select/delete`). Las credenciales van por `local.properties` (nunca en el repo).

## Navegación (`MainActivity`)

Rutas string en un `NavHost`: `dashboard`, `agenda`, `add_appointment/{date}`, `clients`, `add_client`, `edit_client/{clientId}`, `reports`, `income`, `expense`, `settings`, más `login`. La `WDTabBar` solo aparece en `dashboard`, `agenda`, `clients`, `reports`.

## Convenciones

- Componentes reutilizables con prefijo `WD` en `ui/components/`. Screens en `ui/screens/`.
- IDs de entidades: `String`. Usar `UUID.randomUUID().toString()` para registros nuevos (los seeds usan IDs incrementales).
- Fechas como `String` ISO-8601: `LocalDate.toString()` → `"yyyy-MM-dd"`; `dateTime`/`time` de `Transaction` como `"yyyy-MM-ddTHH:mm"`.
- **Citas ligadas a clientes por `clientName` (String), no por ID** — tenerlo en cuenta al renombrar clientes; `deleteClient` borra en cascada las `Appointment` con `clientName` coincidente.
- `minSdk = 26` (Android 8.0), `compileSdk/targetSdk = 34`. No usar APIs por debajo de API 26.
- **WhatsApp:** `WhatsAppHelper.send()` abre `wa.me` con el mensaje interpolado. Plantillas con tokens `{{nombre}}`, `{{fecha}}`, `{{hora}}`, editables en `SettingsScreen` (defaults en `AppRepository`).
- **Catálogo de servicios:** hardcoded en `data/ServiceCatalog.kt` (`WD_SERVICES`), usado en dropdowns de `IncomeScreen` y `AddAppointmentScreen`.
- **Detección de solapes:** `util/OverlapChecker.findConflict()` (puro, testeado); `AppViewModel.checkOverlap` expone `overlapConflict`.
- Al filtrar el token/credencial ver el runbook en `SECURITY.md` (rotar key, `git filter-repo`, force-push).
