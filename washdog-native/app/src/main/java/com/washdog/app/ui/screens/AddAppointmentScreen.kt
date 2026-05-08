package com.washdog.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.WD_SERVICES
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.ApptStatus
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.ServiceLocation
import com.washdog.app.data.models.ServiceZone
import com.washdog.app.data.models.ZonePrices
import com.washdog.app.ui.components.WDSearchableDropdown
import com.washdog.app.ui.components.WDTimePickerDialog
import com.washdog.app.ui.components.pillColor
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDBgAlt
import com.washdog.app.ui.theme.WDExpenseDk
import com.washdog.app.ui.theme.WDExpenseTnt
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.ui.theme.WDPrimaryTnt
import com.washdog.app.util.ProximityEngine
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun AddAppointmentScreen(vm: AppViewModel, initialDate: String, onBack: () -> Unit) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 600.dp else androidx.compose.ui.unit.Dp.Unspecified

    var clientQuery     by remember { mutableStateOf("") }
    var selectedClient  by remember { mutableStateOf<Client?>(null) }
    var service         by remember { mutableStateOf("") }
    var date            by remember { mutableStateOf(initialDate) }
    var time            by remember { mutableStateOf("") }
    var showTimePicker  by remember { mutableStateOf(false) }
    var endTime         by remember { mutableStateOf("") }
    var showEndPicker   by remember { mutableStateOf(false) }
    var endTimeEnabled  by remember { mutableStateOf(false) }
    var serviceLocation by remember { mutableStateOf(ServiceLocation.HOME) }

    val clientNames    = vm.clients.map { "${it.petName} · ${it.ownerName}" }
    val serviceNames   = WD_SERVICES.map { it.name }
    val serviceLabels  = WD_SERVICES.map { it.label }

    if (showTimePicker) {
        WDTimePickerDialog(
            initialTime = time,
            onConfirm   = { time = it; showTimePicker = false },
            onDismiss   = { showTimePicker = false }
        )
    }

    if (showEndPicker) {
        WDTimePickerDialog(
            initialTime = endTime,
            onConfirm   = { endTime = it; showEndPicker = false },
            onDismiss   = { showEndPicker = false }
        )
    }

    LaunchedEffect(time, date, endTime, endTimeEnabled) {
        val client = selectedClient ?: vm.clients.firstOrNull {
            "${it.petName} · ${it.ownerName}" == clientQuery
        }
        val candidate = Appointment(
            id         = "",
            clientName = client?.ownerName ?: "",
            petName    = client?.petName ?: "",
            service    = service,
            time       = time,
            date       = date,
            status     = ApptStatus.SCHEDULED,
            endTime    = if (endTimeEnabled) endTime else ""
        )
        vm.checkOverlap(candidate)
    }

    val conflict = vm.overlapConflict

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter),
            contentPadding      = PaddingValues(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null, tint = WDInk)
                    }
                    Text(
                        text       = "Nueva cita",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = WDInk
                    )
                }
            }

            if (vm.clients.isEmpty()) {
                item(key = "no_clients_warning") {
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = CardDefaults.cardColors(containerColor = WDExpenseTnt)
                    ) {
                        Text(
                            text     = "No hay clientes registrados. Agrega uno desde la pestaña Clientes.",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp,
                            color    = WDExpenseDk
                        )
                    }
                }
            }

            // Cliente — searchable dropdown
            item(key = "client_dropdown") {
                WDSearchableDropdown(
                    value         = clientQuery,
                    onValueChange = { typed ->
                        clientQuery    = typed
                        selectedClient = null
                    },
                    options        = clientNames,
                    displayOptions = clientNames,
                    label          = "Cliente",
                    placeholder    = "Buscar por nombre o mascota…",
                    modifier       = Modifier.fillMaxWidth()
                )
                // Sync selection when user picks from dropdown
                val matched = vm.clients.firstOrNull { "${it.petName} · ${it.ownerName}" == clientQuery }
                if (matched != null && selectedClient?.id != matched.id) {
                    selectedClient = matched
                }
            }

            if (selectedClient != null) {
                item(key = "zone_card") {
                    ZoneContextCard(
                        zone             = selectedClient!!.zone,
                        zonePrices       = vm.zonePrices,
                        serviceLocation  = serviceLocation,
                        onLocationChange = { serviceLocation = it }
                    )
                }
            }

            // Mascota (solo lectura, rellena al elegir cliente)
            item(key = "pet_field") {
                OutlinedTextField(
                    value         = selectedClient?.petName ?: "",
                    onValueChange = {},
                    label         = { Text("Mascota") },
                    enabled       = false,
                    modifier      = Modifier.fillMaxWidth()
                )
            }

            // Servicio — searchable dropdown con catálogo oficial
            item(key = "service_dropdown") {
                WDSearchableDropdown(
                    value          = service,
                    onValueChange  = { service = it },
                    options        = serviceNames,
                    displayOptions = serviceLabels,
                    label          = "Servicio",
                    placeholder    = "Baño, Corte, Uñas…",
                    modifier       = Modifier.fillMaxWidth()
                )
            }

            // Fecha
            item(key = "date_field") {
                OutlinedTextField(
                    value         = date,
                    onValueChange = { date = it },
                    label         = { Text("Fecha") },
                    placeholder   = { Text("yyyy-mm-dd") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            }

            // Hora de inicio
            item(key = "time_field") {
                Box {
                    OutlinedTextField(
                        value         = time,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Hora de inicio") },
                        placeholder   = { Text("Toca para seleccionar") },
                        trailingIcon  = { Icon(Icons.Filled.Schedule, null, tint = WDInkMute) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { showTimePicker = true })
                }
            }

            if (time.isNotBlank() && date.isNotBlank()) {
                item(key = "hours_warning_check") {
                    val dateOk = remember(date) {
                        try { java.time.LocalDate.parse(date); true } catch (e: Exception) { false }
                    }
                    if (dateOk && !vm.isWithinBusinessHours(time, date)) {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = CardDefaults.cardColors(containerColor = WDExpenseTnt)
                        ) {
                            Text(
                                text     = "Fuera del horario de negocio (${vm.businessHours.openTime}–${vm.businessHours.closeTime})",
                                fontSize = 12.sp,
                                color    = WDExpenseDk,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Toggle fin tentativo + hora fin
            item(key = "end_time_row") {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked         = endTimeEnabled,
                        onCheckedChange = { checked ->
                            endTimeEnabled = checked
                            if (!checked) endTime = ""
                        }
                    )
                    Text("Fin tentativo", fontSize = 13.sp, color = WDInkMute)
                    if (endTimeEnabled) {
                        Spacer(Modifier.weight(1f))
                        Box {
                            OutlinedTextField(
                                value         = endTime,
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text("Hora fin") },
                                trailingIcon  = { Icon(Icons.Filled.Schedule, null, tint = WDInkMute) },
                                singleLine    = true,
                                modifier      = Modifier.widthIn(min = 150.dp)
                            )
                            Box(Modifier.matchParentSize().clickable { showEndPicker = true })
                        }
                    }
                }
            }

            if (conflict != null) {
                item(key = "overlap_error") {
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        colors   = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = WDExpenseTnt
                        )
                    ) {
                        Text(
                            text     = "Conflicto: ${conflict.clientName} · ${conflict.petName} ya tiene cita a las ${conflict.time}",
                            fontSize = 12.sp,
                            color    = WDExpenseDk,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item(key = "save_btn") {
                Spacer(Modifier.height(4.dp))
                val dateValid = remember(date) {
                    try { java.time.LocalDate.parse(date); true } catch (e: Exception) { false }
                }
                val fee    = if (selectedClient != null) ProximityEngine.travelFee(selectedClient!!.zone, serviceLocation, vm.zonePrices) else 0.0
                val buffer = if (selectedClient != null) ProximityEngine.travelBuffer(selectedClient!!.zone, serviceLocation, vm.zonePrices) else 0
                Button(
                    onClick = {
                        val client = selectedClient ?: vm.clients.firstOrNull {
                            "${it.petName} · ${it.ownerName}" == clientQuery
                        }
                        if (client != null && service.isNotBlank() && time.isNotBlank() && dateValid && conflict == null) {
                            vm.addAppointment(
                                Appointment(
                                    id              = java.util.UUID.randomUUID().toString(),
                                    clientName      = client.ownerName,
                                    petName         = client.petName,
                                    service         = service,
                                    time            = time,
                                    date            = date,
                                    status          = ApptStatus.SCHEDULED,
                                    endTime         = if (endTimeEnabled) endTime else "",
                                    serviceLocation = serviceLocation,
                                    travelFee       = fee,
                                    travelBuffer    = buffer
                                )
                            )
                            onBack()
                        }
                    },
                    enabled  = conflict == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary, contentColor = Color.White)
                ) { Text("Guardar cita") }
            }
        }
    }
}

@Composable
private fun ZoneContextCard(
    zone: ServiceZone,
    zonePrices: ZonePrices,
    serviceLocation: ServiceLocation,
    onLocationChange: (ServiceLocation) -> Unit
) {
    val borderColor = zone.pillColor
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = borderColor.copy(alpha = 0.08f)),
        border   = BorderStroke(1.dp, borderColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val homeFee    = ProximityEngine.travelFee(zone, ServiceLocation.HOME, zonePrices)
            val homeBuffer = ProximityEngine.travelBuffer(zone, ServiceLocation.HOME, zonePrices)
            Text(
                text = when (zone) {
                    ServiceZone.NEAR   -> "Privada Granada · Sin cargo de traslado"
                    ServiceZone.CLOSE  -> "Zona próxima · ${homeFee.toInt()} MXN traslado"
                    ServiceZone.MEDIUM -> "Zona media · ${homeFee.toInt()} MXN traslado"
                    ServiceZone.FAR    -> "Zona lejana · ${homeFee.toInt()} MXN traslado"
                },
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = WDInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocationOption(
                    selected  = serviceLocation == ServiceLocation.HOME,
                    icon      = "🏠",
                    label     = "A domicilio",
                    feeText   = if (homeFee > 0) "+$${homeFee.toInt()} · +${homeBuffer}min" else "Sin cargo",
                    onClick   = { onLocationChange(ServiceLocation.HOME) },
                    modifier  = Modifier.weight(1f)
                )
                LocationOption(
                    selected  = serviceLocation == ServiceLocation.BASE,
                    icon      = "🏪",
                    label     = "Trae a la base",
                    feeText   = "Sin cargo",
                    onClick   = { onLocationChange(ServiceLocation.BASE) },
                    modifier  = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LocationOption(
    selected: Boolean,
    icon: String,
    label: String,
    feeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape    = RoundedCornerShape(10.dp),
        color    = if (selected) WDPrimaryTnt else WDBgAlt,
        border   = BorderStroke(1.dp, if (selected) WDPrimary else WDInkMute)
    ) {
        Column(
            modifier            = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(label,   fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WDInk)
            Text(feeText, fontSize = 10.sp, color = if (selected) WDPrimary else WDInkMute)
        }
    }
}
