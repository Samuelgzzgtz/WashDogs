# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Washdog** — Android app de gestión para peluquerías caninas. Permite registrar clientes, citas, ingresos/gastos y enviar mensajes de WhatsApp con plantillas personalizables.

El módulo principal está en `washdog-native/`.

## Build

JDK configurado en `gradle.properties` (`org.gradle.java.home`). No modificar `JAVA_HOME` del sistema.

```powershell
# Desde washdog-native/
.\gradlew assembleDebug        # APK de debug
.\gradlew assembleRelease      # APK de release (minificado)
.\gradlew lint                 # Lint (abortOnError=true)
.\gradlew test                 # Unit tests
.\gradlew connectedAndroidTest # Tests instrumentados (requiere emulador/dispositivo)
```

## Arquitectura

Arquitectura de una sola actividad, sin Room (persistencia vía DataStore + Gson).

```
MainActivity
  └── AppViewModel (AndroidViewModel)
        └── AppRepository  ← DataStore + Gson (JSON serializado)

Modelos: Client, Appointment, Transaction  (data classes simples)
Enums:   PetType (DOG/CAT), ApptStatus (DONE/NEXT/REMINDER/SCHEDULED), TxType (IN/OUT)
```

**Flujo de datos:**
- `AppViewModel` expone listas reactivas como `mutableStateListOf` (Compose-observable).
- Al arrancar, carga desde `AppRepository`; si está vacío, inserta seed data.
- Cada mutación llama al `repo.save*` (fire-and-forget en `saveScope` propio de `AppRepository`, no suspend).
- No hay base de datos relacional: todo es JSON plano en un solo `DataStore`.
- `clientsToRemind`: clientes con `daysSince(lastVisit) >= 30`, mostrados en `WDReminderStrip`.
- `deleteClient` en cascada: borra también las `Appointment` cuyo `clientName` coincida.

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

La `WDTabBar` solo aparece en `dashboard`, `agenda`, `clients`, `reports`.

**WhatsApp:** `WhatsAppHelper.send()` abre `wa.me` con el mensaje interpolado. Las plantillas usan tokens `{{nombre}}`, `{{fecha}}`, `{{hora}}` y se editan en `SettingsScreen`.

**Catálogo de servicios:** Lista hardcoded en `data/ServiceCatalog.kt` (`WD_SERVICES: List<WDService>`). Usada en los dropdowns de `IncomeScreen` y `AddAppointmentScreen`.

## Convenciones

- Componentes reutilizables con prefijo `WD` en `ui/components/`.
- IDs de entidades: `String` generados en la UI (actualmente incrementales en seeds; usar `UUID.randomUUID().toString()` para nuevos registros).
- Fechas como `String` ISO-8601 (`LocalDate.toString()` → `"yyyy-MM-dd"`); `dateTime` de `Transaction` es `"yyyy-MM-ddTHH:mm"`.
- Clientes ligados a citas por `clientName` (String), no por ID — tenerlo en cuenta al renombrar clientes.
- `minSdk = 26` (Android 8.0); `compileSdk/targetSdk = 34`. No usar APIs por debajo de API 26.
- No hay tests escritos aún (`test/` no existe); `.\gradlew test` no falla pero no corre nada.
