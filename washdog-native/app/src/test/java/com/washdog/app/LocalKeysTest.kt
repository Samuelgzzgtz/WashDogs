package com.washdog.app

import com.washdog.app.data.LocalKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalKeysTest {

    @Test
    fun scoped_appends_uid() {
        assertEquals("clients_u123", LocalKeys.scoped("clients", "u123"))
    }

    @Test
    fun scoped_null_uid_falls_back_to_anon() {
        assertEquals("clients_anon", LocalKeys.scoped("clients", null))
    }

    @Test
    fun scoped_blank_uid_falls_back_to_anon() {
        assertEquals("clients_anon", LocalKeys.scoped("clients", "   "))
    }

    @Test
    fun basesToCopy_includes_legacy_present_and_scoped_absent() {
        val all = listOf("clients", "transactions", "appointments")
        val result = LocalKeys.basesToCopy(
            all,
            legacyPresent = setOf("clients", "transactions"),
            scopedPresent = setOf("transactions")
        )
        assertEquals(listOf("clients"), result)
    }

    @Test
    fun basesToCopy_excludes_when_scoped_already_present() {
        val all = listOf("clients")
        val result = LocalKeys.basesToCopy(
            all,
            legacyPresent = setOf("clients"),
            scopedPresent = setOf("clients")
        )
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun basesToCopy_empty_when_no_legacy() {
        val all = listOf("clients", "transactions")
        assertEquals(emptyList<String>(), LocalKeys.basesToCopy(all, emptySet(), emptySet()))
    }
}
