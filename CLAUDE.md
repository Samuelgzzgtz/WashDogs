# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Web instalable (PWA):** `washdog-web/`, React + TypeScript + Vite. Es la migración multiplataforma para iPhone, Android y escritorio. Usa Supabase existente como fuente de verdad; Vercel hospeda la web. Setup, despliegue y límites en `README.md`. Desde `washdog-web/`: `npm ci`, `npm test`, `npm run build`, `npm run dev`. Variables públicas en `.env.local` (ver `.env.example`). La migración aditiva es `supabase/migrations/20261023000000_webapp.sql`; no ejecutar el script destructivo antiguo de `docs/`. Los tests validan RLS y triggers con PostgreSQL embebido. CI web en `.github/workflows/web.yml`. Las instrucciones siguientes describen el Android existente.

**WashDogs** — App Android de gestión para peluquerías caninas: clientes, citas, ingresos/gastos y mensajes de WhatsApp con plantillas. Kotlin + Jetpack Compose, arquitectura single-activity. Versión actual `1.3.4` (versionCode 7).

El módulo de código está en `washdog-native/` (paquete `com.washdog.app`). La raíz del repo solo contiene documentación (`SECURITY.md`, `docs/`).

## Setup (obligatorio antes de compilar)

Las credenciales de Supabase NO están en git. Crear `washdog-native/local.properties` con:

```properties
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
```

Se inyectan como `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY` (ver `app/build.gradle.kts`). En CI llegan vía GitHub Secrets. Sin ellas la app compila pero no autentica.

**Firma de release:** el build `release` se firma con una llave estable cuyo keystore y
contraseñas llegan por variables de entorno (`KEYSTORE_FILE`, `STORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`); el CI las inyecta desde GitHub Secrets (`KEYSTORE_BASE64`
se decodifica a un archivo). Sin esas variables (build local), el release queda sin
firmar. Esto permite actualizar el APK sin desinstalar. `isMinifyEnabled` está en `false`
hasta validar proguard en dispositivo.

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
- **`loadData()` — merge last-write-wins + tombstones (v1.3.0, antes "local gana"):** carga local + nube y usa `util/SyncMerge` — `mergeByUpdatedAt` (gana el `updatedAt` ISO más reciente; empate → local) y `applyTombstones` (respeta borrados salvo edición posterior). Luego hace top-up de series recurrentes, backfill de `clientId` (`util/ClientLink`), publica en UI, persiste local y re-sube el merge. En primer arranque real (todo vacío, no inicializado, sin tombstones) siembra demo data.
- **Mutaciones:** cada `add/update/delete` en `AppViewModel` estampa `updatedAt`, actualiza la lista Compose, llama `repo.save*` (persist local, no-suspend, en `saveScope` propio) y lanza el sync a la nube vía `fireSync {}`; si falla marca `pendingSync` (indicador "cambios sin sincronizar" en Dashboard, con reintento). Los borrados registran un `Tombstone` (local + tabla `tombstones`) para propagarse entre dispositivos.

### Caché local particionada por usuario

`washdog_prefs` (DataStore) es compartido por todas las cuentas del dispositivo. Las claves se **namespacean por uid** vía `LocalKeys.scoped(base, uid)` → `"clients_<uid>"` (funciones puras y testeadas en `LocalKeysTest`). Al cambiar de cuenta no hay contaminación cruzada.

`AppRepository.migrateLegacyToCurrentUserIfNeeded()` (llamada al inicio de `loadData()`) migra **una sola vez** los datos legacy sin namespace (pre-v1.1.x) al uid actual y borra las claves legacy; idempotente vía flag global `legacy_migrated`. Garantiza cero pérdida al actualizar.

### Sesión cifrada

`EncryptedSessionManager` (usa `androidx.security:security-crypto` / EncryptedSharedPreferences) persiste el token de Supabase cifrado en disco. Se instala en `WashdogSupabase` solo si `appContext` está fijado; si no, cae al SessionManager por defecto.

### Supabase (backend)

Tablas con RLS por `auth.uid() = user_id`: `clients`, `appointments`, `transactions`, `user_settings`, `tombstones`. Todas con `updated_at` (LWW); `appointments` tiene además `client_id`, `end_time`, `repeat_every_days`, `series_id`. `AppRepository` habla PostgREST directo (`.from(tabla).upsert/select/delete`). Las credenciales van por `local.properties` (nunca en el repo). (Nota: existen 4 tablas — `sessions`, `templates`, `business_hours`, `zone_prices` — que NO usa esta app; parecen de otro servicio y están con RLS sin políticas a propósito.)

### Features v1.3.0

- **Login:** `data/CredentialStore` (EncryptedSharedPreferences) recuerda correo+contraseña; `util/BiometricAuth` (huella/PIN) protege el autocompletado. Logout en `SettingsScreen` conserva las credenciales; "Olvidar datos" las borra.
- **Reagendado recurrente:** `Appointment.repeatEveryDays/seriesId`; `util/Recurrence` (puro) genera ocurrencias en un horizonte de 90 días, con top-up en cada arranque. UI en "Nueva cita" (chips) y "detener recurrencia" en Agenda.
- **Notificaciones:** `work/ReminderWorker` (WorkManager, 1×/día) avisa de clientes con ≥30 días sin visita. Permiso `POST_NOTIFICATIONS`.
- **Lógica pura testeable** en `util/` (Recurrence, SyncMerge, ClientLink, LocalKeys, OverlapChecker) con tests JVM en `app/src/test/`.

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
