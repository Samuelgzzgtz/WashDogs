package com.washdog.app

import android.app.Application
import com.washdog.app.data.WashdogSupabase
import com.washdog.app.work.ReminderWorker

/**
 * Inicializa el contexto de aplicación para [WashdogSupabase] antes de cualquier
 * acceso al cliente (Application.onCreate corre antes de Activities/ViewModels).
 * Necesario para el SessionManager cifrado (EncryptedSharedPreferences).
 */
class WashdogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WashdogSupabase.appContext = applicationContext
        // Recordatorios diarios de clientes sin visitar hace ≥30 días (idempotente).
        ReminderWorker.schedule(applicationContext)
    }
}
