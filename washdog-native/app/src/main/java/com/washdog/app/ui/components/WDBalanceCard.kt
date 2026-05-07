package com.washdog.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.ui.theme.WDExpense
import com.washdog.app.ui.theme.WDIncome
import com.washdog.app.ui.theme.WDPrimary

@Composable
fun WDBalanceCard(
    net: Double,
    income: Double,
    expense: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(28.dp),
        colors    = CardDefaults.cardColors(containerColor = WDPrimary),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Icon(
                imageVector        = Icons.Filled.Pets,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.08f),
                modifier           = Modifier
                    .size(140.dp)
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
            Column(Modifier.padding(24.dp)) {
                Text(
                    text     = stringResource(R.string.balance_title),
                    color    = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text       = "$${"%,.2f".format(net)}",
                    color      = Color.White,
                    fontSize   = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
                    BalanceSubItem(label = stringResource(R.string.income_label),  amount = income,  color = WDIncome)
                    BalanceSubItem(label = stringResource(R.string.expense_label), amount = expense, color = WDExpense)
                }
            }
        }
    }
}

@Composable
private fun BalanceSubItem(label: String, amount: Double, color: Color) {
    Column {
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(
            text       = "$${"%,.2f".format(amount)}",
            color      = color,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
