package com.washdog.app.util

data class RawContact(val name: String, val phone: String)

object VcfParser {
    fun parse(text: String): List<RawContact> {
        val contacts = mutableListOf<RawContact>()
        var name  = ""
        var phone = ""
        for (line in text.lines()) {
            when {
                line.startsWith("FN:")  -> name  = line.removePrefix("FN:").trim()
                line.startsWith("TEL")  -> phone = line.substringAfter(":").trim()
                line.startsWith("END:VCARD") -> {
                    if (name.isNotBlank() && phone.isNotBlank())
                        contacts += RawContact(name, phone)
                    name = ""; phone = ""
                }
            }
        }
        return contacts
    }
}
