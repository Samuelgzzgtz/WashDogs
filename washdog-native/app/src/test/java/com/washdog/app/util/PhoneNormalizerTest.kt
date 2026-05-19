package com.washdog.app.util

import org.junit.Assert.*
import org.junit.Test

class PhoneNormalizerTest {
    @Test fun `10 digits adds +52 prefix`() = assertEquals("+525512345678", PhoneNormalizer.normalize("5512345678"))
    @Test fun `12 digits with 52 adds plus`() = assertEquals("+525512345678", PhoneNormalizer.normalize("525512345678"))
    @Test fun `plus52 12digit already correct`() = assertEquals("+525512345678", PhoneNormalizer.normalize("+525512345678"))
    @Test fun `strips spaces and dashes`() = assertEquals("+525512345678", PhoneNormalizer.normalize("+52 55 1234-5678"))
    @Test fun `strips parentheses`() = assertEquals("+525512345678", PhoneNormalizer.normalize("(55) 1234 5678"))
    @Test fun `5 digits returns null`() = assertNull(PhoneNormalizer.normalize("12345"))
    @Test fun `empty string returns null`() = assertNull(PhoneNormalizer.normalize(""))
    @Test fun `9 digits returns null`() = assertNull(PhoneNormalizer.normalize("551234567"))
}
