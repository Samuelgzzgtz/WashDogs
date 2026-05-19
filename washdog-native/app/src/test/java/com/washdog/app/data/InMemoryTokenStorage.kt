package com.washdog.app.data

class InMemoryTokenStorage : TokenStorage {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var expiresAt: Long = 0L

    override fun saveTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.expiresAt = expiresAt
    }
    override fun getAccessToken(): String? = accessToken
    override fun getRefreshToken(): String? = refreshToken
    override fun getExpiresAt(): Long = expiresAt
    override fun clear() { accessToken = null; refreshToken = null; expiresAt = 0L }
}
