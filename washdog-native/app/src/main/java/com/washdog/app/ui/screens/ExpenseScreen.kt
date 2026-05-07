package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import com.washdog.app.ui.components.WDKeypad
import com.washdog.app.ui.components.WDSearchableDropdown
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDBgAlt
import com.washdog.app.ui.theme.WDExpense
import com.washdog.app.ui.theme.WDExpenseDk
import com.washdog.app.ui.theme.WDExpenseTnt
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDateTime
import java.util.UUID

private data class ExpenseCategory(val name: String, val suggestions: List<String>)

private val EXPENSE_CATEGORIES = listOf(
    ExpenseCategory("Insumos",    listOf("Shampoo", "Acondicionador", "Perfume", "Toallas", "Guantes")),
    ExpenseCategory("Equipos",    listOf("Secadora", "Tijeras", "Cortadora", "Tina", "Mesa de grooming")),
    ExpenseCategory("Arriendo",   listOf("Renta local", "Renta mensual")),
    ExpenseCategory("Servicios",  listOf("Agua", "Luz", "Internet", "Gas")),
    ExpenseCategory("Nómina",     listOf("Sueldo empleado", "Pago asistente")),
    ExpenseCategory("Marketing",  listOf("Redes sociales", "Tarjetas", "Volantes")),
    ExpenseCategory("Transporte", listOf("Gasolina", "Uber", "Mantenimiento")),
    ExpenseCategory("Otro",       emptyList()),
)

private val ALL_SUGGESTIONS = EXPENSE_CATEGORIES.flatMap { it.suggestions } + EXPENSE_CATEGORIES.map { it.name }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseScreen(vm: AppViewModel, onBack: () -> Unit) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val fs       = if (isTablet) 2f else 0f
    val maxW     = if (isTablet) 720.dp else Dp.Unspecified

    var amountStr    by remember { mutableStateOf("0") }
    var conceptQuery by remember { mutableStateOf("") }
    var selectedCat  by remember { mutableStateOf("") }
    var note         by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(horizontal = ph)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, null, tint = WDInk)
                }
                Text(
                    stringResource(R.string.expense_title),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WDInk
                )
            }

            // Monto grande
            Text(
                text       = "$$amountStr",
                fontSize   = (64 + fs).sp,
                fontWeight = FontWeight.Bold,
                color      = WDExpenseDk,
                modifier   = Modifier.align(Alignment.CenterHorizontally)
            )

            // Concepto — dropdown con búsqueda libre
            WDSearchableDropdown(
                value          = conceptQuery,
                onValueChange  = { conceptQuery = it },
                options        = ALL_SUGGESTIONS,
                label          = "Concepto",
                placeholder    = "Ej. Shampoo, Renta, Gasolina…",
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ph)
            )

            // Categorías rápidas
            Surface(
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ph),
                shape          = RoundedCornerShape(14.dp),
                color          = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Categoría",
                        fontSize   = 11.sp,
                        color      = WDInkMute,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp)
                    ) {
                        EXPENSE_CATEGORIES.forEach { cat ->
                            val isSelected = selectedCat == cat.name
                            FilterChip(
                                selected = isSelected,
                                onClick  = {
                                    selectedCat  = cat.name
                                    if (conceptQuery.isBlank()) conceptQuery = cat.name
                                },
                                label  = { Text(cat.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WDExpenseTnt,
                                    selectedLabelColor     = WDExpenseDk
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                    // Sugerencias de la categoría seleccionada
                    val hints = EXPENSE_CATEGORIES.firstOrNull { it.name == selectedCat }?.suggestions
                    if (!hints.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Accesos rápidos",
                            fontSize   = 10.sp,
                            color      = WDInkMute,
                            modifier   = Modifier.padding(bottom = 4.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement   = Arrangement.spacedBy(6.dp)
                        ) {
                            hints.forEach { hint ->
                                Surface(
                                    onClick = { conceptQuery = hint },
                                    shape   = RoundedCornerShape(8.dp),
                                    color   = WDBgAlt
                                ) {
                                    Text(
                                        hint,
                                        fontSize = 12.sp,
                                        color    = WDInk,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Nota
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                placeholder   = { Text(stringResource(R.string.note_hint)) },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = ph),
                shape         = RoundedCornerShape(14.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = WDBgAlt,
                    focusedContainerColor   = WDBgAlt
                )
            )

            // Keypad
            WDKeypad(
                onKey    = { key -> amountStr = handleKeypadInput(amountStr, key) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = ph)
            )

            val amount  = amountStr.toDoubleOrNull() ?: 0.0
            val concept = conceptQuery.ifBlank { selectedCat.ifBlank { "Gasto" } }

            Button(
                onClick  = {
                    if (amount > 0) {
                        vm.addTransaction(
                            Transaction(
                                id       = UUID.randomUUID().toString(),
                                type     = TxType.OUT,
                                amount   = amount,
                                concept  = concept,
                                detail   = note.ifBlank { concept },
                                dateTime = LocalDateTime.now().toString()
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = ph),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = WDExpense)
            ) {
                Text(
                    "${stringResource(R.string.save_expense)} · $${"%.2f".format(amount)}",
                    color      = Color.White,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun handleKeypadInput(current: String, key: String): String {
    if (key == "⌫") return if (current.length <= 1) "0" else current.dropLast(1)
    if (key == "." && current.contains(".")) return current
    if (current == "0" && key != ".") return key
    return current + key
}
