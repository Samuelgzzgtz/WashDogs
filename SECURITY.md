# Seguridad — WashDogs

## Manejo de credenciales

| Credencial | Ubicación | ¿En git? |
|------------|-----------|----------|
| `SUPABASE_URL` | `local.properties` | No — gitignoreado |
| `SUPABASE_ANON_KEY` | `local.properties` | No — gitignoreado |
| `SUPABASE_URL` (CI) | GitHub Secret | No — nunca en texto plano |
| `SUPABASE_ANON_KEY` (CI) | GitHub Secret | No — nunca en texto plano |
| Keystore de firma | GitHub Secret (`KEYSTORE_BASE64`) | No |

## Controles activos

### Pre-commit hook (A.8.12)
`.githooks/pre-commit` bloquea commits que contengan:
- URLs `*.supabase.co`
- JWT tokens (`eyJ...`)
- Project IDs de Supabase en URLs del dashboard

**Activar en cada clon nuevo del repo:**
```sh
git config core.hooksPath .githooks
```

### .gitignore (A.8.12)
Patrones explícitos para `*.env`, `secrets.properties`, `*service_role*`, etc.

### GitHub Secrets (A.8.10)
El CI inyecta las credenciales en `local.properties` en tiempo de build. Las keys nunca aparecen en los logs ni en el código fuente.

## Qué hacer si se filtra una credencial

1. **Rotar la key inmediatamente** en el dashboard de Supabase (Settings → API).
2. Revocar el token comprometido antes de limpiar el historial.
3. Reescribir el historial con `git filter-repo` para eliminar la cadena expuesta.
4. Force-push a todas las ramas afectadas.
5. Notificar a los colaboradores para que hagan `git fetch --all` y rebasing.

## Referencias ISO/IEC 27001:2022

| Control | Descripción | Implementación |
|---------|-------------|----------------|
| A.8.10 | Borrado de información | Limpieza de historial git con filter-repo |
| A.8.12 | Prevención de fuga de datos | Pre-commit hook + .gitignore reforzado |
| A.8.9 | Gestión de la configuración | Credenciales en local.properties / GitHub Secrets, nunca hardcodeadas |
