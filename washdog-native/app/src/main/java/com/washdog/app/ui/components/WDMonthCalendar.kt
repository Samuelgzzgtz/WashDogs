package com.washdog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY_HEADERS = listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sa", "Do")
private val MONTH_FMT   = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es"))

@Composable
fun WDMonthCalendar(
    selectedDay:      LocalDate,
    appointmentDates: Set<String>,
    today:            LocalDate = LocalDate.now(),
    onDaySelected:    (LocalDate) -> Unit,
    modifier:         Modifier = Modifier
) {
    var viewMonth by remember(selectedDay) { mutableStateOf(YearMonth.from(selectedDay)) }

    Column(modifier) {
        MonthHeader(
            viewMonth  = viewMonth,
            onPrev     = { viewMonth = viewMonth.minusMonths(1) },
            onNext     = { viewMonth = viewMonth.plusMonths(1) }
        )
        Spacer(Modifier.height(4.dp))
        DayOfWeekRow()
        Spacer(Modifier.height(2.dp))
        MonthGrid(
            viewMonth        = viewMonth,
            selectedDay      = selectedDay,
            appointmentDates = appointmentDates,
            today            = today,
            onDaySelected    = onDaySelected
        )
    }
}

@Composable
private fun MonthHeader(viewMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior", tint = WDInk)
        }
        Text(
            text       = viewMonth.format(MONTH_FMT).replaceFirstChar { it.uppercase() },
            modifier   = Modifier.weight(1f),
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color      = WDInk,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente", tint = WDInk)
        }
    }
}

@Composable
private fun DayOfWeekRow() {
    Row(Modifier.fillMaxWidth()) {
        DAY_HEADERS.forEach { header ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(header, fontSize = 11.sp, color = WDInkMute, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MonthGrid(
    viewMonth:        YearMonth,
    selectedDay:      LocalDate,
    appointmentDates: Set<String>,
    today:            LocalDate,
    onDaySelected:    (LocalDate) -> Unit
) {
    // ISO: Monday = 1 → offset 0; Sunday = 7 → offset 6
    val startOffset  = viewMonth.atDay(1).dayOfWeek.value - 1
    val daysInMonth  = viewMonth.lengthOfMonth()
    val totalCells   = startOffset + daysInMonth
    val rows         = (totalCells + 6) / 7

    repeat(rows) { row ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { col ->
                val dayNum = row * 7 + col - startOffset + 1
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (dayNum in 1..daysInMonth) {
                        val date       = viewMonth.atDay(dayNum)
                        val isSelected = date == selectedDay
                        val isToday    = date == today
                        val hasAppt    = date.toString() in appointmentDates

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.clickable { onDaySelected(date) }
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> WDPrimary
                                            else       -> Color.Transparent
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = dayNum.toString(),
                                    fontSize   = 13.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color      = when {
                                        isSelected -> Color.White
                                        isToday    -> WDPrimary
                                        else       -> WDInk
                                    }
                                )
                            }
                            Box(Modifier.height(5.dp), contentAlignment = Alignment.Center) {
                                if (hasAppt) {
                                    Box(
                                        Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else WDPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
