# WashDogs — FAB fix, WhatsApp fallback y seguridad ISO/IEC 27001:2022

**Fecha:** 2026-05-26  
**Estado:** Aprobado

---

## Contexto

Tres problemas identificados en la sesión de hoy:

1. El FAB "+" en `ClientsScreen` no responde al toque cuando la lista de clientes llena la pantalla.
2. Los botones de WhatsApp crashean/no hacen nada en dispositivos sin WhatsApp instalado.
3. El project ID de Supabase estaba expuesto en el historial de git.

---

## Sección 1 — Fix FAB z-order

**Archivo:** `washdog-native/app/src/main/java/com/washdog/app/ui/screens/ClientsScreen.kt`

**Causa raíz:** En Compose, dentro de un `Box`, el último elemento declarado tiene mayor z-order y captura los eventos táctiles primero. El `FloatingActionButton` estaba declarado antes del `LazyColumn`, por lo que cuando la lista llenaba la pantalla el LazyColumn tapaba el FAB e interceptaba todos los toques en esa área.

**Fix:** Mover la declaración del `FloatingActionButton` después del `LazyColumn` dentro del `Box`. Sin cambios de lógica ni de UI.

---

## Sección 2 — WhatsApp fallback con Toast

**Archivo:** `washdog-native/app/src/main/java/com/washdog/app/util/WhatsAppHelper.kt`

**Causa raíz:** `startActivity()` lanza `ActivityNotFoundException` si no hay ninguna app (WhatsApp ni browser) que pueda manejar la URI `wa.me`. La excepción no era capturada, resultando en crash silencioso o comportamiento indefinido.

**Fix:** Envolver `startActivity` en `try-catch(ActivityNotFoundException)`. Si falla, mostrar un `Toast` con "WhatsApp no está instalado". No requiere cambios en el manifest ni en ningún caller.

---

## Sección 3 — Seguridad ISO/IEC 27001:2022

### 3a. Limpieza de historial git

**Hallazgo:** `docs/supabase-migration.sql` contenía el project ID de Supabase en un comentario (`SUPABASE_PROJECT_ID`), committeado en el historial de `master`.

**Fix:** Usar `git filter-repo` para reescribir los commits afectados, reemplazando la referencia al project ID por texto genérico. Force-push a `master`.

**Control ISO 27001:** A.8.10 — Borrado de información.

### 3b. Pre-commit hook

**Archivo:** `.githooks/pre-commit` (versionado en el repo)

Bloquea commits que contengan:
- URLs `*.supabase.co`
- JWT tokens (patrón `eyJ`)
- Project IDs de Supabase (26 chars alfanuméricos en minúsculas)

Activar con: `git config core.hooksPath .githooks`

**Control ISO 27001:** A.8.12 — Prevención de fuga de datos.

### 3c. .gitignore reforzado

Añadir patrones explícitos para archivos de secretos: `*.env`, `*.env.local`, `secrets.properties`, `*service_role*`, `*service-role*`.

**Control ISO 27001:** A.8.12 — Prevención de fuga de datos.

### 3d. SECURITY.md

Documento en la raíz del repo que describe:
- Cómo se manejan las credenciales (local.properties gitignoreado, GitHub Secrets para CI)
- Controles activos: pre-commit hook, gitignore reforzado
- Qué hacer si se detecta una filtración
- Controles ISO 27001 aplicados: A.8.10, A.8.12

---

## Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `washdog-native/app/.../ClientsScreen.kt` | Invertir orden FAB/LazyColumn en Box |
| `washdog-native/app/.../WhatsAppHelper.kt` | Try-catch en startActivity + Toast |
| `.gitignore` | Patrones adicionales de secretos |
| `.githooks/pre-commit` | Nuevo — hook anti-filtración |
| `SECURITY.md` | Nuevo — documento de controles |
| `docs/supabase-migration.sql` | Remover project ID del comentario |
| Historial git | Reescritura con filter-repo |
