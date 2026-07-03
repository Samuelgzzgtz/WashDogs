# WashDogs v1.3.0 — Mejoras en 4 fases

**Fecha:** 2026-07-03
**Estado:** aprobado por el usuario (ejecución autónoma)
**Entrega:** rama `feature/v1.3.0-mejoras` integrada a `master` + GitHub Release v1.3.0

## Contexto

WashDogs es una app Android nativa (Kotlin + Jetpack Compose, single-activity) con
persistencia local DataStore+Gson particionada por usuario y sync fire-and-forget a
Supabase. Ver `CLAUDE.md` para la arquitectura completa.

Este spec agrupa mejoras en 4 fases secuenciales, cada una verificable por separado,
integradas en un único entregable final.

### Hallazgo previo a corregir
La tabla `appointments` de Supabase tiene columna `notes` pero **no** `end_time`,
mientras `AppointmentRow` serializa `end_time`. El upsert de citas probablemente falla
en silencio (catch vacío). Se reconcilia en Fase 1 (añadir `end_time` a la tabla).

---

## Fase 1 — Features + quick wins (sin migración de datos)

### 1.1 Cerrar sesión
- Botón "Cerrar sesión" al fondo de `SettingsScreen`, estilo destructivo, con diálogo
  de confirmación. Llama `vm.logout()` (ya existe; hoy no está conectado a UI).
- La navegación ya reacciona a `isLoggedIn=false` → vuelve a `LoginScreen`.
- Opción separada "Olvidar datos guardados" que borra las credenciales recordadas.

### 1.2 Recordar credenciales (cifradas + huella/PIN)
- Nuevo `data/CredentialStore.kt`: envuelve `EncryptedSharedPreferences`
  (`androidx.security:security-crypto`, ya en el proyecto). API: `save(email, password)`,
  `load(): Pair<String,String>?`, `clear()`, `hasCredentials(): Boolean`.
- `LoginScreen`: checkbox "Recordar mis datos". Al login exitoso con el check activo →
  `CredentialStore.save`.
- Al abrir el login sin sesión activa y con credenciales guardadas → `BiometricPrompt`
  (`androidx.biometric:biometric`) con `DEVICE_CREDENTIAL` como fallback (PIN/patrón).
  Al validar, autocompleta email+password y ejecuta `vm.login`.
- Logout conserva las credenciales recordadas (para no reescribir); "Olvidar datos" las
  borra explícitamente.
- **Seguridad:** password nunca en texto plano fuera de EncryptedSharedPreferences; el
  desbloqueo biométrico protege el autocompletado.

### 1.3 Reagendado recurrente
- `Appointment` gana `repeatEveryDays: Int? = null` y `seriesId: String? = null`
  (ambos con default null → citas existentes siguen siendo puntuales).
- Nuevo `util/Recurrence.kt` (funciones puras, testeables JVM):
  - `occurrencesWithinHorizon(startDate, everyDays, horizonDays): List<String>` genera
    fechas ISO desde `startDate` cada `everyDays` hasta `startDate+horizonDays`.
  - `missingOccurrences(existingDates, startDate, everyDays, horizonDays)` para el
    top-up incremental.
- UI en "Nueva cita": fila de chips de preajuste (7 / 14 / 15 / 30 días / personalizado).
  Si se elige recurrencia, al guardar se generan las ocurrencias del horizonte
  (**90 días**), todas con el mismo `seriesId` (UUID) y `repeatEveryDays`.
- Top-up: en `loadData()` (arranque), para cada serie activa se generan las ocurrencias
  faltantes hasta hoy+90 días.
- Borrado: borrar una ocurrencia borra solo esa cita. "Detener recurrencia" (en el row
  de una cita con `seriesId`) borra las ocurrencias futuras de esa serie.
- Chequeo de solapes (`OverlapChecker`) aplica por ocurrencia; las que solapen se omiten.
- Calendario: `WDMonthCalendar` ya marca cualquier fecha con cita → las ocurrencias
  aparecen automáticamente.
- Supabase: añadir columnas `repeat_every_days int`, `series_id text`, `end_time text` a
  `appointments`; `AppointmentRow` mapea las nuevas.

### 1.4 Quick wins
- **Confirmar borrado de cliente:** diálogo que avisa que se borran también sus citas
  (hoy `deleteClient` borra en cascada sin aviso). En `ClientsScreen`/`EditClientScreen`.
- **Estado vacío/onboarding:** mensaje guía cuando no hay clientes/citas reales.
- **Búsqueda/filtro:** campo de búsqueda en `ClientsScreen` (por nombre/mascota) y filtro
  en Agenda.

---

## Fase 2 — Capa de sync confiable

### 2.1 Last-write-wins
- Añadir `updatedAt: String` (ISO-8601 timestamp) a `Client`, `Appointment`,
  `Transaction`, y a los `*Row` (`updated_at` en Supabase). Se setea en cada mutación.
- `mergeById` (en `AppViewModel`) pasa de "local siempre gana" a **comparar `updatedAt`
  y quedarse con el más reciente**. Nueva función pura `mergeByUpdatedAt` con tests.

### 2.2 Tombstones (borrados que propagan)
- Nueva tabla/columna de borrado lógico: `deleted_at` en cada entidad (o tabla
  `tombstones(user_id, entity, id, deleted_at)`). Decisión: columna `deleted_at` nullable
  en cada tabla (más simple).
- Borrar = marcar `deleted_at` (soft delete) y sincronizar; el merge excluye registros
  con `deleted_at`. Evita que la unión "reviva" un borrado hecho en otro dispositivo.
- Purga local de tombstones antiguos (> N días) en el top-up.

### 2.3 Indicador de sincronización pendiente
- `data/SyncQueue.kt`: cola local (DataStore) de mutaciones sin subir. Cada `sync*ToCloud`
  fallido encola; se reintenta en `loadData()` y al recuperar conectividad.
- Estado observable `pendingSync: Int` en `AppViewModel`; badge/indicador en la UI
  (p. ej. en Dashboard).

---

## Fase 3 — Integridad por clientId

- `Appointment` gana `clientId: String` (se mantiene `clientName`/`petName` como cache
  denormalizado para mostrar sin join).
- Migración: al cargar, rellenar `clientId` de citas existentes matcheando `clientName`
  con `Client.ownerName` (única vez, idempotente).
- `deleteClient` en cascada por `clientId` en vez de por nombre.
- Renombrar un cliente ya no desconecta sus citas.
- Supabase: columna `client_id text` en `appointments` + backfill.

---

## Fase 4 — Notificaciones de recordatorio

- `WorkManager` (`androidx.work:work-runtime-ktx`) con job periódico (1×/día) que revisa
  `clientsToRemind` (>30 días sin visita) y lanza notificación local.
- Permiso `POST_NOTIFICATIONS` (API 33+) solicitado en runtime; canal de notificación.
- Tap en la notificación abre la Agenda.
- El job usa el repositorio local (no requiere UI activa); respeta el usuario logueado.

---

## Fase 5 (futura, NO se implementa) — iPhone

Ruta recomendada: **Compose Multiplatform (KMP)**, que reutiliza el Kotlin + Compose +
Supabase existentes moviendo UI/lógica a `commonMain` y añadiendo target iOS con
`expect/actual` para: importar contactos, almacenamiento cifrado (→ Keychain) y DataStore.
**Requiere Mac con Xcode + cuenta Apple Developer ($99/año).** Esfuerzo grande y aparte.

---

## Estrategia de testing

Tests JVM puros (patrón existente en `app/src/test/`, sin framework Android):
- `RecurrenceTest`: generación de ocurrencias y top-up incremental.
- `MergeTest`: `mergeByUpdatedAt` (LWW) y exclusión de tombstones.
- `CredentialStoreTest`: solo la lógica pura extraíble (el cifrado real requiere Android).
- Reutilizar el workaround documentado (test-worker Gradle roto local → `java` + JUnitCore).

## Riesgos y mitigaciones

- **Migraciones de datos (Fases 2 y 3):** idempotentes y best-effort; no destructivas
  (nunca borran datos locales sin sincronizar). Soft-delete en vez de hard-delete.
- **Cambios de esquema Supabase:** vía `apply_migration`; aditivos (columnas nullable),
  no rompen clientes viejos.
- **Verificación:** el entorno local no compila/ejecuta el APK de forma fiable; se validará
  la lógica pura con tests JVM y se reportará con transparencia qué quedó sin verificar en
  dispositivo.
