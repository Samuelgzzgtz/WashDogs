# Caché local por usuario — diseño

Fecha: 2026-06-17
Rama: `fix/per-user-local-cache`

## Problema

El DataStore local (`washdog_prefs`) usa claves fijas sin namespace
(`clients`, `transactions`, `appointments`, `tpl_*`, `initialized`). Como
`logout()` (correctamente) no borra los datos de negocio locales, si el usuario A
cierra sesión y el usuario B inicia sesión en el **mismo dispositivo**:

1. `loadData()` carga los datos locales de A.
2. Los mergea con la nube de B (merge no destructivo por id, local gana).
3. Sube el resultado a la nube → **datos de A acaban en la cuenta de B**
   (contaminación cruzada + fuga de datos entre cuentas).

Riesgo residual no cubierto por los fixes B1/B2.

## Decisión

Aislar la caché local **por usuario**: cada cuenta tiene su propia partición de
datos en el mismo DataStore, vía claves con sufijo del `userId`.

```
clients_<uid>, transactions_<uid>, appointments_<uid>,
tpl_welcome_<uid>, tpl_confirm_<uid>, tpl_reschedule_<uid>, initialized_<uid>
```

## Diseño

### 1. Namespacing de claves (`LocalKeys`, función pura)

`LocalKeys.scoped(base, uid)` → `"${base}_${uid}"`; si `uid` es `null`/vacío usa
`"_anon"` como defensa (en la práctica nunca ocurre: load/save siempre post-login).

`AppRepository` resuelve el uid internamente con su helper `userId()`
(`auth.currentUserOrNull()?.id`). **Las llamadas desde `AppViewModel` no
cambian** (cambio mínimo).

### 2. Migración única de datos legacy

Los usuarios existentes tienen datos bajo las claves viejas sin namespace. Para
no perderlos al actualizar:

- Flag global `legacy_migrated` (boolean, una sola vez por dispositivo).
- En el arranque de `loadData()`, antes de leer: si `!legacy_migrated`, copiar
  cada valor legacy presente → su clave `_<uid>` del usuario logueado (dueño de
  esos datos pre-update) **solo si el destino está vacío**, borrar las claves
  legacy y marcar `legacy_migrated = true`.
- `LocalKeys.basesToCopy(all, legacyPresent, scopedPresent)` decide qué bases
  copiar (presente en legacy y ausente en scoped) — función pura testeable.

### 3. Efecto en el flujo

- `logout()` sigue sin borrar nada; los datos quedan en `*_<uidA>`.
- Al entrar B: `loadData()` lee `*_<uidB>` (vacío si es nuevo) → merge solo con
  la nube de B → sube solo lo de B. A conserva `*_<uidA>` intacto.
- El seed demo (`initialized_<uid>`) es por usuario.

## Pruebas (TDD, JVM puro)

`LocalKeysTest`:
- `scoped()` produce `"clients_<uid>"`; uid null/vacío → `"_anon"`.
- `basesToCopy()` incluye base con legacy presente y scoped ausente; excluye si
  el scoped ya existe o si no hay legacy.

Sin tests instrumentados Android (consistente con el resto del módulo). Se
ejecutan vía `java` + JUnitCore por el test-worker de Gradle roto en este entorno.

## Riesgos / límites

- La migración asume que el usuario del primer arranque post-update es el dueño
  de los datos legacy (correcto: antes era mono-cuenta efectiva).
- No migra datos entre cuentas (es justo lo que se evita).
