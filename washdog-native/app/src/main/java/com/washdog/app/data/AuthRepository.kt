package com.washdog.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class AuthRepository {

    private val auth get() = WashdogSupabase.client.auth

    fun hasActiveSession(): Boolean = auth.currentSessionOrNull() != null

    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    suspend fun login(email: String, password: String) {
        auth.signInWith(Email) {
            this.email    = email
            this.password = password
        }
    }

    suspend fun logout() {
        auth.signOut()
    }
}
