package com.washdog.app.data

import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {
    private val storage = InMemoryTokenStorage()
    private val repo = AuthRepository("https://test.supabase.co", "testkey", storage)

    @Test fun `isAuthenticated false when no tokens`() {
        assertFalse(repo.isAuthenticated())
    }

    @Test fun `isAuthenticated true when token not expired`() {
        storage.saveTokens("tok", "ref", System.currentTimeMillis() / 1000 + 3600)
        assertTrue(repo.isAuthenticated())
    }

    @Test fun `isAuthenticated false when token expired`() {
        storage.saveTokens("tok", "ref", System.currentTimeMillis() / 1000 - 1)
        assertFalse(repo.isAuthenticated())
    }

    @Test fun `signOut clears tokens and isAuthenticated returns false`() {
        storage.saveTokens("tok", "ref", System.currentTimeMillis() / 1000 + 3600)
        repo.signOut()
        assertFalse(repo.isAuthenticated())
        assertNull(storage.getRefreshToken())
    }

    @Test fun `getAccessToken returns stored token`() {
        storage.saveTokens("my_token", "ref", System.currentTimeMillis() / 1000 + 3600)
        assertEquals("my_token", repo.getAccessToken())
    }
}
