package com.washdog.app

import android.app.Application
import com.washdog.app.data.WashdogSupabase

/**
 * Inicializa el contexto de aplicación para [WashdogSupabase] antes de cualquier
 * acceso al cliente (Application.onCreate corre antes de Activities/ViewModels).
 * Necesario para el SessionManager cifrado (EncryptedSharedPreferences).
 */
class WashdogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WashdogSupabase.appContext = applicationContext
    }
}
