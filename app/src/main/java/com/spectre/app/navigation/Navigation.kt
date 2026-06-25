package com.spectre.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.*
import androidx.navigation.compose.*
import com.spectre.app.feature.auth.*
import com.spectre.app.feature.vault.*
import com.spectre.app.core.navigation.Route
import kotlinx.serialization.Serializable

// Navigation system is now type-safe using Route objects defined in core.navigation.Route

data class BottomNavItem(
    val route: Route,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Route.Vault,      "Vault",      Icons.Filled.Lock,    Icons.Outlined.Lock),
    BottomNavItem(Route.Generator,  "Generator",  Icons.Filled.Casino,  Icons.Outlined.Casino),
    BottomNavItem(Route.Watchtower, "Watchtower", Icons.Filled.Shield,  Icons.Outlined.Shield),
    BottomNavItem(Route.Send,       "Send",       Icons.AutoMirrored.Filled.Send, Icons.AutoMirrored.Outlined.Send),
)

@Composable
fun SpectreNavGraph(
    navController: NavHostController,
    startDestination: Route,
    activeAccount: com.spectre.app.core.data.models.Account? = null,
    initialPage: String? = null
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable<Route.CreateLocalVault> {
            CreateLocalVaultScreen(
                onVaultCreated = {
                    navController.navigate(Route.Vault) {
                        popUpTo(Route.CreateLocalVault) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Auth> {
            AddVaultScreen(
                onBitwardenUs = {
                    navController.navigate(
                        Route.Login(
                            serverLabel = "Bitwarden US",
                            serverUrl = "https://api.bitwarden.com",
                            identityUrl = "https://identity.bitwarden.com"
                        )
                    )
                },
                onBitwardenEu = {
                    navController.navigate(
                        Route.Login(
                            serverLabel = "Bitwarden EU",
                            serverUrl = "https://api.bitwarden.eu",
                            identityUrl = "https://identity.bitwarden.eu"
                        )
                    )
                },
                onSelfHosted = {
                    navController.navigate(
                        Route.Login(
                            serverLabel = "Self-hosted",
                            serverUrl = "",
                            identityUrl = "",
                            useCustomServer = true
                        )
                    )
                },
                onKeePass = { navController.navigate(Route.KeePassLogin) },
                onLocalVault = { navController.navigate(Route.CreateLocalVault) },
            )
        }

        composable<Route.Login> { back ->
            val loginRoute: Route.Login = back.toRoute()
            LoginScreen(
                serverLabel = loginRoute.serverLabel,
                serverUrl   = loginRoute.serverUrl,
                identityUrl = loginRoute.identityUrl,
                useCustomServerDefault = loginRoute.useCustomServer,
                onBack      = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.navigate(Route.Vault) {
                        popUpTo(Route.Auth) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.KeePassLogin> {
            KeePassLoginScreen(
                onBack    = { navController.popBackStack() },
                onUnlocked = { _ ->
                    navController.navigate(Route.Vault) {
                        popUpTo(Route.Auth) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Unlock> { back ->
            val unlock: Route.Unlock = back.toRoute()
            UnlockScreen(
                accountId = unlock.accountId, 
                onUnlocked = {
                    navController.navigate(Route.Vault) {
                        popUpTo<Route.Unlock> { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Vault> {
            com.spectre.app.feature.main.MainScreen(
                navController = navController,
                activeAccount = activeAccount,
                initialPageName = initialPage
            )
        }

        composable<Route.Settings> {
            com.spectre.app.feature.settings.SettingsScreen(
                onAddBitwardenAccount = { navController.navigate(Route.Auth) }
            )
        }

        composable<Route.About> {
            com.spectre.app.feature.about.AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.VaultDetail> { back ->
            val detail: Route.VaultDetail = back.toRoute()
            VaultDetailScreen(
                cipherId = detail.cipherId,
                onEdit   = { navController.navigate(Route.VaultEdit(detail.cipherId)) },
                onBack   = { navController.popBackStack() },
            )
        }

        composable<Route.VaultEdit> { back ->
            val edit: Route.VaultEdit = back.toRoute()
            VaultEditScreen(
                cipherId = edit.cipherId,
                type     = edit.type,
                onSaved  = { navController.popBackStack() },
                onBack   = { navController.popBackStack() },
            )
        }
    }
}
