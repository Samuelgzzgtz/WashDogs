package com.washdog.app.util

object PhoneNormalizer {
    fun normalize(raw: String): String? {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when (digits.length) {
            10   -> "+52$digits"
            12   -> if (digits.startsWith("52")) "+$digits" else null
            else -> null
        }
    }
}
