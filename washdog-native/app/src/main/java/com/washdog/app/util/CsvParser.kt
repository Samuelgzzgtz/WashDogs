package com.washdog.app.util

object CsvParser {
    private val NAME_HEADERS  = setOf("name","nombre","full name","nombre completo")
    private val PHONE_HEADERS = setOf("phone","telefono","teléfono","tel","mobile","celular","movil","móvil")

    fun parse(text: String): List<RawContact> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header   = lines.first().split(",").map { it.trim().lowercase() }
        val nameIdx  = header.indexOfFirst { it in NAME_HEADERS }
        val phoneIdx = header.indexOfFirst { it in PHONE_HEADERS }
        if (nameIdx < 0 || phoneIdx < 0) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val cols  = line.split(",")
            val name  = cols.getOrNull(nameIdx)?.trim()  ?: return@mapNotNull null
            val phone = cols.getOrNull(phoneIdx)?.trim() ?: return@mapNotNull null
            if (name.isBlank() || phone.isBlank()) null else RawContact(name, phone)
        }
    }
}
