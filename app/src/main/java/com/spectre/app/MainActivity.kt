package com.spectre.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spectre.app.core.data.datastore.SpectrePreferences
import com.spectre.app.core.data.datastore.SpectreSettings
import com.spectre.app.core.security.LockState
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.ui.theme.SpectreTheme
import com.spectre.app.navigation.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Main ViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefs: SpectrePreferences,
    private val session: VaultSession,
) : ViewModel() {

    val settings: StateFlow<SpectreSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpectreSettings())

    val lockState: StateFlow<LockState> = session.lockState

    val startDestination: StateFlow<String?> = combine(prefs.settings, session.lockState) { s, lock ->
        when {
            s.activeAccountId == null  -> Screen.Login.route
            lock == LockState.LOCKED   -> Screen.Unlock.createRoute(s.activeAccountId)
            lock == LockState.UNLOCKED -> Screen.Vault.route
            else                       -> Screen.Login.route
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            prefs.settings.collect { s ->
                if (s.activeAccountId != null) session.setAccountExists()
                else session.setNoAccount()
            }
        }
    }
}

// ── Activity ──────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel  = hiltViewModel()
            val settings           by vm.settings.collectAsState()
            val startDestination   by vm.startDestination.collectAsState()

            // Screenshot protection
            LaunchedEffect(settings.screenshotProtection) {
                if (settings.screenshotProtection) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            SpectreTheme(appTheme = settings.theme) {
                val start = startDestination
                if (start != null) {
                    SpectreApp(startDestination = start, settings = settings)
                }
            }
        }
    }
}

// ── Root Composable ───────────────────────────────────────────────────────────

@Composable
fun SpectreApp(startDestination: String, settings: SpectreSettings) {
    val navController = rememberNavController()
    val currentEntry  by navController.currentBackStackEntryAsState()
    val currentRoute  = currentEntry?.destination?.route

    val showBottomNav = currentRoute in bottomNavItems.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick  = {
                                if (!selected) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            },
                            icon = {
                                Icon(if (selected) item.selectedIcon else item.unselectedIcon, item.label)
                            },
                            label = if (settings.showBottomNavLabels) {
                                { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                            } else null,
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            SpectreNavGraph(navController = navController, startDestination = startDestination)
        }
    }
}
