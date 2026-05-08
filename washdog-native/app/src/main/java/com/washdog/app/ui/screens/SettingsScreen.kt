package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.BusinessHours
import com.washdog.app.data.models.ZonePrices
import com.washdog.app.ui.components.WDTimePickerDialog
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDBgAlt
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.ui.theme.WDPrimaryTnt
import com.washdog.app.util.WhatsAppHelper
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 720.dp else Dp.Unspecified

    var welcome    by remember { mutableStateOf(vm.tplWelcome) }
    var confirm    by remember { mutableStateOf(vm.tplConfirm) }
    var reschedule by remember { mutableStateOf(vm.tplReschedule) }

    // Horarios
    var openTime   by remember { mutableStateOf(vm.businessHours.openTime) }
    var closeTime  by remember { mutableStateOf(vm.businessHours.closeTime) }
    var closedDays by remember { mutableStateOf(vm.businessHours.closedDays.toSet()) }
    var showOpenPicker  by remember { mutableStateOf(false) }
    var showClosePicker by remember { mutableStateOf(false) }

    // Precios por zona
    var nearFee      by remember { mutableStateOf(vm.zonePrices.nearFee.toInt().toString()) }
    var nearBuffer   by remember { mutableStateOf(vm.zonePrices.nearBuffer.toString()) }
    var closeFee     by remember { mutableStateOf(vm.zonePrices.closeFee.toInt().toString()) }
    var closeBuffer  by remember { mutableStateOf(vm.zonePrices.closeBuffer.toString()) }
    var mediumFee    by remember { mutableStateOf(vm.zonePrices.mediumFee.toInt().toString()) }
    var mediumBuffer by remember { mutableStateOf(vm.zonePrices.mediumBuffer.toString()) }
    var farFee       by remember { mutableStateOf(vm.zonePrices.farFee.toInt().toString()) }
    var farBuffer    by remember { mutableStateOf(vm.zonePrices.farBuffer.toString()) }

    if (showOpenPicker) {
        WDTimePickerDialog(
            initialTime = openTime,
            onConfirm   = { openTime = it; showOpenPicker = false },
            onDismiss   = { showOpenPicker = false }
        )
    }
    if (showClosePicker) {
        WDTimePickerDialog(
            initialTime = closeTime,
            onConfirm   = { closeTime = it; showClosePicker = false },
            onDismiss   = { showClosePicker = false }
        )
    }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, null, tint = WDInk)
                }
                Text(
                    stringResource(R.string.settings_title),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = WDInk
                )
            }

            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = WDPrimaryTnt),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Text(
                    stringResource(R.string.tpl_variables_hint),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color    = WDPrimary
                )
            }

            TemplateField(
                label    = stringResource(R.string.tpl_welcome_label),
                value    = welcome,
                onChange = { welcome = it },
                preview  = WhatsAppHelper.interpolate(welcome, "Juan Pérez")
            )

            TemplateField(
                label    = stringResource(R.string.tpl_confirm_label),
                value    = confirm,
                onChange = { confirm = it },
                preview  = WhatsAppHelper.interpolate(confirm, "Juan Pérez", "lunes 28 de abril", "10:00")
            )

            TemplateField(
                label    = stringResource(R.string.tpl_reschedule_label),
                value    = reschedule,
                onChange = { reschedule = it },
                preview  = WhatsAppHelper.interpolate(reschedule, "Juan Pérez")
            )

            // ── Horario de negocio ────────────────────────────────────────────
            Text(
                "Horario de negocio",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WDInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Apertura", fontSize = 12.sp, color = WDInkMute)
                    Box {
                        OutlinedTextField(
                            value = openTime, onValueChange = {},
                            readOnly = true, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Filled.Schedule, null, tint = WDInkMute) }
                        )
                        Box(Modifier.matchParentSize().clickable { showOpenPicker = true })
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cierre", fontSize = 12.sp, color = WDInkMute)
                    Box {
                        OutlinedTextField(
                            value = closeTime, onValueChange = {},
                            readOnly = true, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Filled.Schedule, null, tint = WDInkMute) }
                        )
                        Box(Modifier.matchParentSize().clickable { showClosePicker = true })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Días cerrados", fontSize = 12.sp, color = WDInkMute)
                val dayLabels = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = idx in closedDays,
                            onClick  = {
                                closedDays = if (idx in closedDays) closedDays - idx else closedDays + idx
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WDPrimary,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
            }

            // ── Precios por zona ──────────────────────────────────────────────
            Text(
                "Precios por zona",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WDInk
            )
            ZonePriceRow("Granada (NEAR)",  nearFee,   { nearFee   = it }, nearBuffer,   { nearBuffer   = it })
            ZonePriceRow("Próxima (CLOSE)", closeFee,  { closeFee  = it }, closeBuffer,  { closeBuffer  = it })
            ZonePriceRow("Media (MEDIUM)",  mediumFee, { mediumFee = it }, mediumBuffer, { mediumBuffer = it })
            ZonePriceRow("Lejos (FAR)",     farFee,    { farFee    = it }, farBuffer,    { farBuffer    = it })

            Button(
                onClick  = {
                    vm.updateTemplates(welcome, confirm, reschedule)
                    vm.updateBusinessHours(
                        BusinessHours(
                            openTime   = openTime,
                            closeTime  = closeTime,
                            closedDays = closedDays.toList()
                        )
                    )
                    vm.updateZonePrices(
                        ZonePrices(
                            nearFee      = nearFee.toDoubleOrNull()   ?: 0.0,
                            nearBuffer   = nearBuffer.toIntOrNull()   ?: 0,
                            closeFee     = closeFee.toDoubleOrNull()  ?: 50.0,
                            closeBuffer  = closeBuffer.toIntOrNull()  ?: 10,
                            mediumFee    = mediumFee.toDoubleOrNull() ?: 90.0,
                            mediumBuffer = mediumBuffer.toIntOrNull() ?: 25,
                            farFee       = farFee.toDoubleOrNull()    ?: 120.0,
                            farBuffer    = farBuffer.toIntOrNull()    ?: 40
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
            ) {
                Text(
                    stringResource(R.string.tpl_save),
                    color      = Color.White,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TemplateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    preview: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WDInk)
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            minLines      = 2
        )
        Card(
            shape  = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = WDBgAlt),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    stringResource(R.string.tpl_preview_title),
                    fontSize = 10.sp,
                    color    = WDInkMute
                )
                Text(preview, fontSize = 13.sp, color = WDInk)
            }
        }
    }
}

@Composable
private fun ZonePriceRow(
    label: String,
    fee: String,   onFeeChange: (String) -> Unit,
    buf: String,   onBufChange: (String) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontSize = 12.sp, color = WDInkMute, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value           = fee,
            onValueChange   = onFeeChange,
            label           = { Text("$") },
            modifier        = Modifier.widthIn(min = 80.dp, max = 80.dp),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value           = buf,
            onValueChange   = onBufChange,
            label           = { Text("min") },
            modifier        = Modifier.widthIn(min = 70.dp, max = 70.dp),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
