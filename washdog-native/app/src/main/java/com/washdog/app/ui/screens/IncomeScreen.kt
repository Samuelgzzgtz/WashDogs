package com.washdog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.washdog.app.data.WDService
import com.washdog.app.data.WD_SERVICES
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import com.washdog.app.ui.components.WDKeypad
import com.washdog.app.ui.components.WDSearchableDropdown
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDBgAlt
import com.washdog.app.ui.theme.WDCard
import com.washdog.app.ui.theme.WDExpenseDk
import com.washdog.app.ui.theme.WDExpenseTnt
import com.washdog.app.ui.theme.WDHairline
import com.washdog.app.ui.theme.WDIncome
import com.washdog.app.ui.theme.WDIncomeTnt
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDateTime
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeScreen(vm: AppViewModel, onBack: () -> Unit) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val fs       = if (isTablet) 2f else 0f
    val maxW     = if (isTablet) 720.dp else Dp.Unspecified

    // Carrito acumulador
    val cart         = remember { mutableStateListOf<WDService>() }
    var manualAmount by remember { mutableStateOf<Double?>(null) }  // null = usar suma del carrito
    var amountStr    by remember { mutableStateOf("0") }
    var serviceQuery by remember { mutableStateOf("") }
    var note         by remember { mutableStateOf("") }

    // Total: manual si el usuario tocó el keypad, si no → suma del carrito
    val cartTotal = cart.sumOf { it.price }
    val display   = manualAmount ?: cartTotal
    val displayStr = if (display == 0.0) amountStr else "%.0f".format(display)

    fun addToCart(svc: WDService) {
        cart.add(svc)
        manualAmount = null          // vuelve al modo carrito
        serviceQuery = svc.name
        amountStr    = "0"
    }

    fun removeFromCart(idx: Int) {
        if (idx in cart.indices) {
            cart.removeAt(idx)
            manualAmount = null
            amountStr    = "0"
        }
    }

    val serviceNames  = WD_SERVICES.map { it.name }
    val serviceLabels = WD_SERVICES.map { it.label }

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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = ph)) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = WDInk) }
                Text(stringResource(R.string.income_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WDInk)
            }

            // Total grande
            Text(
                text       = "$$displayStr",
                fontSize   = (64 + fs).sp,
                fontWeight = FontWeight.Bold,
                color      = WDPrimary,
                modifier   = Modifier.align(Alignment.CenterHorizontally)
            )

            // Carrito — muestra los servicios agregados
            if (cart.isNotEmpty()) {
                Surface(
                    modifier       = Modifier.fillMaxWidth().padding(horizontal = ph),
                    shape          = RoundedCornerShape(14.dp),
                    color          = WDIncomeTnt,
                    tonalElevation = 0.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Servicios seleccionados", fontSize = 11.sp, color = WDInkMute, fontWeight = FontWeight.SemiBold)
                            TextButton(
                                onClick      = { cart.clear(); manualAmount = null; amountStr = "0" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) { Text("Limpiar", fontSize = 11.sp, color = WDExpenseDk) }
                        }
                        cart.forEachIndexed { idx, svc ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(svc.name, fontSize = 13.sp, color = WDInk, modifier = Modifier.weight(1f))
                                Text("$${svc.price.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WDPrimary)
                                IconButton(onClick = { removeFromCart(idx) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Close, null, tint = WDInkMute, modifier = Modifier.size(14.dp))
                                }
                            }
                            if (idx < cart.lastIndex) HorizontalDivider(color = WDHairline, thickness = 0.5.dp)
                        }
                        HorizontalDivider(color = WDIncome, thickness = 1.dp, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WDInk)
                            Text("$${"%.0f".format(cartTotal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WDPrimary)
                        }
                    }
                }
            }

            // Dropdown de servicio
            WDSearchableDropdown(
                value          = serviceQuery,
                onValueChange  = { serviceQuery = it },
                options        = serviceNames,
                displayOptions = serviceLabels,
                label          = "Servicio",
                placeholder    = "Buscar servicio…",
                modifier       = Modifier.fillMaxWidth().padding(horizontal = ph)
            )

            // Catálogo con botón "+"
            PriceCatalog(ph = ph, onAdd = ::addToCart)

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

            // Keypad (ajuste manual — desactiva el carrito como fuente de total)
            Column(Modifier.padding(horizontal = ph)) {
                Text("Ajuste manual", fontSize = 11.sp, color = WDInkMute, modifier = Modifier.padding(bottom = 4.dp))
                WDKeypad(
                    onKey = { key ->
                        val prev = if (manualAmount != null) "%.0f".format(manualAmount) else "0"
                        amountStr    = handleKeypadInput(prev, key)
                        manualAmount = amountStr.toDoubleOrNull()?.takeIf { it > 0 }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val finalAmount = display.takeIf { it > 0 } ?: (amountStr.toDoubleOrNull() ?: 0.0)
            val concept     = if (cart.size == 1) cart[0].name
                              else if (cart.size > 1) cart.joinToString(", ") { it.name }
                              else serviceQuery.ifBlank { "Ingreso" }

            Button(
                onClick  = {
                    if (finalAmount > 0) {
                        vm.addTransaction(
                            Transaction(
                                id       = UUID.randomUUID().toString(),
                                type     = TxType.IN,
                                amount   = finalAmount,
                                concept  = concept.take(60),
                                detail   = note.ifBlank { concept.take(60) },
                                dateTime = LocalDateTime.now().toString()
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = ph),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = WDIncome)
            ) {
                Text(
                    "${stringResource(R.string.save_income)} · $${"%.2f".format(finalAmount)}",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceCatalog(ph: Dp, onAdd: (WDService) -> Unit) {
    val groups = listOf(
        "Baño perros" to WD_SERVICES.filter { it.name.startsWith("Baño Perro") },
        "Baño gatos"  to WD_SERVICES.filter { it.name.startsWith("Baño Gato") },
        "Servicios"   to WD_SERVICES.filter { !it.name.startsWith("Baño") && !it.name.startsWith("Domicilio") },
        "Domicilio"   to WD_SERVICES.filter { it.name.startsWith("Domicilio") }
    )
    Surface(
        modifier       = Modifier.fillMaxWidth().padding(horizontal = ph),
        shape          = RoundedCornerShape(14.dp),
        color          = WDCard,
        tonalElevation = 1.dp
    ) {
        Column {
            groups.forEachIndexed { gi, (groupName, services) ->
                if (services.isEmpty()) return@forEachIndexed
                Text(
                    groupName,
                    fontSize   = 11.sp,
                    color      = WDInkMute,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(start = 14.dp, top = if (gi == 0) 12.dp else 8.dp, bottom = 2.dp)
                )
                services.forEach { svc ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(svc.name, fontSize = 13.sp, color = WDInk, modifier = Modifier.weight(1f).padding(start = 14.dp, top = 8.dp, bottom = 8.dp))
                        // Botón "+" — agrega al carrito
                        Surface(
                            onClick  = { onAdd(svc) },
                            shape    = RoundedCornerShape(8.dp),
                            color    = WDBgAlt,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, tint = WDPrimary, modifier = Modifier.size(12.dp))
                                Text("$${svc.price.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WDPrimary)
                            }
                        }
                    }
                    if (svc != services.last()) HorizontalDivider(modifier = Modifier.padding(start = 14.dp), color = WDHairline, thickness = 0.5.dp)
                }
                if (gi < groups.size - 1) HorizontalDivider(color = WDHairline, thickness = 1.dp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun handleKeypadInput(current: String, key: String): String {
    if (key == "⌫") return if (current.length <= 1) "0" else current.dropLast(1)
    if (key == "." && current.contains(".")) return current
    if (current == "0" && key != ".") return key
    return current + key
}
