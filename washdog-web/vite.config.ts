import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['icons/*.png', 'favicon.png'],
      manifest: {
        id: '/',
        name: 'Washdog',
        short_name: 'Washdog',
        lang: 'es-MX',
        description: 'Tu estética canina, siempre contigo.',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: '#faf6f0',
        theme_color: '#2d4a3e',
        icons: [
          {
            src: '/icons/icon-192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/icons/icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/icons/maskable-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // Only the application shell is cached. Customer data and auth stay off the service worker.
        globPatterns: ['**/*.{js,css,html,png,svg,woff2}'],
        navigateFallbackDenylist: [/^\/api\//, /^\/auth\//],
        cleanupOutdatedCaches: true,
      },
    }),
  ],
})
