package com.washdog.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.TxType
import com.washdog.app.ui.components.WDBalanceCard
import com.washdog.app.ui.components.WDIconBox
import com.washdog.app.ui.components.WDQuickActions
import com.washdog.app.ui.components.WDReminderStrip
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDExpenseDk
import com.washdog.app.ui.theme.WDExpenseTnt
import com.washdog.app.ui.theme.WDHairline
import com.washdog.app.ui.theme.WDIncomeDk
import com.washdog.app.ui.theme.WDIncomeTnt
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    vm:                AppViewModel,
    onAddIncome:       () -> Unit,
    onAddExpense:      () -> Unit,
    onNavigateClients: () -> Unit = {}
) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 720.dp else Dp.Unspecified

    Box(Modifier.fillMaxSize().background(WDBg)) {
        LazyColumn(
            modifier            = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter),
            contentPadding      = PaddingValues(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header")  { DashboardHeader(isTablet) }
            item(key = "balance") {
                WDBalanceCard(net = vm.netBalance, income = vm.totalIncome, expense = vm.totalExpense)
            }
            item(key = "actions") {
                WDQuickActions(onAddIncome = onAddIncome, onAddExpense = onAddExpense)
            }
            if (vm.clientsToRemind.isNotEmpty()) {
                item(key = "reminder") {
                    WDReminderStrip(clients = vm.clientsToRemind, onClick = onNavigateClients)
                }
            }
            item(key = "tx_header") {
                Text(
                    text       = stringResource(R.string.recent_tx),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = WDInk
                )
            }
            if (vm.transactions.isEmpty()) {
                item(key = "empty_tx") {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sin transacciones aún", fontSize = 14.sp, color = WDInkMute)
                    }
                }
            }
            items(vm.transactions, key = { it.id }) { tx ->
                TxRow(tx = tx, onDelete = { vm.deleteTransaction(tx.id) })
            }
            item(key = "bottom_space") { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DashboardHeader(isTablet: Boolean) {
    val today = LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es")))
        .replaceFirstChar { it.uppercase() }
    Column {
        Text(
            text       = stringResource(R.string.greeting),
            fontSize   = if (isTablet) 26.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            color      = WDInk
        )
        Text(text = today, fontSize = 13.sp, color = WDInkMute)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TxRow(tx: Transaction, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart }
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    val bgColor by animateColorAsState(
        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                          WDExpenseTnt else WDBg,
        label       = "swipe_bg"
    )

    SwipeToDismissBox(
        state                       = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent           = {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = WDExpenseDk)
            }
        }
    ) {
        TxRowContent(tx)
    }
}

@Composable
private fun TxRowContent(tx: Transaction) {
    val isIncome = tx.type == TxType.IN
    val icon     = if (isIncome) Icons.Filled.ContentCut else Icons.Filled.Receipt
    val iconBg   = if (isIncome) WDIncomeTnt else WDExpenseTnt
    val iconTint = if (isIncome) WDIncomeDk  else WDExpenseDk
    val amtColor = if (isIncome) WDPrimary   else WDExpenseDk
    val amtSign  = if (isIncome) "+" else "-"

    Column(Modifier.background(WDBg)) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WDIconBox(icon = icon, tint = iconTint, background = iconBg)
            Column(Modifier.weight(1f)) {
                Text(tx.concept, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WDInk)
                Text(tx.detail,  fontSize = 12.sp, color = WDInkMute)
            }
            Text(
                text       = "$amtSign$${"%.2f".format(tx.amount)}",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = amtColor
            )
        }
        HorizontalDivider(color = WDHairline, thickness = 1.dp)
    }
}
