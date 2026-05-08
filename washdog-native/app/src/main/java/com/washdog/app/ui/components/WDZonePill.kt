package com.washdog.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.ServiceZone

@Composable
fun WDZonePill(
    zone: ServiceZone,
    fee: Double,
    onZoneChange: (ServiceZone) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Surface(
            shape    = RoundedCornerShape(50),
            color    = zone.pillColor,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text       = zone.pillLabel(fee),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
                Icon(
                    imageVector        = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded          = expanded,
            onDismissRequest  = { expanded = false }
        ) {
            ServiceZone.entries.forEach { z ->
                DropdownMenuItem(
                    text    = { Text(z.dropdownLabel) },
                    onClick = { onZoneChange(z); expanded = false }
                )
            }
        }
    }
}

val ServiceZone.pillColor: Color
    get() = when (this) {
        ServiceZone.NEAR   -> Color(0xFF4CAF50)
        ServiceZone.CLOSE  -> Color(0xFF2196F3)
        ServiceZone.MEDIUM -> Color(0xFFFFC107)
        ServiceZone.FAR    -> Color(0xFFF44336)
    }

fun ServiceZone.pillLabel(fee: Double): String = when (this) {
    ServiceZone.NEAR   -> "Granada · $0"
    ServiceZone.CLOSE  -> "Próxima · \$${fee.toInt()}"
    ServiceZone.MEDIUM -> "Media · \$${fee.toInt()}"
    ServiceZone.FAR    -> "Lejos · \$${fee.toInt()}"
}

val ServiceZone.dropdownLabel: String
    get() = when (this) {
        ServiceZone.NEAR   -> "NEAR — Privada Granada"
        ServiceZone.CLOSE  -> "CLOSE — Ensenada / Cedral / Ajusco"
        ServiceZone.MEDIUM -> "MEDIUM — Mitras Poniente"
        ServiceZone.FAR    -> "FAR — Fuera de zona"
    }
