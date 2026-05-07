# Washdog v2 — Design Spec

**Date:** 2026-05-06  
**Project:** Washdog (Android pet-grooming management app)  
**Scope:** Phase 1 UI/UX + Phase 2 DevOps + Phase 3 QA & Roadmap

---

## 1. Context

Single-activity Android app (Jetpack Compose + NavHost). Persistence via DataStore + Gson. No Room. `minSdk=26`, `targetSdk=34`. No tests exist yet. Module root: `washdog-native/`.

Key existing constructs:
- `AppViewModel` — `mutableStateListOf` for `clients`, `appointments`, `transactions`
- `AppRepository` — fire-and-forget saves via `saveScope`; suspend loads at startup
- Client↔Appointment link is `clientName: String` (not ID)

---

## 2. Sub-Project A — UI/UX Features

### A1 · Calendar Engine / Timeline Vertical

**Component:** `WDTimeline` in `ui/components/WDTimeline.kt`

**Placement:** Below `WDMonthCalendar` in `AgendaScreen`. The selected date drives which appointments are shown.

**Implementation:**
- `LazyColumn` with hour slots 08:00–20:00 (13 rows × 60dp each = 780dp total)
- Each slot is a `Row(height=60.dp)` with a time label and a content area
- Appointments are overlaid as `Box` elements positioned at `startHour * 60.dp` from the top
- Height = `max(60.dp, durationMinutes.dp)` where duration = `endTime - startTime` (default 60 min if `endTime` is empty)
- Tap on an appointment block opens edit flow

**Color mapping by `ApptStatus`:**
| Status | Color token |
|--------|-------------|
| `DONE` | `MaterialTheme.colorScheme.surfaceVariant` |
| `NEXT` | `MaterialTheme.colorScheme.primary` |
| `SCHEDULED` | `MaterialTheme.colorScheme.tertiary` |
| `REMINDER` | `Color(0xFFFFA000)` (amber) |

---

### A2 · Circular Time Picker (AM/PM + Start/End)

**Model change — `Appointment` data class:**
```kotlin
data class Appointment(
    val id: String,
    val clientName: String,
    val petName: String,
    val service: String,
    val time: String,         // start time "HH:mm" — field name kept for DataStore compat
    val endTime: String = "", // new, optional end time "HH:mm", default "" = no end
    val date: String,
    val status: ApptStatus
)
```
Backward compatible: existing DataStore JSON loads `endTime` as `""` via Gson default.

**Component:** `WDTimePickerDialog` wrapping M3 `TimePicker` (BOM 2024.05.00 — no new deps).

**UI in `AddAppointmentScreen`:**
- Row: `[Start time chip]  [Toggle "Fin tentativo"]  [End time chip — visible when toggle ON]`
- Each chip opens `WDTimePickerDialog`
- AM/PM is native to M3 `TimePicker` when `is24Hour = false`

---

### A3 · Phone Mask +52

**Component:** `WDPhoneField` in `ui/components/WDPhoneField.kt`

**Behavior:**
- Prefix `+52 ` is always visible and non-editable (implemented via `VisualTransformation`)
- Internal state stores only the 10-digit numeric string
- `keyboardOptions = KeyboardType.Phone`
- `maxLength = 10` digits enforced in `onValueChange`
- Persisted value: `"+52" + digits` (no space) in `Client.phone`

**Replaces** the current plain `OutlinedTextField` for phone in `AddClientScreen` and `EditClientScreen`.

---

### A4 · Fuzzy Search (Accent/Case Insensitive)

**Utility:** `fun String.normalized(): String` in `util/StringUtils.kt`
```kotlin
import java.text.Normalizer
fun String.normalized() = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("[^\\p{ASCII}]"), "")
    .lowercase()
fun String.fuzzyContains(query: String) = this.normalized().contains(query.normalized())
```

**Applied in:**
- `ClientsScreen` — search bar filters `clients` list by `ownerName.fuzzyContains(query) || petName.fuzzyContains(query)`
- `WDSearchableDropdown` (services) — filters `WD_SERVICES` by `name.fuzzyContains(query)`

No new dependencies required (`java.text.Normalizer` is part of Android SDK since API 9).

---

### A5 · CRUD Delete + Home/Back Navigation

**Delete — Appointments (`AgendaScreen`):**
- `SwipeToDismiss` (M3 `SwipeToDismissBox`) on each appointment card
- On swipe: show `AlertDialog("¿Eliminar esta cita?")` → confirm calls `vm.deleteAppointment(id)`
- Background shows red trash icon during swipe

**Delete — Transactions (`DashboardScreen`, `ReportsScreen`):**
- Each transaction row gets a trailing `IconButton(Icons.Default.Delete)`
- Tap: `AlertDialog` → confirm calls `vm.deleteTransaction(id)`

**Home/Back navigation:**
- All non-tab screens (`income`, `expense`, `add_appointment`, `add_client`, `edit_client`, `settings`) get a `TopAppBar` with `NavigationIcon = ArrowBack` calling `onBack()`
- Already wired via lambda params; audit each screen to ensure consistent usage

---

### A6 · Overlap Detection & Blocking

**Logic:** Pure function in `AppViewModel`, called before `addAppointment` and `updateAppointment`.

```
Two appointments A and B overlap if:
  A.date == B.date
  AND parseTime(A.time) < parseEndTime(B)
  AND parseEndTime(A) > parseTime(B.time)

Where parseEndTime(x) = if x.endTime.isNotEmpty() x.endTime else x.time + 1h
```

**ViewModel state:**
```kotlin
var overlapConflict by mutableStateOf<Appointment?>(null)
    private set
```

**Flow:**
1. User fills form in `AddAppointmentScreen`
2. On each time change, `vm.checkOverlap(candidate)` updates `overlapConflict`
3. UI observes `overlapConflict`: if non-null, shows `SnackBar("Conflicto con [clientName] a las [time]")` and disables the Save button
4. `addAppointment`/`updateAppointment` also guard internally (double check)

---

## 3. Sub-Project B — DevOps

### B1–B2 · Git Init & GitHub Repo

```bash
# Inside washdog-native/
git init
git add .
git commit -m "feat: initial Washdog commit"
gh repo create WashDogs --private --source=. --remote=origin --push
```

Requires: `gh auth login` active in session.

### B3 · CI/CD Pipeline

**File:** `.github/workflows/main.yml` (at repo root, i.e. `washdog-native/.github/workflows/main.yml`)

```yaml
name: Washdog CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Run unit tests
        run: ./gradlew test
        # fail-fast: pipeline stops here if tests fail

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: washdog-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          retention-days: 7
```

---

## 4. Sub-Project C — QA & Roadmap

### C1 · Unit Tests (JUnit 4)

**Location:** `app/src/test/java/com/washdog/app/`

**Test files and cases:**

**`PhoneFormatTest.kt`**
- `+52` prefix always present in stored value
- Exactly 10 digits accepted; 9 or 11 rejected
- Non-numeric characters stripped

**`TimeAmPmTest.kt`**
- `"09:00"` with AM stays `"09:00"`
- `"12:00"` PM = `"12:00"`, `"12:00"` AM = `"00:00"`
- `"01:00"` PM = `"13:00"`
- Round-trip: 24h → 12h+AM/PM → 24h is identity

**`OverlapDetectionTest.kt`**
- No overlap: `[09:00–10:00]` vs `[10:00–11:00]` (adjacent = no overlap)
- Clear overlap: `[09:00–11:00]` vs `[10:00–12:00]`
- Contained: `[09:00–12:00]` vs `[10:00–11:00]`
- Different date: same times, different date = no overlap
- No endTime: default 1h duration used

### C2 · Roadmap — 3 Mejoras Escalables

1. **CRM de clientes** — Pantalla de historial por cliente: lista de visitas, ticket promedio, frecuencia (días entre visitas). Exportable a CSV vía `ShareSheet`. No requiere cambio de modelo; se calcula de `appointments` y `transactions` existentes.

2. **Notificaciones automáticas** — `WorkManager` + `NotificationManager` para recordatorios 24h antes de la cita. Al crear/editar una cita, se encola un `OneTimeWorkRequest`. Sin permisos de internet adicionales; todo local. Complementa (no reemplaza) el envío manual por WhatsApp.

3. **Dashboard de ventas** — Gráficas de ingresos/egresos semanales y mensuales con la librería `Vico` (Compose-native, ~150KB). Comparativa vs. período anterior. Integra en `ReportsScreen` como pestaña adicional.

---

## 5. Implementation Order

```
A5 (CRUD delete + back nav)   ← más simple, desbloquea UX básica
A3 (phone mask)               ← componente aislado, no deps
A4 (fuzzy search)             ← pure function + conectar en UI
A2 (time picker + endTime)    ← model change, luego UI
A6 (overlap detection)        ← depende de A2 (endTime)
A1 (timeline)                 ← depende de A2 (endTime para alturas)
B  (git + GitHub + CI/CD)     ← paralelo o después de A
C  (tests + roadmap doc)      ← último, cuando haya lógica que testear
```

---

## 6. Constraints & Non-Goals

- No Room — persistencia sigue siendo DataStore + Gson
- No dependencias nuevas excepto: ninguna (todo usa APIs ya incluidas)
- No internacionalización — app es para un negocio específico en México
- No autenticación — app es local, single-user
- Tests instrumentados (Espresso) fuera de scope — solo unit tests de lógica pura
