package com.washdog.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object PhoneHelper {
    /**
     * Abre el marcador con el número. En dispositivos sin app de teléfono (p. ej. una
     * tablet solo-WiFi) no hay actividad que maneje ACTION_DIAL, así que se captura la
     * excepción y se avisa en vez de cerrar la app.
     */
    fun dial(context: Context, phone: String) {
        val clean  = phone.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                context, "Este dispositivo no puede hacer llamadas", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
