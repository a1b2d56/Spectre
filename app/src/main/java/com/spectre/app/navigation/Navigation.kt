package com.spectre.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.*
import androidx.navigation.compose.*
import com.spectre.app.feature.auth.LoginScreen
import com.spectre.app.feature.auth.UnlockScreen
import com.spectre.app.feature.vault.VaultScreen
import com.spectre.app.feature.vault.VaultDetailScreen
import com.spectre.app.feature.vault.VaultEditScreen
import com.spectre.app.feature.generator.GeneratorScreen
import com.spectre.app.feature.watchtower.WatchtowerScreen
import com.spectre.app.feature.settings.SettingsScreen
import com.spectre.app.feature.send.SendScreen

sealed class Screen(val route: String) {
    // Auth
    object Login   : Screen("login")
    object Unlock  : Screen("unlock/{accountId}") {
        fun createRoute(accountId: String) = "unlock/$accountId"
    }

    // Main tabs
    object Vault      : Screen("vault")
    object Generator  : Screen("generator")
    object Watchtower : Screen("watchtower")
    object Send       : Screen("send")
    object Settings   : Screen("settings")

    // Vault detail/edit
    object VaultDetail : Screen("vault/detail/{cipherId}") {
        fun createRoute(cipherId: String) = "vault/detail/$cipherId"
    }
    object VaultEdit : Screen("vault/edit/{cipherId}?type={type}") {
        fun createRoute(cipherId: String? = null, type: Int = 1) =
            if (cipherId != null) "vault/edit/$cipherId?type=$type"
            else "vault/edit/new?type=$type"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Vault,      "Vault",      Icons.Filled.Lock,     Icons.Outlined.Lock),
    BottomNavItem(Screen.Generator,  "Generator",  Icons.Filled.Casino,   Icons.Outlined.Casino),
    BottomNavItem(Screen.Watchtower, "Watchtower", Icons.Filled.Shield,   Icons.Outlined.Shield),
    BottomNavItem(Screen.Send,       "Send",       Icons.Filled.Send,     Icons.Outlined.Send),
    BottomNavItem(Screen.Settings,   "Settings",   Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun SpectreNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
    ) {
        // ── Auth ──────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Vault.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Unlock.route,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            UnlockScreen(
                accountId = accountId,
                onUnlocked = {
                    navController.navigate(Screen.Vault.route) {
                        popUpTo(Screen.Unlock.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Main tabs ─────────────────────────────────────────────────────────
        composable(Screen.Vault.route) {
            VaultScreen(
                onItemClick = { id -> navController.navigate(Screen.VaultDetail.createRoute(id)) },
                onAddClick  = { type -> navController.navigate(Screen.VaultEdit.createRoute(type = type)) },
            )
        }

        composable(Screen.Generator.route)  { GeneratorScreen() }
        composable(Screen.Watchtower.route) {
            WatchtowerScreen(
                onItemClick = { id -> navController.navigate(Screen.VaultDetail.createRoute(id)) }
            )
        }
        composable(Screen.Send.route)    { SendScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }

        // ── Vault detail ──────────────────────────────────────────────────────
        composable(
            route = Screen.VaultDetail.route,
            arguments = listOf(navArgument("cipherId") { type = NavType.StringType })
        ) { backStackEntry ->
            val cipherId = backStackEntry.arguments?.getString("cipherId") ?: return@composable
            VaultDetailScreen(
                cipherId  = cipherId,
                onEdit    = { navController.navigate(Screen.VaultEdit.createRoute(cipherId)) },
                onBack    = { navController.popBackStack() },
            )
        }

        // ── Vault edit ────────────────────────────────────────────────────────
        composable(
            route = Screen.VaultEdit.route,
            arguments = listOf(
                navArgument("cipherId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("type") { type = NavType.IntType; defaultValue = 1 },
            )
        ) { backStackEntry ->
            val cipherId = backStackEntry.arguments?.getString("cipherId")
            val type     = backStackEntry.arguments?.getInt("type") ?: 1
            VaultEditScreen(
                cipherId = cipherId,
                type     = type,
                onSaved  = { navController.popBackStack() },
                onBack   = { navController.popBackStack() },
            )
        }
    }
}
