package com.washdog.app.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AuthRepository(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val tokenStorage: TokenStorage
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    private data class AuthResponse(
        val access_token: String?,
        val refresh_token: String?,
        val expires_in: Int?,
        val error_description: String?
    )

    fun isAuthenticated(): Boolean {
        val exp = tokenStorage.getExpiresAt()
        return exp > 0L && System.currentTimeMillis() / 1000 < exp
    }

    fun getAccessToken(): String? = tokenStorage.getAccessToken()

    fun signOut() = tokenStorage.clear()

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = gson.toJson(mapOf("email" to email, "password" to password))
            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON))
                .build()
            val resp = http.newCall(req).execute()
            val json = resp.body?.string() ?: throw Exception("Sin respuesta del servidor")
            val parsed = gson.fromJson(json, AuthResponse::class.java)
            if (!resp.isSuccessful || parsed.access_token == null) {
                throw Exception(parsed.error_description ?: "Credenciales incorrectas")
            }
            tokenStorage.saveTokens(
                parsed.access_token,
                parsed.refresh_token ?: "",
                System.currentTimeMillis() / 1000 + (parsed.expires_in ?: 3600)
            )
        }
    }

    suspend fun refreshIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (isAuthenticated()) return@withContext true
        val refreshToken = tokenStorage.getRefreshToken()?.takeIf { it.isNotBlank() }
            ?: return@withContext false
        runCatching {
            val body = gson.toJson(mapOf("refresh_token" to refreshToken))
            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON))
                .build()
            val resp = http.newCall(req).execute()
            val json = resp.body?.string() ?: return@runCatching false
            val parsed = gson.fromJson(json, AuthResponse::class.java)
            if (!resp.isSuccessful || parsed.access_token == null) return@runCatching false
            tokenStorage.saveTokens(
                parsed.access_token,
                parsed.refresh_token ?: refreshToken,
                System.currentTimeMillis() / 1000 + (parsed.expires_in ?: 3600)
            )
            true
        }.getOrElse { false }
    }
}
