package com.sekharjal.shiftpay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sekharjal.shiftpay.ShiftPayViewModel
import com.sekharjal.shiftpay.data.AppSettings
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.data.ThemeChoice
import com.sekharjal.shiftpay.ui.screens.HomeScreen
import com.sekharjal.shiftpay.ui.screens.ProfileScreen
import com.sekharjal.shiftpay.ui.screens.ReportsScreen
import com.sekharjal.shiftpay.ui.screens.SettingsScreen
import com.sekharjal.shiftpay.ui.screens.ShiftEditorScreen
import com.sekharjal.shiftpay.ui.screens.ShiftsScreen
import com.sekharjal.shiftpay.ui.theme.ShiftPayTheme

/** The bottom bar shows icons only: each page already shows its own name at the top. */
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Shifts("shifts", "Shifts", Icons.Filled.DateRange),
    Reports("reports", "Reports", Icons.AutoMirrored.Filled.List),
    Profile("profile", "Profile", Icons.Filled.Person),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

private const val EDITOR_ROUTE = "shift_edit"

@Composable
fun ShiftPayApp(vm: ShiftPayViewModel = viewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    val settings = loaded
    val dark = when (settings?.theme ?: ThemeChoice.SYSTEM) {
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }
    ShiftPayTheme(darkTheme = dark) {
        if (settings == null) {
            // Saved settings load in a split second; show an empty background until then.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else {
            AppContent(vm, settings)
        }
    }
}

@Composable
private fun AppContent(vm: ShiftPayViewModel, settings: AppSettings) {
    val nav = rememberNavController()
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    val allowances by vm.allowances.collectAsStateWithLifecycle()
    val deductions by vm.deductions.collectAsStateWithLifecycle()
    val snapshot = remember(settings, shifts, allowances, deductions) {
        Snapshot(settings, shifts, allowances, deductions)
    }
    val clock = remember(vm) {
        ClockActions(
            onClockIn = { vm.clockIn() },
            onChangeStart = { vm.changeActiveStart(it) },
            onDiscard = { vm.discardActiveShift() },
            onClockOut = { vm.clockOut(it) },
        )
    }

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = Destination.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                nav.navigate(destination.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    snap = snapshot,
                    clock = clock,
                    onSetupPay = {
                        nav.navigate(Destination.Profile.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Destination.Shifts.route) {
                ShiftsScreen(
                    snap = snapshot,
                    clock = clock,
                    onAddManual = { nav.navigate("$EDITOR_ROUTE/-1") },
                    onEdit = { id -> nav.navigate("$EDITOR_ROUTE/$id") },
                    onDelete = { id -> vm.deleteShift(id) },
                )
            }
            composable(Destination.Reports.route) {
                ReportsScreen(snap = snapshot)
            }
            composable(Destination.Profile.route) {
                ProfileScreen(
                    snap = snapshot,
                    onSaveSettings = { vm.saveSettings(it) },
                    onAddAllowance = { name, amount, freq -> vm.addAllowance(name, amount, freq) },
                    onDeleteAllowance = { vm.deleteAllowance(it) },
                    onAddDeduction = { name, amount, freq -> vm.addDeduction(name, amount, freq) },
                    onDeleteDeduction = { vm.deleteDeduction(it) },
                    onAddHoliday = { date, name -> vm.addHoliday(date, name) },
                    onDeleteHoliday = { vm.deleteHoliday(it) },
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(snap = snapshot, onSaveSettings = { vm.saveSettings(it) })
            }
            composable(
                route = "$EDITOR_ROUTE/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                ShiftEditorScreen(
                    snap = snapshot,
                    shiftId = id,
                    onSave = { record ->
                        vm.saveShift(record)
                        nav.popBackStack()
                    },
                    onDelete = { shiftId ->
                        vm.deleteShift(shiftId)
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
