package com.washdog.app

import com.washdog.app.ui.components.digitsToPhone
import com.washdog.app.ui.components.phoneToDigits
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneFormatTest {

    @Test fun `digitsToPhone prepends +52`() {
        assertEquals("+521234567890", digitsToPhone("1234567890"))
    }

    @Test fun `digitsToPhone with empty digits returns empty`() {
        assertEquals("", digitsToPhone(""))
    }

    @Test fun `phoneToDigits strips +52 prefix`() {
        assertEquals("1234567890", phoneToDigits("+521234567890"))
    }

    @Test fun `phoneToDigits strips +52 with space`() {
        assertEquals("1234567890", phoneToDigits("+52 1234567890"))
    }

    @Test fun `phoneToDigits handles missing prefix`() {
        assertEquals("1234567890", phoneToDigits("1234567890"))
    }

    @Test fun `phoneToDigits removes non-digits`() {
        assertEquals("1234567890", phoneToDigits("+52-123-456-7890"))
    }
}
