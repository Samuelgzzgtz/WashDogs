package com.washdog.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.washdog.app.util.fuzzyContains

/**
 * Dropdown que permite tanto seleccionar de una lista como escribir libremente.
 * El filtrado es insensible a mayúsculas y acentos (normalización NFD).
 *
 * @param options        Valores almacenados al seleccionar.
 * @param displayOptions Etiquetas visibles en el menú (mismo índice que options).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WDSearchableDropdown(
    value:         String,
    onValueChange: (String) -> Unit,
    options:       List<String>,
    label:         String,
    modifier:      Modifier     = Modifier,
    displayOptions: List<String> = options,
    placeholder:   String       = "",
    leadingIcon:   ImageVector? = null
) {
    var expanded by remember { mutableStateOf(false) }

    val filtered: List<Int> = remember(value) {
        if (value.isBlank()) displayOptions.indices.toList()
        else displayOptions.indices.filter { i -> displayOptions[i].fuzzyContains(value) }
    }

    ExposedDropdownMenuBox(
        expanded         = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier         = modifier
    ) {
        OutlinedTextField(
            value         = value,
            onValueChange = { onValueChange(it); expanded = true },
            label         = { Text(label) },
            placeholder   = if (placeholder.isNotEmpty()) { { Text(placeholder) } } else null,
            trailingIcon  = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && filtered.isNotEmpty())
            },
            leadingIcon   = leadingIcon?.let { icon ->
                { androidx.compose.material3.Icon(icon, contentDescription = null) }
            },
            singleLine    = true,
            modifier      = Modifier.menuAnchor().fillMaxWidth()
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filtered.take(10).forEach { i ->
                    DropdownMenuItem(
                        text    = { Text(displayOptions[i]) },
                        onClick = {
                            onValueChange(options[i])
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
