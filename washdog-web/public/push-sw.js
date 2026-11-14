/* Notificaciones de Washdog. Lo importa el service worker que genera vite-plugin-pwa. */
self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { body: event.data ? event.data.text() : '' }
  }
  event.waitUntil(
    self.registration.showNotification(data.title || 'Washdog', {
      body: data.body || '',
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      tag: data.tag || undefined,
      renotify: Boolean(data.tag),
      data: { url: data.url || '/#bot' },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(
    (event.notification.data && event.notification.data.url) || '/#bot',
    self.location.origin,
  ).href
  event.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((windows) => {
        for (const win of windows) {
          if (new URL(win.url).origin === self.location.origin && 'focus' in win) {
            if ('navigate' in win) win.navigate(url).catch(() => {})
            return win.focus()
          }
        }
        return self.clients.openWindow(url)
      }),
  )
})
