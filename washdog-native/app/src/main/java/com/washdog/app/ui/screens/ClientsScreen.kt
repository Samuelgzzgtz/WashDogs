package com.washdog.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.ui.components.WDIconBox
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDCard
import com.washdog.app.ui.theme.WDExpenseDk
import com.washdog.app.ui.theme.WDExpenseTnt
import com.washdog.app.ui.theme.WDIncomeDk
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.ui.theme.WDPrimaryTnt
import com.washdog.app.util.WhatsAppHelper
import com.washdog.app.util.fuzzyContains
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun ClientsScreen(vm: AppViewModel, onNavigateSettings: () -> Unit, onAddClient: () -> Unit, onEditClient: (clientId: String) -> Unit) {
    val config      = LocalConfiguration.current
    val isTablet    = config.screenWidthDp >= 600
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ph          = if (isTablet) 24.dp else 16.dp
    val maxW        = if (isTablet) 800.dp else Dp.Unspecified
    val context     = LocalContext.current
    var query       by remember { mutableStateOf("") }

    val filtered   = vm.clients.filter {
        it.ownerName.fuzzyContains(query) ||
        it.petName.fuzzyContains(query)   ||
        it.breed.fuzzyContains(query)
    }
    val reschedule = filtered.filter { it.needsReschedule }
    val rest       = filtered.filter { !it.needsReschedule }

    fun sendWelcome(client: Client) {
        if (client.phone.isBlank()) return
        val msg = WhatsAppHelper.interpolate(vm.tplWelcome, client.ownerName)
        WhatsAppHelper.send(context, client.phone, msg)
    }

    fun sendConfirm(client: Client) {
        if (client.phone.isBlank()) return
        val appt = vm.appointments.firstOrNull {
            it.clientId == client.id || (it.clientId.isBlank() && it.clientName == client.ownerName)
        }
        val msg  = WhatsAppHelper.interpolate(
            vm.tplConfirm, client.ownerName,
            appt?.date ?: "", appt?.time ?: ""
        )
        WhatsAppHelper.send(context, client.phone, msg)
    }

    fun sendReschedule(client: Client) {
        if (client.phone.isBlank()) return
        val msg = WhatsAppHelper.interpolate(vm.tplReschedule, client.ownerName)
        WhatsAppHelper.send(context, client.phone, msg)
    }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter),
            contentPadding      = PaddingValues(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "clients_header") {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.clients_title),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WDInk
                    )
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_gear),
                            tint = WDInkMute
                        )
                    }
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value         = query,
                    onValueChange = { query = it },
                    placeholder   = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon   = { Icon(Icons.Filled.Search, null) },
                    trailingIcon  = { Icon(Icons.Filled.FilterList, null) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(16.dp),
                    singleLine    = true
                )
            }
            if (reschedule.isNotEmpty()) {
                item(key = "reschedule_label") {
                    Text(
                        stringResource(R.string.reschedule_section),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WDInkMute
                    )
                }
                items(reschedule, key = { "r_${it.id}" }) { client ->
                    ClientRow(
                        client      = client,
                        isLandscape = isLandscape,
                        onWelcome   = { sendWelcome(client) },
                        onConfirm   = { sendConfirm(client) },
                        onReschedule = { sendReschedule(client) },
                        onEdit      = { onEditClient(client.id) }
                    )
                }
            }
            item(key = "all_label") {
                Text(
                    stringResource(R.string.all_clients_section),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WDInkMute
                )
            }
            if (filtered.isEmpty()) {
                item(key = "empty_clients") {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se encontraron clientes", fontSize = 14.sp, color = WDInkMute)
                    }
                }
            }
            items(rest, key = { "a_${it.id}" }) { client ->
                ClientRow(
                    client      = client,
                    isLandscape = isLandscape,
                    onWelcome   = { sendWelcome(client) },
                    onConfirm   = { sendConfirm(client) },
                    onReschedule = { sendReschedule(client) },
                    onEdit      = { onEditClient(client.id) }
                )
            }
            item(key = "bottom_space") { Spacer(Modifier.height(80.dp)) }
        }
        FloatingActionButton(
            onClick        = onAddClient,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp),
            containerColor = WDPrimary,
            contentColor   = Color.White
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
private fun ClientRow(
    client: Client,
    isLandscape: Boolean,
    onWelcome: () -> Unit,
    onConfirm: () -> Unit,
    onReschedule: () -> Unit,
    onEdit: () -> Unit
) {
    val petIcon  = if (client.petType == PetType.DOG) Icons.Filled.Pets     else Icons.Filled.Favorite
    val iconBg   = if (client.petType == PetType.DOG) WDPrimaryTnt           else WDExpenseTnt
    val iconTint = if (client.petType == PetType.DOG) WDPrimary              else WDExpenseDk

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WDCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        if (isLandscape) {
            Row(
                modifier              = Modifier.padding(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WDIconBox(icon = petIcon, tint = iconTint, background = iconBg)
                ClientInfo(client = client, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint               = WDInkMute,
                        modifier           = Modifier.size(20.dp)
                    )
                }
                WaButtons(
                    onWelcome    = onWelcome,
                    onConfirm    = onConfirm,
                    onReschedule = onReschedule
                )
            }
        } else {
            Column(Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WDIconBox(icon = petIcon, tint = iconTint, background = iconBg)
                    ClientInfo(client = client, modifier = Modifier.weight(1f))
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint               = WDInkMute,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                WaButtons(
                    onWelcome    = onWelcome,
                    onConfirm    = onConfirm,
                    onReschedule = onReschedule,
                    modifier     = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ClientInfo(client: Client, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = client.petName,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = WDInk
            )
            if (client.needsReschedule) {
                Surface(shape = RoundedCornerShape(50), color = WDExpenseTnt) {
                    Text(
                        text     = "REAGENDAR",
                        fontSize = 9.sp,
                        color    = WDExpenseDk,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            text     = "${client.ownerName} · ${client.breed}",
            fontSize = 12.sp,
            color    = WDInkMute
        )
    }
}

@Composable
private fun WaButtons(
    onWelcome: () -> Unit,
    onConfirm: () -> Unit,
    onReschedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WaButton(
            text     = stringResource(R.string.wa_welcome),
            color    = WDPrimary,
            onClick  = onWelcome,
            modifier = Modifier.weight(1f)
        )
        WaButton(
            text     = stringResource(R.string.wa_confirm),
            color    = WDIncomeDk,
            onClick  = onConfirm,
            modifier = Modifier.weight(1f)
        )
        WaButton(
            text     = stringResource(R.string.wa_reschedule),
            color    = WDExpenseDk,
            onClick  = onReschedule,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WaButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick         = onClick,
        modifier        = modifier.height(38.dp),
        shape           = RoundedCornerShape(10.dp),
        contentPadding  = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        border          = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        colors          = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = color
        )
    ) {
        Text(
            text     = text,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
