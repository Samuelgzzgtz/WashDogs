package com.washdog.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persiste la sesión de Supabase (incl. el token) cifrada en disco mediante
 * EncryptedSharedPreferences (AES256). Reemplaza al SessionManager por defecto,
 * que guarda el token en texto plano. Cumple rule #4 (token seguro).
 */
class EncryptedSessionManager(context: Context) : SessionManager {

    private val json  = Json { ignoreUnknownKeys = true }
    private val prefs = securePrefs(context.applicationContext)

    override suspend fun saveSession(session: UserSession) {
        prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
    }

    override suspend fun loadSession(): UserSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return try { json.decodeFromString<UserSession>(raw) } catch (e: Exception) { null }
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private companion object {
        const val PREFS_NAME  = "washdog_secure_session"
        const val KEY_SESSION = "supabase_session"

        fun securePrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return try {
                build(context, masterKey)
            } catch (e: Exception) {
                // Prefs corruptas o master key inválida (p. ej. tras restaurar un backup
                // en otro dispositivo): se descarta solo la sesión y se recrea el store.
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
