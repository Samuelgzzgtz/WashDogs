package com.washdog.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda las credenciales de login (correo + contraseña) cifradas en disco con
 * EncryptedSharedPreferences (AES256), para la opción "Recordar mis datos". El
 * autocompletado desde estas credenciales queda detrás de un desbloqueo biométrico
 * (ver LoginScreen); nunca se guardan en texto plano.
 */
class CredentialStore(context: Context) {

    private val prefs = securePrefs(context.applicationContext)

    fun save(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /** Devuelve (email, password) si hay credenciales guardadas, o null. */
    fun load(): Pair<String, String>? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return email to password
    }

    fun hasCredentials(): Boolean =
        prefs.contains(KEY_EMAIL) && prefs.contains(KEY_PASSWORD)

    fun clear() {
        prefs.edit().remove(KEY_EMAIL).remove(KEY_PASSWORD).apply()
    }

    private companion object {
        const val PREFS_NAME  = "washdog_secure_credentials"
        const val KEY_EMAIL    = "login_email"
        const val KEY_PASSWORD = "login_password"

        fun securePrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return try {
                build(context, masterKey)
            } catch (e: Exception) {
                // Store corrupto o master key inválida (p. ej. backup restaurado en otro
                // dispositivo): se descarta y se recrea; el usuario reescribe sus datos.
                context.deleteSharedPreferences(PREFS_NAME)
                build(context, masterKey)
            }
        }

        private fun build(context: Context, masterKey: MasterKey): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
    }
}
