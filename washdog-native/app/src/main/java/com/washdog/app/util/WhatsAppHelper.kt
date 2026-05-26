package com.washdog.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object WhatsAppHelper {
    fun send(context: Context, phone: String, message: String) {
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
        val uri    = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                context, "WhatsApp no está instalado", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun interpolate(template: String, name: String, fecha: String = "", hora: String = ""): String =
        template
            .replace("{{nombre}}", name)
            .replace("{{fecha}}", fecha)
            .replace("{{hora}}", hora)
}
