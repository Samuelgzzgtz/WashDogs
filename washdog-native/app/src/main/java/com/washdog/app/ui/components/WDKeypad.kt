package com.washdog.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val keypadKeys = listOf("1","2","3","4","5","6","7","8","9",".","0","⌫")

@Composable
fun WDKeypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        modifier              = modifier.height(220.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled     = false
    ) {
        items(keypadKeys, key = { it }) { key ->
            ElevatedButton(
                onClick  = { onKey(key) },
                modifier = Modifier.height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text(key, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
