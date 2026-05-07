package com.washdog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.ui.theme.WDWarn

private val SLOT_H: Dp = 64.dp
private const val FIRST_H = 8
private const val LAST_H  = 21
private val LABEL_W: Dp = 44.dp

@Composable
fun WDTimeline(
    appointments: List<Appointment>,
    modifier: Modifier = Modifier
) {
    val totalH = SLOT_H * (LAST_H - FIRST_H)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalH)
    ) {
        Column(Modifier.fillMaxWidth().height(totalH)) {
            (FIRST_H until LAST_H).forEach { hour ->
                Row(
                    modifier          = Modifier.fillMaxWidth().height(SLOT_H),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text     = "%02d:00".format(hour),
                        fontSize = 10.sp,
                        color    = WDInkMute,
                        modifier = Modifier
                            .width(LABEL_W)
                            .padding(start = 4.dp, top = 2.dp)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0x08000000))
                    )
                }
            }
        }

        appointments.forEach { appt ->
            val startMin = toMin(appt.time) ?: return@forEach
            if (startMin < FIRST_H * 60 || startMin >= LAST_H * 60) return@forEach

            val endMin   = toMin(appt.endTime)
                .takeIf { appt.endTime.isNotEmpty() } ?: (startMin + 60)
            val topDp    = SLOT_H * ((startMin - FIRST_H * 60).toFloat() / 60f)
            val heightDp = (SLOT_H * ((endMin - startMin).toFloat() / 60f))
                .coerceAtLeast(44.dp)

            Box(
                modifier = Modifier
                    .padding(start = LABEL_W + 4.dp, end = 8.dp)
                    .absoluteOffset(y = topDp)
                    .fillMaxWidth()
                    .height(heightDp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(appt.status.blockColor())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(
                        text     = if (appt.endTime.isNotEmpty()) "${appt.time}–${appt.endTime}"
                                   else appt.time,
                        fontSize = 10.sp,
                        color    = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text       = appt.clientName,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                    Text(
                        text     = appt.service,
                        fontSize = 10.sp,
                        color    = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun toMin(time: String): Int? {
    if (time.isBlank()) return null
    val parts = time.split(":").mapNotNull { it.toIntOrNull() }
    return if (parts.size == 2) parts[0] * 60 + parts[1] else null
}

private fun ApptStatus.blockColor(): Color = when (this) {
    ApptStatus.DONE      -> Color(0xFF9E9E9E)
    ApptStatus.NEXT      -> WDPrimary
    ApptStatus.SCHEDULED -> Color(0xFF5B8C7E)
    ApptStatus.REMINDER  -> WDWarn
}
