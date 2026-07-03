package com.washdog.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.washdog.app.ui.components.WDTabBar
import com.washdog.app.ui.screens.AddAppointmentScreen
import com.washdog.app.ui.screens.AddClientScreen
import com.washdog.app.ui.screens.AgendaScreen
import com.washdog.app.ui.screens.ClientsScreen
import com.washdog.app.ui.screens.EditClientScreen
import com.washdog.app.ui.screens.DashboardScreen
import com.washdog.app.ui.screens.ExpenseScreen
import com.washdog.app.ui.screens.IncomeScreen
import com.washdog.app.ui.screens.LoginScreen
import com.washdog.app.ui.screens.ReportsScreen
import com.washdog.app.ui.screens.SettingsScreen
import com.washdog.app.ui.theme.WashdogTheme
import com.washdog.app.viewmodel.AppViewModel

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { WashdogTheme { WashdogApp(vm) } }
    }
}

@Composable
private fun WashdogApp(vm: AppViewModel) {
    if (vm.sessionLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (!vm.isLoggedIn) {
        LoginScreen(vm = vm)
        return
    }

    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route ?: "dashboard"
    val tabRoutes     = setOf("dashboard", "agenda", "clients", "reports")

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                WDTabBar(currentRoute = currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo("dashboard") { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = "dashboard",
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    vm                = vm,
                    onAddIncome       = { navController.navigate("income") },
                    onAddExpense      = { navController.navigate("expense") },
                    onNavigateClients = {
                        navController.navigate("clients") {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
            composable("agenda") {
                AgendaScreen(vm = vm, onAddAppointment = { date ->
                    navController.navigate("add_appointment/$date")
                })
            }
            composable("add_appointment/{date}") { backStackEntry ->
                val date = backStackEntry.arguments?.getString("date") ?: ""
                AddAppointmentScreen(vm = vm, initialDate = date) { navController.popBackStack() }
            }
            composable("clients") {
                ClientsScreen(
                    vm                 = vm,
                    onNavigateSettings = { navController.navigate("settings") },
                    onAddClient        = { navController.navigate("add_client") },
                    onEditClient       = { clientId -> navController.navigate("edit_client/$clientId") }
                )
            }
            composable("add_client") {
                AddClientScreen(vm = vm) { navController.popBackStack() }
            }
            composable("edit_client/{clientId}") { backStackEntry ->
                val clientId = backStackEntry.arguments?.getString("clientId") ?: ""
                EditClientScreen(vm = vm, clientId = clientId) { navController.popBackStack() }
            }
            composable("reports")  { ReportsScreen(vm) }
            composable("income")   { IncomeScreen(vm)   { navController.popBackStack() } }
            composable("expense")  { ExpenseScreen(vm)  { navController.popBackStack() } }
            composable("settings") { SettingsScreen(vm) { navController.popBackStack() } }
        }
    }
}
