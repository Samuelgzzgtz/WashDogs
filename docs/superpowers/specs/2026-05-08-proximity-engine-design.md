# Washdog — Motor de Proximidad + Configuración de Negocio
**Fecha:** 2026-05-08  
**Sub-proyecto:** A — App Android (sin backend)  
**Estado:** Aprobado

---

## Objetivo

Añadir lógica de proximidad geográfica al flujo de citas de Washdog. El sistema detecta
automáticamente la zona del cliente según su dirección, calcula el cargo de traslado y el
buffer de tiempo, y ofrece al operador la opción de servicio a domicilio vs traer la mascota
a la base (Privada Granada, Mitras Poniente).

---

## Sección 1 — Modelos de Datos

### Nuevos enums

```kotlin
// ServiceZone.kt
enum class ServiceZone { NEAR, CLOSE, MEDIUM, FAR }

// ServiceLocation.kt
enum class ServiceLocation { HOME, BASE }
```

### Client (campos nuevos)

```kotlin
data class Client(
    // ... campos existentes sin cambio ...
    val address: String = "",
    val zone: ServiceZone = ServiceZone.FAR   // auto-detectada al guardar, override manual permitido
)
```

### Appointment (campos nuevos)

```kotlin
data class Appointment(
    // ... campos existentes sin cambio ...
    val serviceLocation: ServiceLocation = ServiceLocation.HOME,
    val travelFee: Double = 0.0,
    val travelBuffer: Int = 0              // minutos de traslado sumados a la cita
)
```

Todos los campos nuevos tienen defaults → retrocompatible con JSON existente en DataStore.

### BusinessHours (nuevo modelo)

```kotlin
data class BusinessHours(
    val openTime: String  = "09:00",
    val closeTime: String = "19:00",
    val closedDays: List<Int> = listOf(0)  // 0=Dom, 1=Lun … 6=Sáb
)
```

### ZonePrices (nuevo modelo)

```kotlin
data class ZonePrices(
    val nearFee:    Double = 0.0,   val nearBuffer:   Int = 0,
    val closeFee:   Double = 50.0,  val closeBuffer:  Int = 10,
    val mediumFee:  Double = 90.0,  val mediumBuffer: Int = 25,
    val farFee:     Double = 120.0, val farBuffer:    Int = 40
)
```

---

## Sección 2 — ProximityEngine

Archivo: `util/ProximityEngine.kt`

### Zonas y keywords

| Zona | Keywords en address (case-insensitive) | Cargo default | Buffer default |
|------|----------------------------------------|---------------|----------------|
| NEAR | "granada" | $0 | 0 min |
| CLOSE | "ensenada", "cedral", "ajusco" | $50 | 10 min |
| MEDIUM | "mitras poniente" (sin hit previo) | $90 | 25 min |
| FAR | todo lo demás | $120 | 40 min |

**Regla especial BASE:** Si `serviceLocation == BASE`, cargo = $0 y buffer = 0 sin importar zona.

### Implementación

```kotlin
object ProximityEngine {

    private val NEAR_KEYWORDS  = listOf("granada")
    private val CLOSE_KEYWORDS = listOf("ensenada", "cedral", "ajusco")

    fun detectZone(address: String): ServiceZone {
        val n = address.lowercase()
        return when {
            NEAR_KEYWORDS.any  { n.contains(it) } -> ServiceZone.NEAR
            CLOSE_KEYWORDS.any { n.contains(it) } -> ServiceZone.CLOSE
            n.contains("mitras poniente")          -> ServiceZone.MEDIUM
            else                                   -> ServiceZone.FAR
        }
    }

    fun travelFee(zone: ServiceZone, location: ServiceLocation, prices: ZonePrices): Double =
        if (location == ServiceLocation.BASE) 0.0
        else when (zone) {
            ServiceZone.NEAR   -> prices.nearFee
            ServiceZone.CLOSE  -> prices.closeFee
            ServiceZone.MEDIUM -> prices.mediumFee
            ServiceZone.FAR    -> prices.farFee
        }

    fun travelBuffer(zone: ServiceZone, location: ServiceLocation, prices: ZonePrices): Int =
        if (location == ServiceLocation.BASE) 0
        else when (zone) {
            ServiceZone.NEAR   -> prices.nearBuffer
            ServiceZone.CLOSE  -> prices.closeBuffer
            ServiceZone.MEDIUM -> prices.mediumBuffer
            ServiceZone.FAR    -> prices.farBuffer
        }
}
```

---

## Sección 3 — Repository + ViewModel

### AppRepository — cambios

Dos nuevas claves en DataStore:

```kotlin
private val KEY_BUSINESS_HOURS = stringPreferencesKey("business_hours")
private val KEY_ZONE_PRICES    = stringPreferencesKey("zone_prices")
```

Métodos nuevos (mismo patrón fire-and-forget que el resto):

```
saveBusinessHours(bh: BusinessHours)
saveZonePrices(zp: ZonePrices)
suspend loadBusinessHours(): BusinessHours
suspend loadZonePrices(): ZonePrices
```

### AppViewModel — cambios

```kotlin
var businessHours by mutableStateOf(BusinessHours())   private set
var zonePrices    by mutableStateOf(ZonePrices())       private set
```

**Cargados en `loadData()`** junto con templates y listas existentes.

**Método nuevo — validación de horario:**

```kotlin
fun isWithinBusinessHours(time: String, date: String): Boolean {
    val dow = LocalDate.parse(date).dayOfWeek.value % 7   // 0=Dom
    if (dow in businessHours.closedDays) return false
    return time >= businessHours.openTime && time <= businessHours.closeTime
}
```

**Métodos nuevos — actualización:**

```kotlin
fun updateBusinessHours(bh: BusinessHours) { businessHours = bh; repo.saveBusinessHours(bh) }
fun updateZonePrices(zp: ZonePrices)       { zonePrices = zp;    repo.saveZonePrices(zp) }
```

**Auto-detección de zona al guardar cliente:**

```kotlin
fun addClient(c: Client) {
    val withZone = c.copy(zone = ProximityEngine.detectZone(c.address))
    clients.add(withZone)
    repo.saveClients(clients.toList())
}
// updateClient() aplica la misma lógica
```

---

## Sección 4 — UI

### AddClientScreen / EditClientScreen

Dos campos nuevos al final del formulario:

1. **Dirección** — `OutlinedTextField` de texto libre (calle + colonia)
2. **Zona detectada** — pill de color, calculada en tiempo real mientras el usuario escribe;
   dropdown de override manual con las 4 opciones

Colores del pill:

| Zona | Color |
|------|-------|
| NEAR | Verde |
| CLOSE | Azul |
| MEDIUM | Amarillo |
| FAR | Rojo |

### AddAppointmentScreen

Al seleccionar un cliente se muestra una card de contexto de zona:

- **NEAR:** card verde — "Privada Granada · Sin cargo de traslado". `serviceLocation` fijado en HOME automáticamente.
- **CLOSE / MEDIUM / FAR:** card con dos botones seleccionables:
  - 🏠 A domicilio — muestra cargo y buffer correspondientes
  - 🏪 Trae a la base — muestra $0 / 0 min

La selección actualiza `serviceLocation`, `travelFee` y `travelBuffer` en tiempo real.

**Validaciones adicionales:**
- Si hora fuera de `businessHours` → warning rojo bajo campo de hora
- Si día en `closedDays` → warning "Negocio cerrado este día"
- Ambas validaciones son advertencias (no bloquean guardar) para dar flexibilidad al operador

### SettingsScreen

Dos nuevas secciones al final del scroll existente:

**Horario de negocio**
- `WDTimePicker` para hora de apertura y hora de cierre
- 7 chips toggleables (Dom / Lun / Mar / Mié / Jue / Vie / Sáb) para días cerrados

**Precios por zona**
- 4 filas: NEAR / CLOSE / MEDIUM / FAR
- Cada fila: campo de cargo ($) + campo de buffer (min)
- Guardado con el botón "Guardar" unificado ya existente en SettingsScreen

### AgendaScreen — AppointmentRow

El subtítulo de cada cita incluye:
- Icono 🏠 o 🏪 según `serviceLocation`
- Si `travelFee > 0`: texto `+$XX` en color gris junto al icono

```
10:00–11:10 · Carla Mendoza
Luna · Baño+Corte  🏠 +$50
```

---

## Archivos afectados

| Archivo | Cambio |
|---------|--------|
| `data/models/Client.kt` | +address, +zone |
| `data/models/Appointment.kt` | +serviceLocation, +travelFee, +travelBuffer |
| `data/models/ServiceZone.kt` | nuevo enum |
| `data/models/ServiceLocation.kt` | nuevo enum |
| `data/models/BusinessHours.kt` | nuevo data class |
| `data/models/ZonePrices.kt` | nuevo data class |
| `util/ProximityEngine.kt` | nuevo object |
| `data/AppRepository.kt` | +2 keys, +4 métodos |
| `viewmodel/AppViewModel.kt` | +2 states, +3 métodos, auto-detección en add/updateClient |
| `ui/screens/AddClientScreen.kt` | +dirección, +pill de zona |
| `ui/screens/EditClientScreen.kt` | +dirección, +pill de zona |
| `ui/screens/AddAppointmentScreen.kt` | +card zona, +HOME/BASE selector, +validación horario |
| `ui/screens/SettingsScreen.kt` | +sección horarios, +sección precios zona |
| `ui/screens/AgendaScreen.kt` | +iconos y cargo en AppointmentRow |

---

## Fuera de scope (Sub-proyecto B)

- Bot de WhatsApp con Twilio (backend Node.js separado)
- Integración Google Maps API para cálculo de tráfico real
- Notificaciones push
