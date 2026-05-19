# Washdog Full Build — Design Spec
_Date: 2026-05-18_

## Scope

Four independent sub-projects to be implemented in parallel:

| ID | Sub-proyecto | Prioridad |
|----|-------------|-----------|
| A  | Supabase Auth (sesión persistente) | Fundacional |
| B  | Dynamic Scheduling + Closing Barrier | Core business |
| C  | WhatsApp Business Contact Import | Utilidad |
| D  | GitHub Actions CI/CD APK build | DevOps |

---

## Sub-proyecto A — Auth (Supabase Auth, sesión persistente)

### Decisiones
- Email + password via Supabase Auth REST (`/auth/v1/token?grant_type=password`)
- Sesión persiste hasta logout explícito (no se borra al cerrar la app)
- Token almacenado en `EncryptedSharedPreferences` (Android Keystore)
- Un solo admin; no hay registro desde la app — el usuario se crea en el dashboard de Supabase

### Arquitectura

```
LoginScreen
  └── AppViewModel.login(email, password)
        └── AuthRepository
              ├── signIn()         → POST /auth/v1/token?grant_type=password
              ├── refresh()        → POST /auth/v1/token?grant_type=refresh_token
              ├── signOut()        → POST /auth/v1/logout  + limpiar EncryptedSharedPrefs
              └── loadSession()    → leer tokens de EncryptedSharedPrefs al arrancar

MainActivity:
  - Antes de montar NavHost: AppViewModel.isAuthenticated
    - false → LoginScreen
    - true  → NavHost (dashboard)
```

### Flujo de sesión
1. App abre → `AuthRepository.loadSession()`
2. Si `access_token` presente y no expirado → dashboard directo
3. Si expirado pero `refresh_token` presente → `refresh()` → actualizar tokens → dashboard
4. Si no hay tokens → `LoginScreen`
5. Login exitoso → guardar tokens → navegar a dashboard
6. Logout → borrar tokens → `LoginScreen`

### Archivos nuevos
- `data/AuthRepository.kt`
- `ui/screens/LoginScreen.kt`
- `viewmodel/AppViewModel.kt` — añadir `isAuthenticated`, `login()`, `logout()`, `authError`

### Campos EncryptedSharedPreferences
- `auth_access_token: String`
- `auth_refresh_token: String`
- `auth_expires_at: Long` (epoch segundos)

---

## Sub-proyecto B — Dynamic Scheduling + Closing Barrier

### Decisiones
- `durationMinutes: Int` añadido a `Appointment` (default sugerido por categoría, ajustable manualmente)
- Ajuste via stepper `−15 / +15 min` en `AddAppointmentScreen`
- Closing Barrier: validación antes de guardar, no permite guardar si se excede `closeTime`

### Estimación de duración por defecto
`ServiceDurationEstimator.suggest(serviceName: String): Int`

| Keyword en nombre | Minutos default |
|-------------------|----------------|
| "Jumbo"           | 360            |
| "Grande"          | 240            |
| "Mediano"         | 120            |
| "Pequeño" / "Gato"| 90             |
| Resto             | 60             |

### Closing Barrier
```
valid = (startMinutes + durationMinutes + travelBuffer) <= closeMinutes
```
Si inválido: dialog de error mostrando cuántos minutos excede la jornada.
La validación se ejecuta en `AddAppointmentScreen` antes de llamar `vm.addAppointment()`.

### Cambios al modelo
```kotlin
data class Appointment(
    // ... campos existentes ...
    val durationMinutes: Int = 60   // NUEVO
)
```

### Archivos nuevos/modificados
- `data/models/Appointment.kt` — añadir `durationMinutes`
- `util/ServiceDurationEstimator.kt` — nuevo
- `ui/screens/AddAppointmentScreen.kt` — stepper de duración + validación Closing Barrier
- `data/AppRepository.kt` — incluir `duration_minutes` en upsert de appointments

---

## Sub-proyecto C — Contact Import (WhatsApp Business)

### Decisiones
- Acepta `.vcf` y `.csv`
- Normaliza teléfonos: strip espacios/guiones, forzar prefijo `+52`
- Descarta duplicados por número de teléfono contra clientes existentes
- Preview antes de confirmar importación masiva
- WhatsApp Business (`com.whatsapp.w4b`) como target explícito en todos los envíos

### Parsers
`VcfParser.parse(inputStream): List<RawContact>`
- Extrae `FN:` (nombre) y `TEL:` (teléfono) de cada bloque `BEGIN:VCARD`

`CsvParser.parse(inputStream): List<RawContact>`
- Detecta columnas `name`/`nombre` y `phone`/`telefono`/`tel` (case-insensitive)

`PhoneNormalizer.normalize(raw: String): String?`
- Strip: `[\s\-\(\)]`
- Si empieza con `52` → `+52...`
- Si empieza con `1` (10 dígitos) → `+521...`
- Si tiene 10 dígitos sin prefijo → `+52...`
- Si no es válido → `null` (descartado con log)

### UI
Nueva ruta: `contact_import` — accesible desde `SettingsScreen` ("Importar Contactos")
1. Botón "Seleccionar archivo (.vcf / .csv)"
2. Parsing → lista preview con nombre + teléfono normalizado + badge "Ya existe" si duplicado
3. Contador: "X nuevos · Y duplicados"
4. Botón "Importar X contactos" → llama `vm.addClient()` para cada nuevo contacto

### WhatsApp Business
`WhatsAppHelper.send()`:
```kotlin
Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$msg")).apply {
    setPackage("com.whatsapp.w4b")
    if (resolveActivity(pm) == null) setPackage("com.whatsapp")  // fallback
}
```

### Archivos nuevos/modificados
- `util/VcfParser.kt`
- `util/CsvParser.kt`
- `util/PhoneNormalizer.kt`
- `ui/screens/ContactImportScreen.kt`
- `util/WhatsAppHelper.kt` — actualizar Intent
- `MainActivity.kt` — añadir ruta `contact_import`
- `ui/screens/SettingsScreen.kt` — añadir botón de acceso

---

## Sub-proyecto D — CI/CD (GitHub Actions → APK)

### Decisiones
- Repo privado `WashDogs` en `Samuelgzzgtz` (ya existe con remote configurado)
- Build en cada push a `main`
- Fail-fast: lint + unit tests deben pasar antes de assembleRelease
- `SUPABASE_URL` y `SUPABASE_ANON_KEY` como GitHub Secrets (no en repo)
- APK subido como artifact descargable (retención 7 días)

### Secrets requeridos en GitHub
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `KEYSTORE_BASE64` (opcional, para release firmado — fuera de scope ahora)

### Workflow `.github/workflows/build.yml`
```yaml
trigger: push to main
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - checkout
      - setup JDK 17
      - cache ~/.gradle
      - write local.properties from secrets
      - ./gradlew lint         # fail-fast
      - ./gradlew test         # fail-fast
      - ./gradlew assembleRelease
      - upload-artifact: app-release.apk (7 días)
```

### Archivos nuevos
- `.github/workflows/build.yml`
- `.gitignore` — verificar que `local.properties` y `*.jks` están excluidos

---

## Estado de features ya implementadas (no requieren trabajo)

- `ProximityEngine` + `ServiceZone` (NEAR/CLOSE/MEDIUM/FAR)
- `ServiceLocation` (HOME/BASE) + `travelFee` / `travelBuffer`
- `BusinessHours` (openTime/closeTime/closedDays)
- `ZonePrices` con 4 zonas
- `WDTimeline`, `WDTimePicker`, `WDPhoneField` (+52), `WDSearchableDropdown` (fuzzy), `WDZonePill`
- `OverlapChecker`
