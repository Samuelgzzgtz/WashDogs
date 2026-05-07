package com.washdog.app

import com.washdog.app.util.fuzzyContains
import com.washdog.app.util.normalized
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringUtilsTest {

    @Test fun `normalized removes accents and lowercases`() {
        assert("jose".normalized() == "jose")
        assert("José".normalized() == "jose")
        assert("ÁNGEL".normalized() == "angel")
        assert("Año".normalized() == "ano")
    }

    @Test fun `fuzzyContains finds with accents`() {
        assertTrue("Ramírez".fuzzyContains("ramirez"))
        assertTrue("ramirez".fuzzyContains("Ramírez"))
        assertTrue("Ángela".fuzzyContains("angel"))
    }

    @Test fun `fuzzyContains is case insensitive`() {
        assertTrue("Luna".fuzzyContains("luna"))
        assertTrue("LUNA".fuzzyContains("luna"))
        assertTrue("luna".fuzzyContains("LUNA"))
    }

    @Test fun `fuzzyContains returns false when not found`() {
        assertFalse("Mochi".fuzzyContains("firulais"))
    }

    @Test fun `fuzzyContains returns true on empty query`() {
        assertTrue("Luna".fuzzyContains(""))
    }
}
