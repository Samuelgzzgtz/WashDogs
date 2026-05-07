package com.washdog.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WDTimePickerDialog(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = initialTime.split(":").mapNotNull { it.toIntOrNull() }
    val state = rememberTimePickerState(
        initialHour   = parts.getOrElse(0) { 9 },
        initialMinute = parts.getOrElse(1) { 0 },
        is24Hour      = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Hora de la cita") },
        text             = { TimePicker(state = state) },
        confirmButton    = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) {
                Text("OK")
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
