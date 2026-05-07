package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
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

            Button(
                onClick  = {
                    vm.updateTemplates(welcome, confirm, reschedule)
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
