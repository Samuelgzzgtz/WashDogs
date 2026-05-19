# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Washdog** — Android app de gestión para peluquerías caninas. Permite registrar clientes, citas, ingresos/gastos y enviar mensajes de WhatsApp con plantillas personalizables.

El módulo principal está en `washdog-native/`.

## Build

JDK configurado en `gradle.properties` (`org.gradle.java.home`). No modificar `JAVA_HOME` del sistema.

Antes de compilar, `washdog-native/local.properties` debe tener:
```
supabase.url=https://<project>.supabase.co
supabase.anonKey=<anon-key>
```
Estos valores se inyectan como `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.

```powershell
# Desde washdog-native/
.\gradlew assembleDebug        # APK de debug
.\gradlew assembleRelease      # APK de release (minificado)
.\gradlew lint                 # Lint (abortOnError=true)
.\gradlew test                 # Unit tests
.\gradlew connectedAndroidTest # Tests instrumentados (requiere emulador/dispositivo)
```

**Nota Windows:** Si Opera GX está en el PATH con comillas (`"C:\...\Opera GX"`), los tests fallan con `ClassNotFoundException: GX;...`. Solución antes de correr tests:
```powershell
$env:PATH = (($env:PATH -split ";") | Where-Object { $_ -notmatch "Opera" -and $_ -ne "" }) -join ";"
```

## Arquitectura

Arquitectura de una sola actividad, sin Room. Persistencia en **Supabase** vía REST (OkHttp + Gson).

```
MainActivity
  └── AppViewModel (AndroidViewModel)
        └── AppRepository  ← SupabaseDataSource (OkHttp REST)
```

**Modelos:**

| Clase | Campos clave |
|-------|-------------|
| `Client` | `id`, `ownerName`, `petName`, `breed`, `phone`, `address`, `zone: ServiceZone`, `petType: PetType`, `lastVisit: String`, `needsReschedule: Boolean` |
| `Appointment` | `id`, `clientName`, `petName`, `service`, `time`, `date`, `status: ApptStatus`, `endTime`, `serviceLocation: ServiceLocation`, `travelFee: Double`, `travelBuffer: Int`, `durationMinutes: Int` (default 60) |
| `Transaction` | `id`, `type: TxType`, `amount`, `concept`, `detail` (local-only, **no** en Supabase), `dateTime` |
| `BusinessHours` | `openTime`, `closeTime`, `closedDays: List<Int>` (0=Dom) |
| `ZonePrices` | fee + buffer por zona: `near`, `close`, `medium`, `far` |

**Enums:** `PetType` (DOG/CAT), `ApptStatus` (DONE/NEXT/REMINDER/SCHEDULED), `TxType` (IN/OUT), `ServiceZone` (NEAR/CLOSE/MEDIUM/FAR), `ServiceLocation` (HOME/BASE)

**Tablas Supabase:** `clients`, `appointments`, `transactions`, `templates`, `business_hours`, `zone_prices`

**Flujo de datos:**
- `AppViewModel` expone listas reactivas como `mutableStateListOf` (Compose-observable).
- Al arrancar: si no hay datos en Supabase, intenta migrar desde DataStore (one-shot legacy path), luego inserta seed data si sigue vacío.
- Cada mutación llama `repo.save*()` (fire-and-forget en `saveScope` propio de `AppRepository`, no suspend).
- El patrón de sync en `AppRepository`: mantiene `knownIds` sets por entidad; en cada `save*`, borra en Supabase los IDs que desaparecieron del set y hace upsert del resto. Los IDs no-UUID se reemplazan por `UUID.randomUUID()` antes de subir.
- `Transaction.detail` existe solo en memoria; **no** se sube a Supabase (campo excluido explícitamente en `saveTransactions`).
- `clientsToRemind`: clientes con `daysSince(lastVisit) >= 30`, mostrados en `WDReminderStrip`.
- `deleteClient` en cascada: borra también las `Appointment` cuyo `clientName` coincida.

**Utilidades:**
- `ProximityEngine`: detecta `ServiceZone` a partir de keywords en la dirección (NEAR=Granada, CLOSE=Ensenada/Cedral/Ajusco, MEDIUM=Mitras Poniente, FAR=resto). También calcula `travelFee` y `travelBuffer` según zona y `ServiceLocation`.
- `OverlapChecker`: detecta conflictos de horario entre citas del mismo día. Si `endTime` está vacío, asume duración de 60 min.

**Navegación:** `NavHost` con rutas string en `MainActivity`:

| Ruta | Pantalla |
|------|----------|
| `dashboard` | Dashboard |
| `agenda` | Agenda |
| `add_appointment/{date}` | Nueva cita |
| `clients` | Clientes |
| `add_client` | Nuevo cliente |
| `edit_client/{clientId}` | Editar cliente |
| `reports` | Reportes |
| `income` | Ingreso |
| `expense` | Gasto |
| `settings` | Configuración |
| `contact_import` | Importar contactos (.vcf/.csv) |

La `WDTabBar` solo aparece en `dashboard`, `agenda`, `clients`, `reports`.

**WhatsApp:** `WhatsAppHelper.send()` abre `wa.me` con el mensaje interpolado. Las plantillas usan tokens `{{nombre}}`, `{{fecha}}`, `{{hora}}` y se editan en `SettingsScreen`.

**Catálogo de servicios:** Lista hardcoded en `data/ServiceCatalog.kt` (`WD_SERVICES: List<WDService>`). Usada en los dropdowns de `IncomeScreen` y `AddAppointmentScreen`.

## Convenciones

- Componentes reutilizables con prefijo `WD` en `ui/components/`: `WDTabBar`, `WDIcon`, `WDBalanceCard`, `WDQuickActions`, `WDKeypad`, `WDReminderStrip`, `WDMonthCalendar`, `WDTimeline`, `WDTimePicker`, `WDPhoneField`, `WDSearchableDropdown`, `WDZonePill`.
- IDs de entidades: `String`; usar `UUID.randomUUID().toString()` para nuevos registros (los seeds usan incrementales "1","2"… pero `AppRepository` los reemplaza por UUID al subir).
- Fechas como `String` ISO-8601 (`LocalDate.toString()` → `"yyyy-MM-dd"`); `dateTime` de `Transaction` es `"yyyy-MM-ddTHH:mm"`.
- Clientes ligados a citas por `clientName` (String), no por ID — tenerlo en cuenta al renombrar clientes.
- `minSdk = 26` (Android 8.0); `compileSdk/targetSdk = 34`. No usar APIs por debajo de API 26.
- Tests en `src/test/java/.../util/`: 38 pruebas unitarias para `ServiceDurationEstimator`, `ClosingBarrierValidator`, `PhoneNormalizer`, `VcfParser`, `CsvParser` + 5 para `AuthRepository`.
- `SupabaseDataSource.gson` usa `LOWER_CASE_WITH_UNDERSCORES`. Al construir filas manualmente (como en `saveClients`), usar nombres snake_case explícitos en el `mapOf`.
- **Supabase migration pendiente:** `ALTER TABLE appointments ADD COLUMN IF NOT EXISTS duration_minutes INTEGER DEFAULT 60;`
- **Auth:** sesión en `EncryptedSharedPreferences` (`washdog_auth`). `TokenStorage` interface permite tests unitarios sin Android.
- **Scheduling:** `ServiceDurationEstimator.suggest(service)` devuelve minutos default por keyword. `ClosingBarrierValidator.minutesExceeded(start, duration, buffer, closeTime)` retorna exceso en minutos (0 = válido).
- **Contactos:** `VcfParser.parse(text)` y `CsvParser.parse(text)` devuelven `List<RawContact(name, phone)>`. `PhoneNormalizer.normalize(raw)` normaliza a `+52XXXXXXXXXX` o null si inválido.
- **WhatsApp:** `WhatsAppHelper.send()` apunta a `com.whatsapp.w4b` con fallback a `com.whatsapp`.
