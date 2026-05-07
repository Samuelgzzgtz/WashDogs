package com.washdog.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.washdog.app.R
import com.washdog.app.ui.theme.WDCard
import com.washdog.app.ui.theme.WDExpense
import com.washdog.app.ui.theme.WDIncome

@Composable
fun WDQuickActions(
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick  = onAddIncome,
            modifier = Modifier.weight(1f).height(48.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = WDIncome)
        ) { Text(stringResource(R.string.add_income), color = WDCard) }

        Button(
            onClick  = onAddExpense,
            modifier = Modifier.weight(1f).height(48.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = WDExpense)
        ) { Text(stringResource(R.string.add_expense), color = WDCard) }
    }
}
