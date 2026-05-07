package com.washdog.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.washdog.app.R
import com.washdog.app.ui.theme.WDCard
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.ui.theme.WDPrimaryTnt

data class WDTab(val route: String, val icon: ImageVector, val labelRes: Int)

val wdTabs = listOf(
    WDTab("dashboard", Icons.Filled.Home,      R.string.tab_dashboard),
    WDTab("agenda",    Icons.Filled.DateRange, R.string.tab_agenda),
    WDTab("clients",   Icons.Filled.People,    R.string.tab_clients),
    WDTab("reports",   Icons.Filled.BarChart,  R.string.tab_reports),
)

@Composable
fun WDTabBar(currentRoute: String, onTabSelected: (String) -> Unit) {
    val isTablet  = LocalConfiguration.current.screenWidthDp >= 600
    val barHeight = if (isTablet) 64.dp else 56.dp
    NavigationBar(
        modifier       = Modifier.height(barHeight),
        containerColor = WDCard,
        tonalElevation = 8.dp
    ) {
        wdTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick  = { onTabSelected(tab.route) },
                icon     = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label    = { Text(stringResource(tab.labelRes)) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = WDPrimary,
                    selectedTextColor   = WDPrimary,
                    indicatorColor      = WDPrimaryTnt,
                    unselectedIconColor = WDInkMute,
                    unselectedTextColor = WDInkMute
                )
            )
        }
    }
}
