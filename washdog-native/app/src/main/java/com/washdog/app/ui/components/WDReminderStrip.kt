package com.washdog.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.Client
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDWarn
import com.washdog.app.ui.theme.WDWarnTnt

@Composable
fun WDReminderStrip(clients: List<Client>, onClick: () -> Unit = {}) {
    if (clients.isEmpty()) return
    val first = clients[0]
    val days = try {
        java.time.LocalDate.now().toEpochDay() - java.time.LocalDate.parse(first.lastVisit).toEpochDay()
    } catch (e: Exception) { 0L }
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WDWarnTnt),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Notifications, null, tint = WDWarn, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(text = "${first.petName} · ${first.ownerName} lleva $days días sin visitar", fontSize = 13.sp, color = WDInk)
                if (clients.size > 1) {
                    Text(text = "+${clients.size - 1} más", fontSize = 12.sp, color = WDInkMute)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = WDInkMute, modifier = Modifier.size(18.dp))
        }
    }
}
