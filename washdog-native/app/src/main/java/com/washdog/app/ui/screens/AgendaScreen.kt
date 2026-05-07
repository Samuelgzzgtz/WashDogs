package com.washdog.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.ui.components.WDMonthCalendar
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDCard
import com.washdog.app.ui.theme.WDIncome
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkFaint
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.ui.theme.WDWarn
import com.washdog.app.ui.theme.WDWarnTnt
import com.washdog.app.util.WhatsAppHelper
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDate

@Composable
fun AgendaScreen(vm: AppViewModel, onAddAppointment: (date: String) -> Unit) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 720.dp else Dp.Unspecified
    val today    = LocalDate.now()

    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = remember(selectedDay) {
        try { LocalDate.parse(selectedDay) } catch (e: Exception) { today }
    }

    val appointmentDates = remember(vm.appointments.size) {
        vm.appointments.map { it.date }.toSet()
    }
    val filtered = vm.appointments.filter { it.date == selectedDay }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter),
            contentPadding      = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "agenda_header") {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = ph),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text       = stringResource(R.string.agenda_title),
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = WDInk
                    )
                    FloatingActionButton(
                        onClick        = { onAddAppointment(selectedDay) },
                        containerColor = WDPrimary,
                        contentColor   = Color.White,
                        modifier       = Modifier.size(42.dp)
                    ) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp)) }
                }
            }

            item(key = "month_calendar") {
                Surface(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ph),
                    shape     = RoundedCornerShape(16.dp),
                    color     = WDCard,
                    tonalElevation = 2.dp
                ) {
                    WDMonthCalendar(
                        selectedDay      = selectedDate,
                        appointmentDates = appointmentDates,
                        today            = today,
                        onDaySelected    = { selectedDay = it.toString() },
                        modifier         = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }

            if (vm.clientsToRemind.isNotEmpty()) {
                item(key = "reminder_card") {
                    AgendaReminderCard(
                        client     = vm.clientsToRemind[0],
                        ph         = ph,
                        onSchedule = { onAddAppointment(selectedDay) }
                    )
                }
            }

            if (filtered.isEmpty()) {
                item(key = "empty_agenda") {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sin citas para este día", fontSize = 14.sp, color = WDInkMute)
                    }
                }
            } else {
                items(filtered, key = { it.id }) { appt ->
                    AppointmentRow(
                        appt     = appt,
                        ph       = ph,
                        onDelete = { vm.deleteAppointment(appt.id) }
                    )
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AgendaReminderCard(
    client:     com.washdog.app.data.models.Client,
    ph:         Dp,
    onSchedule: () -> Unit
) {
    val context = LocalContext.current
    val days    = try {
        LocalDate.now().toEpochDay() - LocalDate.parse(client.lastVisit).toEpochDay()
    } catch (e: Exception) { 0L }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = ph),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WDWarnTnt),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Notifications, null, tint = WDWarn, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${client.petName} · ${client.ownerName} lleva $days días sin visitar",
                    fontSize = 13.sp, color = WDInk
                )
            }
        }
        Row(
            modifier              = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick  = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phone}")))
                },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(50)
            ) { Text("Llamar", fontSize = 11.sp) }
            OutlinedButton(
                onClick  = {
                    WhatsAppHelper.send(
                        context, client.phone,
                        "Hola ${client.ownerName}, te recordamos que ${client.petName} tiene una cita próximamente."
                    )
                },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(50)
            ) { Text("WhatsApp", fontSize = 11.sp) }
            OutlinedButton(
                onClick  = onSchedule,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(50)
            ) { Text("Agendar", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun AppointmentRow(appt: Appointment, ph: Dp, onDelete: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title            = { Text("Eliminar cita") },
            text             = { Text("¿Eliminar la cita de ${appt.clientName} — ${appt.petName}?") },
            confirmButton    = {
                TextButton(onClick = { onDelete(); showDialog = false }) {
                    Text("Eliminar", color = androidx.compose.ui.graphics.Color(0xFF9F6E7E))
                }
            },
            dismissButton    = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }

    val borderColor = when (appt.status) {
        ApptStatus.DONE      -> WDPrimary
        ApptStatus.NEXT      -> WDIncome
        ApptStatus.REMINDER  -> WDWarn
        ApptStatus.SCHEDULED -> WDInkFaint
    }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = ph),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WDCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(borderColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text       = if (appt.endTime.isNotEmpty()) "${appt.time}–${appt.endTime} · ${appt.clientName}"
                                 else "${appt.time} · ${appt.clientName}",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = WDInk
                )
                Text(
                    text  = "${appt.petName} · ${appt.service}",
                    fontSize = 12.sp,
                    color = WDInkMute
                )
            }
            IconButton(
                onClick  = { showDialog = true },
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Eliminar cita",
                    tint               = WDInkMute,
                    modifier           = Modifier.size(18.dp)
                )
            }
        }
    }
}
