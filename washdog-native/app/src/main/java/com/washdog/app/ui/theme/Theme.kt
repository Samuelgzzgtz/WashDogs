package com.washdog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val WDColorScheme = lightColorScheme(
    primary          = WDPrimary,
    onPrimary        = WDCard,
    primaryContainer = WDPrimaryTnt,
    background       = WDBg,
    surface          = WDCard,
    onBackground     = WDInk,
    onSurface        = WDInk,
    secondary        = WDIncome,
    tertiary         = WDExpense,
    outline          = WDHairline
)

@Composable
fun WashdogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WDColorScheme,
        typography  = WDTypography,
        content     = content
    )
}
