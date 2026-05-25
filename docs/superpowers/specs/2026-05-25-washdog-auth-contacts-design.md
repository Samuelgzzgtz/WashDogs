# Washdog — Auth + Sync Supabase + Contact Picker

**Date:** 2026-05-25
**Status:** Approved

---

## Resumen

Dos features nuevas para Washdog:

1. **Login con Supabase** — Email + contraseña. Data almacenada en Supabase PostgreSQL. DataStore sigue siendo caché local para soporte offline. Al iniciar sesión en un dispositivo nuevo se descarga la data de la nube.
2. **Selector de contactos** — Botón en Add/Edit Client que abre el selector de contactos de Android para copiar nombre y teléfono sin escribirlos manualmente.

---

## 1. Arquitectura

```
MainActivity
  └── WashdogApp
        ├── LoginScreen        ← NUEVA (startDestination si no hay sesión)
        └── [rutas existentes] dashboard, agenda, clients, reports…

AppViewModel
  ├── AuthRepository            ← NUEVO
  │     └── Supabase Auth (email/password)
  └── AppRepository             ← MODIFICADO
        ├── DataStore + Gson    ← caché local (igual que hoy)
        └── SupabaseSync        ← NUEVO: upsert a cloud en cada mutación

WashdogSupabase (singleton)     ← NUEVO
  └── createSupabaseClient(url, anonKey) { Auth + Postgrest }
```

**Flujo de arranque:**

1. App abre → `AuthRepository.hasActiveSession()` verifica token guardado.
2. Sin sesión → navega a `LoginScreen`.
3. Login exitoso:
   a. Si es primer dispositivo: sube DataStore local a Supabase.
   b. Si ya hay datos en nube: descarga y sobreescribe DataStore.
4. Conexión recuperada tras offline → `AppRepository.syncPendingToCloud()` sube cambios locales.

---

## 2. Supabase Schema

Proyecto nuevo: **WashDogs** (us-east-1, plan gratuito).

### Tablas

```sql
-- clients
create table clients (
  id            text        primary key,
  user_id       uuid        references auth.users not null,
  owner_name    text        not null,
  pet_name      text        not null,
  breed         text        not null,
  last_visit    text        not null,
  needs_reschedule boolean  not null default false,
  pet_type      text        not null,
  phone         text        not null default ''
);

-- appointments
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

-- transactions
create table transactions (
  id        text primary key,
  user_id   uuid references auth.users not null,
  type      text             not null,
  amount    double precision not null,
  concept   text             not null,
  detail    text             not null,
  date_time text             not null
);

-- user_settings
create table user_settings (
  user_id        uuid primary key references auth.users,
  tpl_welcome    text not null,
  tpl_confirm    text not null,
  tpl_reschedule text not null
);
```

### RLS (Row Level Security)

Todas las tablas aplican: `auth.uid() = user_id`.

```sql
alter table clients      enable row level security;
alter table appointments enable row level security;
alter table transactions enable row level security;
alter table user_settings enable row level security;

create policy "own_clients"      on clients      for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own_appointments" on appointments for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own_transactions" on transactions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own_settings"     on user_settings for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
```

---

## 3. Dependencias Android

```kotlin
// build.gradle.kts (app)
implementation(platform("io.github.jan-tennert.supabase:bom:2.6.1"))
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:auth-kt")
implementation("io.ktor:ktor-client-android:2.3.12")
```

`BuildConfig` expondrá `SUPABASE_URL` y `SUPABASE_ANON_KEY` via `local.properties` + `buildConfigField`.

---

## 4. Archivos nuevos / modificados

| Acción   | Ruta                                                                  |
|----------|-----------------------------------------------------------------------|
| Create   | `app/src/main/java/com/washdog/app/data/WashdogSupabase.kt`           |
| Create   | `app/src/main/java/com/washdog/app/data/AuthRepository.kt`            |
| Create   | `app/src/main/java/com/washdog/app/ui/screens/LoginScreen.kt`         |
| Modify   | `app/src/main/java/com/washdog/app/data/AppRepository.kt`             |
| Modify   | `app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt`         |
| Modify   | `app/src/main/java/com/washdog/app/MainActivity.kt`                   |
| Modify   | `app/src/main/java/com/washdog/app/ui/screens/AddClientScreen.kt`     |
| Modify   | `app/src/main/java/com/washdog/app/ui/screens/EditClientScreen.kt`    |
| Modify   | `app/src/main/AndroidManifest.xml`                                    |
| Modify   | `washdog-native/app/build.gradle.kts`                                 |
| Modify   | `washdog-native/local.properties`                                     |

---

## 5. Contact Picker

- Permiso: `READ_CONTACTS` en `AndroidManifest.xml`.
- En `AddClientScreen` y `EditClientScreen`: `IconButton` con ícono de persona/contacto.
- Usa `rememberLauncherForActivityResult(ActivityResultContracts.PickContact())`.
- Al seleccionar: extrae `DISPLAY_NAME` y primer `PHONE` del `ContentResolver`.
- Pre-llena los campos `ownerName` y `phone` del formulario.

---

## 6. Comportamiento offline

- Cada mutación escribe en DataStore primero (siempre instantáneo).
- Si hay internet, hace upsert a Supabase inmediatamente después.
- Si no hay internet, se marca `syncPending = true` en DataStore.
- Al recuperar conexión (en `AppViewModel.onResume` o `NetworkCallback`), ejecuta sync pendiente.
- La sesión de Supabase Auth se persiste en `EncryptedSharedPreferences` vía el SDK.

---

## 7. LoginScreen

- Campo email + campo password (con toggle de visibilidad).
- Botón "Iniciar sesión".
- Indicador de carga durante el request.
- Error inline si credenciales inválidas.
- Sin registro público — el dueño crea su cuenta desde Supabase Dashboard o se usa el método `signUp` una sola vez desde la app (se puede deshabilitar después).

---

## 8. Decisiones excluidas

- **No se implementa multi-usuario / empleados** en esta iteración.
- **No se implementa WhatsApp Business API** — queda para una iteración futura.
- **No se implementa sync en tiempo real** (Realtime de Supabase) — los upserts son suficientes para un solo usuario.
