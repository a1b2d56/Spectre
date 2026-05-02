package com.spectre.app.feature.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.datastore.*
import com.spectre.app.core.data.repository.AuthRepository
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.ui.components.*
import com.spectre.app.core.ui.theme.SpectreTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SpectrePreferences,
    private val authRepository: AuthRepository,
    private val session: VaultSession,
) : ViewModel() {

    val settings: StateFlow<SpectreSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpectreSettings())

    val accounts: StateFlow<List<com.spectre.app.core.data.models.Account>> = authRepository
        .observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTheme(t: SpectreTheme)              = viewModelScope.launch { prefs.setTheme(t) }
    fun setLockTimeout(t: LockTimeout)         = viewModelScope.launch { prefs.setLockTimeout(t) }
    fun setLockOnBackground(v: Boolean)        = viewModelScope.launch { prefs.setLockOnBackground(v) }
    fun setBiometricUnlock(v: Boolean)         = viewModelScope.launch { prefs.setBiometricUnlock(v) }
    fun setClipboardClear(s: Int)              = viewModelScope.launch { prefs.setClipboardClearSeconds(s) }
    fun setScreenshotProtection(v: Boolean)    = viewModelScope.launch { prefs.setScreenshotProtection(v) }
    fun setShowFavicons(v: Boolean)            = viewModelScope.launch { prefs.setShowFavicons(v) }
    fun setCompactList(v: Boolean)             = viewModelScope.launch { prefs.setCompactVaultList(v) }
    fun setSortOrder(o: VaultSortOrder)        = viewModelScope.launch { prefs.setSortOrder(o) }
    fun setSyncInterval(i: SyncInterval)       = viewModelScope.launch { prefs.setSyncInterval(i) }
    fun setShowBottomNavLabels(v: Boolean)     = viewModelScope.launch { prefs.setShowBottomNavLabels(v) }
    fun setDuressEnabled(v: Boolean)           = viewModelScope.launch { prefs.setDuressEnabled(v) }
    fun lockVault()                            = session.lock()
    fun signOut(accountId: String)             = viewModelScope.launch { authRepository.signOut(accountId) }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsState()
    val accounts by vm.accounts.collectAsState()

    var showThemePicker     by remember { mutableStateOf(false) }
    var showTimeoutPicker   by remember { mutableStateOf(false) }
    var showClipboardPicker by remember { mutableStateOf(false) }
    var showSyncPicker      by remember { mutableStateOf(false) }
    var showSortPicker      by remember { mutableStateOf(false) }
    var showSignOutConfirm  by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpectreTopBar(title = "Settings")

            // ── Accounts ──────────────────────────────────────────────────────
            SettingsSection("Accounts") {
                accounts.forEach { account ->
                    SettingsItem(
                        title    = account.email,
                        subtitle = if (account.isActive) "Active · ${account.serverUrl}" else account.serverUrl,
                        icon     = Icons.Filled.AccountCircle,
                        trailing = {
                            if (account.isActive) Badge { Text("Active") }
                            else IconButton(onClick = { /* switch */ }) { Icon(Icons.Filled.SwapHoriz, "Switch") }
                        },
                    )
                }
                SettingsItem(title = "Add account", icon = Icons.Filled.PersonAdd, onClick = { /* navigate login */ })
            }

            // ── Security ──────────────────────────────────────────────────────
            SettingsSection("Security") {
                SettingsItem(
                    title    = "Lock vault now",
                    icon     = Icons.Filled.Lock,
                    onClick  = vm::lockVault,
                )
                SettingsItem(
                    title    = "Vault lock timeout",
                    subtitle = settings.lockTimeout.label,
                    icon     = Icons.Filled.Timer,
                    onClick  = { showTimeoutPicker = true },
                )
                SettingsSwitchItem(
                    title   = "Lock on background",
                    subtitle = "Lock when app is minimised",
                    icon    = Icons.Filled.MobileFriendly,
                    checked = settings.lockOnBackground,
                    onChange = vm::setLockOnBackground,
                )
                SettingsSwitchItem(
                    title   = "Biometric unlock",
                    icon    = Icons.Filled.Fingerprint,
                    checked = settings.biometricUnlock,
                    onChange = vm::setBiometricUnlock,
                )
                SettingsSwitchItem(
                    title   = "Screenshot protection",
                    subtitle = "Prevent screen capture",
                    icon    = Icons.Filled.Screenshot,
                    checked = settings.screenshotProtection,
                    onChange = vm::setScreenshotProtection,
                )
                SettingsItem(
                    title    = "Clipboard clear",
                    subtitle = "${settings.clipboardClearSeconds}s after copy",
                    icon     = Icons.Filled.ContentPaste,
                    onClick  = { showClipboardPicker = true },
                )
                SettingsSwitchItem(
                    title   = "Duress / decoy vault",
                    subtitle = "Show empty vault on alternate PIN",
                    icon    = Icons.Filled.Security,
                    checked = settings.duressEnabled,
                    onChange = vm::setDuressEnabled,
                )
            }

            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection("Appearance") {
                SettingsItem(
                    title    = "Theme",
                    subtitle = settings.theme.name.lowercase().replaceFirstChar { it.uppercase() },
                    icon     = Icons.Filled.Palette,
                    onClick  = { showThemePicker = true },
                )
                SettingsSwitchItem(
                    title   = "Compact vault list",
                    icon    = Icons.Filled.ViewList,
                    checked = settings.compactVaultList,
                    onChange = vm::setCompactList,
                )
                SettingsSwitchItem(
                    title   = "Show bottom nav labels",
                    icon    = Icons.Filled.Label,
                    checked = settings.showBottomNavLabels,
                    onChange = vm::setShowBottomNavLabels,
                )
                SettingsSwitchItem(
                    title   = "Show favicons",
                    subtitle = "Fetch website icons for login items",
                    icon    = Icons.Filled.Bookmark,
                    checked = settings.showFavicons,
                    onChange = vm::setShowFavicons,
                )
                SettingsItem(
                    title    = "Default sort order",
                    subtitle = settings.sortOrder.label,
                    icon     = Icons.Filled.Sort,
                    onClick  = { showSortPicker = true },
                )
            }

            // ── Sync ──────────────────────────────────────────────────────────
            SettingsSection("Sync") {
                SettingsItem(
                    title    = "Background sync interval",
                    subtitle = settings.backgroundSyncInterval.label,
                    icon     = Icons.Filled.Sync,
                    onClick  = { showSyncPicker = true },
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection("About") {
                SettingsItem(title = "Version", subtitle = "0.1.0-alpha", icon = Icons.Filled.Info)
                SettingsItem(title = "Licences", icon = Icons.Filled.Article, onClick = { })
                SettingsItem(title = "GitHub", subtitle = "View source code", icon = Icons.Filled.Code, onClick = { })
            }

            // ── Danger zone ───────────────────────────────────────────────────
            SettingsSection("Account") {
                accounts.firstOrNull { it.isActive }?.let { account ->
                    SettingsItem(
                        title   = "Sign out",
                        icon    = Icons.Filled.Logout,
                        tint    = MaterialTheme.colorScheme.error,
                        onClick = { showSignOutConfirm = account.id },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showThemePicker) {
        PickerDialog(
            title   = "Choose Theme",
            options = SpectreTheme.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selectedIndex = SpectreTheme.entries.indexOf(settings.theme),
            onSelect = { vm.setTheme(SpectreTheme.entries[it]); showThemePicker = false },
            onDismiss = { showThemePicker = false },
        )
    }

    if (showTimeoutPicker) {
        PickerDialog(
            title   = "Lock Timeout",
            options = LockTimeout.entries.map { it.label },
            selectedIndex = LockTimeout.entries.indexOf(settings.lockTimeout),
            onSelect = { vm.setLockTimeout(LockTimeout.entries[it]); showTimeoutPicker = false },
            onDismiss = { showTimeoutPicker = false },
        )
    }

    if (showSyncPicker) {
        PickerDialog(
            title   = "Sync Interval",
            options = SyncInterval.entries.map { it.label },
            selectedIndex = SyncInterval.entries.indexOf(settings.backgroundSyncInterval),
            onSelect = { vm.setSyncInterval(SyncInterval.entries[it]); showSyncPicker = false },
            onDismiss = { showSyncPicker = false },
        )
    }

    showSignOutConfirm?.let { accountId ->
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = null },
            icon   = { Icon(Icons.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title  = { Text("Sign out?") },
            text   = { Text("Your local vault data will be removed. You can log in again to restore it.") },
            confirmButton = {
                Button(
                    onClick = { vm.signOut(accountId); showSignOutConfirm = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { showSignOutConfirm = null }) { Text("Cancel") } },
        )
    }
}

// ── Reusable setting composables ──────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text      = title.uppercase(),
            style     = MaterialTheme.typography.labelMedium,
            color     = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier  = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        SpectreCard(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (tint != MaterialTheme.colorScheme.onSurfaceVariant) tint else MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke() ?: onClick?.let {
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = { onSelect(index) })
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(16.dp),
    )
}
