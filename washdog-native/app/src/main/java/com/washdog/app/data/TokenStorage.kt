package com.washdog.app.data

interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String, expiresAt: Long)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getExpiresAt(): Long
    fun clear()
}
