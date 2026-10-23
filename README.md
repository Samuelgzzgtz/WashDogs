# Washdog

Gestión de estética canina: clientes, agenda, ingresos, gastos y mensajes de WhatsApp.

La web instalable está en `washdog-web/`. Usa las mismas cuentas y tablas de Supabase que la app Android de `washdog-native/`.

## Desarrollo

Requiere Node.js 22 o posterior. Desde `washdog-web/`:

```powershell
npm ci
Copy-Item .env.example .env.local
# Completar las dos variables de Supabase en .env.local.
npm run dev
```

Variables: `VITE_SUPABASE_URL` y `VITE_SUPABASE_ANON_KEY`, del mismo proyecto que utiliza Android. La clave debe ser la pública/anon o publishable, nunca una clave service role. Las políticas RLS restringen los datos a la cuenta autenticada.

```powershell
npm test
npm run build
npm run preview
```

Las pruebas ejecutan la migración y los permisos sobre PostgreSQL embebido, sin acceder a datos reales. El build incluye manifiesto, iconos y service worker. El service worker guarda los archivos de la aplicación; los datos de clientes se consultan en Supabase y los cambios requieren conexión. No hay una cola de cambios sin sincronizar.

## Primer despliegue en Vercel

1. En el proyecto Supabase existente, crear un respaldo y ejecutar `supabase/migrations/20261023000000_webapp.sql` desde el SQL Editor, o aplicar con Supabase CLI. Es una migración aditiva: conserva tablas, usuarios y registros. No ejecutar `docs/supabase-migration.sql`, que es un script antiguo destructivo.
2. Importar este repositorio en un proyecto nuevo de Vercel. Elegir **Vite** y **Root Directory: `washdog-web`**. El archivo `washdog-web/vercel.json` configura build y salida.
3. Añadir `VITE_SUPABASE_URL` y `VITE_SUPABASE_ANON_KEY` en las variables de Vercel para Production y Preview. Crear el despliegue. Si cambian las variables, volver a desplegar.
4. Verificar inicio de sesión con la cuenta existente y comprobar sus clientes, citas y movimientos antes de retirar Android. Los datos que Android aún tenga pendientes de sincronizar deben subirse desde ese dispositivo primero.

Vercel hospeda la web y proporciona HTTPS. Supabase conserva autenticación y base de datos; el disco de una función de Vercel no se usa para persistir información.

## Instalar y compartir

- **iPhone/iPad:** abrir el enlace HTTPS en Safari, tocar Compartir → Añadir a pantalla de inicio. Activar «Abrir como app» si aparece.
- **Android:** abrir el enlace en Chrome y tocar «Instalar app» o usar la opción del menú del navegador.
- La app instalada abre sin barra del navegador y avisa cuando hay una actualización. «Compartir app» envía el mismo enlace a cualquier dispositivo.

Cada cuenta conserva sus datos privados. Compartir el enlace no comparte la sesión ni los registros. Para trabajar desde dos dispositivos con los mismos datos, usar la misma cuenta. Esta versión incluye recordatorios dentro de la app y envío manual por WhatsApp; las notificaciones en segundo plano de Android no se migran a notificaciones push web.
