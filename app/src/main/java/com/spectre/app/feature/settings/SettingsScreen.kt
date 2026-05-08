package com.spectre.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.datastore.*
import com.spectre.app.core.data.models.UriMatchType
import com.spectre.app.core.data.repository.AuthRepository
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.ui.components.*
import com.spectre.app.core.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

/**
 * ViewModel responsible for managing app-wide settings and user accounts.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SpectrePreferences,
    private val authRepository: AuthRepository,
    private val session: VaultSession,
    private val vaultRepository: com.spectre.app.core.data.repository.VaultRepository,
    val biometricUnlock: com.spectre.app.core.security.BiometricUnlock,
) : ViewModel() {

    val settings: StateFlow<SpectreSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpectreSettings())

    val accounts: StateFlow<List<com.spectre.app.core.data.models.Account>> = authRepository
        .observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isAutofillEnabled = MutableStateFlow(false)
    val isAutofillEnabled: StateFlow<Boolean> = _isAutofillEnabled.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    fun setTheme(t: SpectreTheme)              = viewModelScope.launch { prefs.setTheme(t) }
    fun setLockTimeout(t: LockTimeout)         = viewModelScope.launch { prefs.setLockTimeout(t) }
    fun setLockOnBackground(v: Boolean)        = viewModelScope.launch { prefs.setLockOnBackground(v) }
    
    fun setBiometricUnlock(v: Boolean, activity: androidx.fragment.app.FragmentActivity? = null, masterPassword: String? = null) {
        viewModelScope.launch {
            if (v) {
                if (masterPassword != null && activity != null) {
                    biometricUnlock.promptToEncrypt(activity, masterPassword) { result ->
                        if (result is com.spectre.app.core.security.BiometricResult.Success) {
                            viewModelScope.launch { prefs.setBiometricUnlock(true) }
                        }
                    }
                }
            } else {
                biometricUnlock.clearSecret()
                prefs.setBiometricUnlock(false)
            }
        }
    }

    fun setClipboardClear(s: Int)              = viewModelScope.launch { prefs.setClipboardClearSeconds(s) }
    fun setScreenshotProtection(v: Boolean)    = viewModelScope.launch { prefs.setScreenshotProtection(v) }
    fun setShowFavicons(v: Boolean)            = viewModelScope.launch { prefs.setShowFavicons(v) }
    fun setCompactList(v: Boolean)             = viewModelScope.launch { prefs.setCompactVaultList(v) }
    fun setSortOrder(o: VaultSortOrder)        = viewModelScope.launch { prefs.setSortOrder(o) }
    fun setSyncInterval(i: SyncInterval)       = viewModelScope.launch { prefs.setSyncInterval(i) }
    fun setShowBottomNavLabels(v: Boolean)     = viewModelScope.launch { prefs.setShowBottomNavLabels(v) }
    fun setDuressEnabled(v: Boolean)           = viewModelScope.launch { prefs.setDuressEnabled(v) }

    fun setAfCredentialProvider(v: Boolean)    = viewModelScope.launch { prefs.setAfCredentialProvider(v) }
    fun setAfInline(v: Boolean)                = viewModelScope.launch { prefs.setAfInline(v) }
    fun setAfManual(v: Boolean)                = viewModelScope.launch { prefs.setAfManual(v) }
    fun setAfRespectDisabled(v: Boolean)       = viewModelScope.launch { prefs.setAfRespectDisabled(v) }
    fun setAfAutoCopyOtp(v: Boolean)           = viewModelScope.launch { prefs.setAfAutoCopyOtp(v) }
    fun setAfOtpNotifDur(d: Int)               = viewModelScope.launch { prefs.setAfOtpNotifDur(d) }
    fun setAfAutoSave(v: Boolean)              = viewModelScope.launch { prefs.setAfAutoSave(v) }
    fun setAfAskSave(v: Boolean)               = viewModelScope.launch { prefs.setAfAskSave(v) }
    fun setAfMatchDetection(m: UriMatchType)   = viewModelScope.launch { prefs.setAfMatchDetection(m) }

    fun setWtHibp(v: Boolean)                  = viewModelScope.launch { prefs.setWtHibp(v) }
    fun setWtReused(v: Boolean)                = viewModelScope.launch { prefs.setWtReused(v) }
    fun setWtWeak(v: Boolean)                  = viewModelScope.launch { prefs.setWtWeak(v) }
    fun setWt2fa(v: Boolean)                  = viewModelScope.launch { prefs.setWt2fa(v) }
    fun setCustomServerUrl(url: String)        = viewModelScope.launch { prefs.setCustomServerUrl(url) }

    fun lockVault()                            = session.lock()
    fun signOut(accountId: String)             = viewModelScope.launch { authRepository.signOut(accountId) }
    fun switchAccount(accountId: String)       = viewModelScope.launch { authRepository.switchAccount(accountId) }

    fun setPanicPin(pin: String)               = viewModelScope.launch { prefs.setPanicPin(pin); _snackbar.emit("Panic PIN set") }
    fun clearPanicPin()                        = viewModelScope.launch { prefs.setPanicPin(null); _snackbar.emit("Panic PIN removed") }
    fun purgeTrash()                           = viewModelScope.launch { vaultRepository.purgeTrash() }

    fun exportVault(context: android.content.Context) {
        viewModelScope.launch {
            val accountId = session.activeAccountId ?: return@launch
            _snackbar.emit("Preparing export...")
            
            try {
                val ciphers = vaultRepository.getAllDecryptedCiphers(accountId)
                val json = kotlinx.serialization.json.Json { 
                    prettyPrint = true 
                    encodeDefaults = true
                }
                val exportData = json.encodeToString(ciphers)
                
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_TEXT, exportData)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Spectre Vault Export")
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Save or Share Vault Export"))
                
                _snackbar.emit("Vault exported successfully")
            } catch (e: Exception) {
                _snackbar.emit("Export failed: ${e.message}")
            }
        }
    }

    fun checkAutofillStatus(context: android.content.Context) {
        val activeService = android.provider.Settings.Secure.getString(context.contentResolver, "autofill_service")
        _isAutofillEnabled.value = activeService?.contains(context.packageName) == true
    }

    fun openAutofillSettings(context: android.content.Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            viewModelScope.launch { _snackbar.emit("Open Settings > Passwords & autofill to enable Spectre") }
        }
    }
}


/**
 * Settings screens categorized by functional area.
 */
enum class SettingsSubScreen { HUB, AUTOFILL, SECURITY, APPEARANCE, WATCHTOWER, OTHER }

@Composable
fun SettingsScreen(
    onAddBitwardenAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var subScreen by remember { mutableStateOf(SettingsSubScreen.HUB) }

    val context = LocalContext.current
    val snackbarFlow = vm.snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        snackbarFlow.collect { snackbarHostState.showSnackbar(it) }
    }

    BackHandler(enabled = subScreen != SettingsSubScreen.HUB) {
        subScreen = SettingsSubScreen.HUB
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = subScreen,
            transitionSpec = {
                if (targetState == SettingsSubScreen.HUB) {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                } else {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                }
            },
            label = "settings_nav"
        ) { screen ->
            when (screen) {
                SettingsSubScreen.HUB -> SettingsHub(
                    settings = settings,
                    accounts = accounts,
                    onNavigate = { subScreen = it },
                    onAddAccount = onAddBitwardenAccount
                )
                SettingsSubScreen.AUTOFILL -> AutofillSettingsScreen(
                    settings = settings,
                    onBack = { subScreen = SettingsSubScreen.HUB },
                    vm = vm
                )
                SettingsSubScreen.SECURITY -> SecuritySettingsScreen(
                    settings = settings,
                    onBack = { subScreen = SettingsSubScreen.HUB },
                    vm = vm
                )
                SettingsSubScreen.APPEARANCE -> AppearanceSettingsScreen(
                    settings = settings,
                    onBack = { subScreen = SettingsSubScreen.HUB },
                    vm = vm
                )
                SettingsSubScreen.WATCHTOWER -> WatchtowerSettingsScreen(
                    settings = settings,
                    onBack = { subScreen = SettingsSubScreen.HUB },
                    vm = vm
                )
                SettingsSubScreen.OTHER -> OtherSettingsScreen(
                    settings = settings,
                    accounts = accounts,
                    onBack = { subScreen = SettingsSubScreen.HUB },
                    vm = vm
                )
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        )
    }
}

@Composable
private fun SettingsHub(
    settings: SpectreSettings,
    accounts: List<com.spectre.app.core.data.models.Account>,
    onNavigate: (SettingsSubScreen) -> Unit,
    onAddAccount: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            SpectreTopBar(
                title = "Settings",
                actions = {
                    IconButton(onClick = { /* TODO: Search */ }) {
                        Icon(Icons.Default.Search, "Search settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Account Status Section
            val activeAccount = accounts.find { it.isActive }
            if (activeAccount != null) {
                SettingsCategoryHeader("VAULT STATUS")
                SpectreCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        Box(
                            Modifier.size(44.dp).background(
                                if (activeAccount.premium || activeAccount.isLocal) MaterialTheme.colorScheme.primary.copy(0.12f)
                                else MaterialTheme.colorScheme.secondary.copy(0.1f),
                                RoundedCornerShape(12.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (activeAccount.premium || activeAccount.isLocal) Icons.Default.Star 
                                else Icons.Default.Person, 
                                null, 
                                tint = if (activeAccount.premium || activeAccount.isLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            val statusTitle = when {
                                activeAccount.isLocal -> "Local Vault"
                                activeAccount.premium -> "Premium Account"
                                else -> "Standard Account"
                            }
                            Text(statusTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(activeAccount.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!activeAccount.isLocal && !activeAccount.premium) {
                            TextButton(onClick = { /* Open BW Premium page */ }) {
                                Text("Upgrade")
                            }
                        }
                    }
                }
            }

            // Options Section
            SettingsCategoryHeader("OPTIONS")
            SpectreCard {
                Column {
                    HubItem(
                        title = "Autofill",
                        subtitle = "Android Autofill & Credential Manager",
                        icon = Icons.Default.AutoAwesome,
                        onClick = { onNavigate(SettingsSubScreen.AUTOFILL) }
                    )
                    HubDivider()
                    HubItem(
                        title = "Security",
                        subtitle = "Biometrics, change password, lock…",
                        icon = Icons.Default.Lock,
                        onClick = { onNavigate(SettingsSubScreen.SECURITY) }
                    )
                    HubDivider()
                    HubItem(
                        title = "Watchtower",
                        subtitle = "Compromised services, vulnerabilities…",
                        icon = Icons.Default.GppGood,
                        onClick = { onNavigate(SettingsSubScreen.WATCHTOWER) }
                    )
                    HubDivider()
                    HubItem(
                        title = "Appearance",
                        subtitle = "Language, theme and animations…",
                        icon = Icons.Default.Palette,
                        onClick = { onNavigate(SettingsSubScreen.APPEARANCE) }
                    )
                    HubDivider()
                    HubItem(
                        title = "Other",
                        subtitle = "Community websites, app info…",
                        icon = Icons.Default.Info,
                        onClick = { onNavigate(SettingsSubScreen.OTHER) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutofillSettingsScreen(
    settings: SpectreSettings,
    onBack: () -> Unit,
    vm: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAutofillActive by vm.isAutofillEnabled.collectAsStateWithLifecycle()
    var showMatchPicker by remember { mutableStateOf(false) }
    var showOtpDurPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.checkAutofillStatus(context)
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            SpectreTopBar(
                title = "Autofill",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Status Card (KeyGuard-style)
            SpectreCard(
                containerColor = if (isAutofillActive) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) 
                                else MaterialTheme.colorScheme.errorContainer.copy(0.1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    Icon(
                        if (isAutofillActive) Icons.Default.CheckCircle else Icons.Default.Error,
                        null,
                        tint = if (isAutofillActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isAutofillActive) "Service Active" else "Service Inactive",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isAutofillActive) "Spectre is protecting your apps" else "Enable Spectre in Android Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isAutofillActive) {
                        TextButton(onClick = { vm.openAutofillSettings(context) }) {
                            Text("Setup")
                        }
                    }
                }
            }

            SpectreCard {
                Column {
                    SettingsSwitchItem(
                        title = "Credential provider",
                        subtitle = "Passkeys, passwords and data services",
                        icon = Icons.Default.Key,
                        checked = settings.autofillCredentialProvider,
                        onChange = vm::setAfCredentialProvider
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Autofill service",
                        subtitle = "Legacy Autofill Framework support",
                        icon = Icons.Default.AutoAwesome,
                        checked = isAutofillActive,
                        onChange = { vm.openAutofillSettings(context) }
                    )
                }
            }

            // Info Card for Chrome
            SpectreCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Chrome to support third-party autofill services natively", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "1. Open Chrome's Settings and tap Autofill Services\n" +
                            "2. Choose Autofill using another service\n" +
                            "3. Confirm and restart Chrome",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { /* TODO: Open Link */ }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            SpectreCard {
                Column {
                    SettingsItem(
                        title = "Default match detection",
                        subtitle = settings.autofillMatchDetection.toString().lowercase().replaceFirstChar { it.uppercase() },
                        icon = Icons.Default.Domain,
                        onClick = { showMatchPicker = true }
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Inline suggestions",
                        subtitle = "Embeds autofill suggestions directly into compatible keyboards",
                        icon = Icons.Default.Keyboard,
                        checked = settings.autofillInlineSuggestions,
                        onChange = vm::setAfInline
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Manual selection",
                        subtitle = "Displays an option to manually search a vault for an entry",
                        icon = Icons.Default.TouchApp,
                        checked = settings.autofillManualSelection,
                        onChange = vm::setAfManual
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Respect Autofill disabled flag",
                        subtitle = "Disables autofill for a browser field that has autofill disabled flag set",
                        icon = Icons.Default.Flag,
                        checked = settings.autofillRespectDisabled,
                        onChange = vm::setAfRespectDisabled
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Auto-copy one-time passwords",
                        subtitle = "When filling a login information, automatically copy one-time passwords",
                        icon = Icons.Default.ContentCopy,
                        checked = settings.autofillAutoCopyOtp,
                        onChange = vm::setAfAutoCopyOtp
                    )
                    HubDivider()
                    SettingsItem(
                        title = "One-time password notification duration",
                        subtitle = "${settings.autofillOtpNotificationDuration} seconds",
                        icon = Icons.Default.NotificationsActive,
                        onClick = { showOtpDurPicker = true }
                    )
                }
            }

            SpectreCard {
                Column {
                    SettingsSwitchItem(
                        title = "Ask to save data",
                        subtitle = "Asks for updating the vault when filling of a form has completed",
                        icon = Icons.Default.Save,
                        checked = settings.autofillAskToSave,
                        onChange = vm::setAfAskSave
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Auto-save app or website info",
                        icon = Icons.Default.CloudUpload,
                        checked = settings.autofillAutoSave,
                        onChange = vm::setAfAutoSave
                    )
                    HubDivider()
                    SettingsItem(
                        title = "Block autofill",
                        subtitle = "Prevents offering autofill for these URIs",
                        icon = Icons.Default.Block,
                        onClick = { /* TODO */ }
                    )
                    HubDivider()
                    SettingsItem(
                        title = "Privileged apps",
                        subtitle = "Privileged apps can make credential requests on behalf of other services",
                        icon = Icons.Default.Apps,
                        onClick = { /* TODO */ }
                    )
                }
            }
        }
    }

    if (showMatchPicker) {
        PickerDialog(
            title = "Match Detection",
            options = UriMatchType.entries.map { it.toString().lowercase().replaceFirstChar { c -> c.uppercase() } },
            selectedIndex = UriMatchType.entries.indexOf(settings.autofillMatchDetection),
            onSelect = { vm.setAfMatchDetection(UriMatchType.entries[it]); showMatchPicker = false },
            onDismiss = { showMatchPicker = false }
        )
    }

    if (showOtpDurPicker) {
        PickerDialog(
            title = "OTP Duration",
            options = listOf("10 seconds", "20 seconds", "30 seconds", "60 seconds", "120 seconds"),
            selectedIndex = listOf(10, 20, 30, 60, 120).indexOf(settings.autofillOtpNotificationDuration).coerceAtLeast(0),
            onSelect = { 
                val dur = listOf(10, 20, 30, 60, 120)[it]
                vm.setAfOtpNotifDur(dur)
                showOtpDurPicker = false 
            },
            onDismiss = { showOtpDurPicker = false }
        )
    }
}

@Composable
private fun SecuritySettingsScreen(
    settings: SpectreSettings,
    onBack: () -> Unit,
    vm: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    var showTimeoutPicker by remember { mutableStateOf(false) }
    var showClipboardPicker by remember { mutableStateOf(false) }
    var showPanicPinDialog by remember { mutableStateOf(false) }
    var showBioPwPrompt by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            SpectreTopBar(
                title = "Security",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpectreCard {
                Column {
                    SettingsItem(
                        title = "Lock vault now",
                        icon = Icons.Default.Lock,
                        onClick = vm::lockVault
                    )
                    HubDivider()
                    SettingsItem(
                        title = "Vault lock timeout",
                        subtitle = settings.lockTimeout.label,
                        icon = Icons.Default.Timer,
                        onClick = { showTimeoutPicker = true }
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Lock on background",
                        subtitle = "Lock when app is minimised",
                        icon = Icons.Default.MobileFriendly,
                        checked = settings.lockOnBackground,
                        onChange = vm::setLockOnBackground
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Biometric unlock",
                        icon = Icons.Default.Fingerprint,
                        checked = settings.biometricUnlock,
                        onChange = { 
                            if (it) showBioPwPrompt = true 
                            else vm.setBiometricUnlock(false)
                        }
                    )
                }
            }

            if (showBioPwPrompt) {
                val activity = LocalContext.current as androidx.fragment.app.FragmentActivity
                PasswordConfirmDialog(
                    title = "Enable Biometric",
                    onConfirm = { pw -> vm.setBiometricUnlock(true, activity, pw); showBioPwPrompt = false },
                    onDismiss = { showBioPwPrompt = false }
                )
            }

            SpectreCard {
                Column {
                    SettingsSwitchItem(
                        title = "Screenshot protection",
                        subtitle = "Prevent screen capture",
                        icon = Icons.Default.Screenshot,
                        checked = settings.screenshotProtection,
                        onChange = vm::setScreenshotProtection
                    )
                    HubDivider()
                    SettingsItem(
                        title = "Clipboard clear",
                        subtitle = "${settings.clipboardClearSeconds}s after copy",
                        icon = Icons.Default.ContentPaste,
                        onClick = { showClipboardPicker = true }
                    )
                }
            }

            SpectreCard {
                Column {
                    SettingsSwitchItem(
                        title = "Duress / decoy vault",
                        subtitle = "Show empty vault on alternate PIN",
                        icon = Icons.Default.Security,
                        checked = settings.duressEnabled,
                        onChange = vm::setDuressEnabled
                    )
                    HubDivider()
                    SettingsItem(
                        title = "Panic PIN",
                        subtitle = if (settings.panicPin != null) "Configured — tap to change" else "Wipe local data instantly if this PIN is entered",
                        icon = Icons.Default.Warning,
                        onClick = { showPanicPinDialog = true }
                    )
                }
            }
        }
    }

    if (showTimeoutPicker) {
        PickerDialog(
            title = "Lock Timeout",
            options = LockTimeout.entries.map { it.label },
            selectedIndex = LockTimeout.entries.indexOf(settings.lockTimeout),
            onSelect = { vm.setLockTimeout(LockTimeout.entries[it]); showTimeoutPicker = false },
            onDismiss = { showTimeoutPicker = false }
        )
    }

    if (showClipboardPicker) {
        PickerDialog(
            title = "Clipboard Clear",
            options = listOf("10 seconds", "20 seconds", "30 seconds", "60 seconds", "Never"),
            selectedIndex = when(settings.clipboardClearSeconds) { 10->0; 20->1; 30->2; 60->3; else->4 },
            onSelect = { 
                val secs = listOf(10, 20, 30, 60, -1)[it]
                vm.setClipboardClear(secs)
                showClipboardPicker = false 
            },
            onDismiss = { showClipboardPicker = false }
        )
    }

    if (showPanicPinDialog) {
        PanicPinDialog(
            currentPin = settings.panicPin,
            onSetPin   = { vm.setPanicPin(it); showPanicPinDialog = false },
            onClearPin = { vm.clearPanicPin(); showPanicPinDialog = false },
            onDismiss  = { showPanicPinDialog = false },
        )
    }
}

@Composable
private fun AppearanceSettingsScreen(
    settings: SpectreSettings,
    onBack: () -> Unit,
    vm: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    var showThemePicker by remember { mutableStateOf(false) }
    var showSortPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            SpectreTopBar(
                title = "Appearance",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpectreCard {
                Column {
                    SettingsItem(
                        title = "Theme",
                        subtitle = settings.theme.toString().lowercase().replaceFirstChar { it.uppercase() },
                        icon = Icons.Default.Palette,
                        onClick = { showThemePicker = true }
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Compact vault list",
                        icon = Icons.AutoMirrored.Filled.ViewList,
                        checked = settings.compactVaultList,
                        onChange = vm::setCompactList
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Show bottom nav labels",
                        icon = Icons.AutoMirrored.Filled.Label,
                        checked = settings.showBottomNavLabels,
                        onChange = vm::setShowBottomNavLabels
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Show favicons",
                        subtitle = "Fetch website icons for login items",
                        icon = Icons.Default.Bookmark,
                        checked = settings.showFavicons,
                        onChange = vm::setShowFavicons
                    )
                    HubDivider()
                    SettingsItem(
                        title = "Default sort order",
                        subtitle = settings.sortOrder.label,
                        icon = Icons.AutoMirrored.Filled.Sort,
                        onClick = { showSortPicker = true }
                    )
                }
            }
        }
    }

    if (showThemePicker) {
        PickerDialog(
            title = "Choose Theme",
            options = SpectreTheme.entries.map { it.toString().lowercase().replaceFirstChar { c -> c.uppercase() } },
            selectedIndex = SpectreTheme.entries.indexOf(settings.theme),
            onSelect = { vm.setTheme(SpectreTheme.entries[it]); showThemePicker = false },
            onDismiss = { showThemePicker = false }
        )
    }

    if (showSortPicker) {
        PickerDialog(
            title = "Sort Order",
            options = VaultSortOrder.entries.map { it.label },
            selectedIndex = VaultSortOrder.entries.indexOf(settings.sortOrder),
            onSelect = { vm.setSortOrder(VaultSortOrder.entries[it]); showSortPicker = false },
            onDismiss = { showSortPicker = false }
        )
    }
}

@Composable
private fun WatchtowerSettingsScreen(
    settings: SpectreSettings,
    onBack: () -> Unit,
    vm: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            SpectreTopBar(
                title = "Watchtower",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpectreCard {
                Column {
                    SettingsSwitchItem(
                        title = "Check breached sites (HIBP)",
                        checked = settings.watchtowerCheckHibp,
                        onChange = vm::setWtHibp
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Scan for reused passwords",
                        checked = settings.watchtowerScanReused,
                        onChange = vm::setWtReused
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Scan for weak passwords",
                        checked = settings.watchtowerScanWeak,
                        onChange = vm::setWtWeak
                    )
                    HubDivider()
                    SettingsSwitchItem(
                        title = "Scan for inactive 2FA",
                        checked = settings.watchtowerScan2fa,
                        onChange = vm::setWt2fa
                    )
                }
            }
        }
    }
}

@Composable
private fun OtherSettingsScreen(
    settings: SpectreSettings,
    accounts: List<com.spectre.app.core.data.models.Account>,
    onBack: () -> Unit,
    vm: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    var showSyncPicker by remember { mutableStateOf(false) }
    var showServerUrlDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            SpectreTopBar(
                title = "Other",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsCategoryHeader("SYNC")
            SpectreCard {
                Column {
                    SettingsItem(
                        title = "Background sync interval",
                        subtitle = settings.backgroundSyncInterval.label,
                        icon = Icons.Default.Sync,
                        onClick = { showSyncPicker = true }
                    )
                    HubDivider()
                    SettingsItem(
                        title = "Custom Server",
                        subtitle = "Configure self-hosted instance",
                        icon = Icons.Default.Storage,
                        onClick = { showServerUrlDialog = true }
                    )
                }
            }

            SettingsCategoryHeader("DATA")
            SpectreCard {
                Column {
                    SettingsItem(
                        title = "Export Vault",
                        subtitle = "Export unencrypted JSON",
                        icon = Icons.Default.FileDownload,
                        onClick = { showExportDialog = true }
                    )
                    HubDivider()
                    var showPurgeConfirm by remember { mutableStateOf(false) }
                    SettingsItem(
                        title = "Purge Trash",
                        icon = Icons.Default.DeleteForever,
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { showPurgeConfirm = true }
                    )
                    if (showPurgeConfirm) {
                        AlertDialog(
                            onDismissRequest = { showPurgeConfirm = false },
                            title = { Text("Purge Trash") },
                            text = { Text("Are you sure? This cannot be undone.") },
                            confirmButton = {
                                Button(onClick = { vm.purgeTrash(); showPurgeConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Purge") }
                            },
                            dismissButton = { TextButton(onClick = { showPurgeConfirm = false }) { Text("Cancel") } }
                        )
                    }
                }
            }

            SettingsCategoryHeader("ABOUT")
            SpectreCard {
                Column {
                    SettingsItem(title = "Version", subtitle = com.spectre.app.BuildConfig.VERSION_NAME, icon = Icons.Default.Info)
                    HubDivider()
                    SettingsItem(title = "GitHub", subtitle = "View source code", icon = Icons.Default.Code)
                }
            }

            SettingsCategoryHeader("ACCOUNT")
            SpectreCard {
                accounts.forEach { account ->
                    SettingsItem(
                        title = account.email,
                        subtitle = if (account.isActive) "Active" else "Switch to this account",
                        icon = Icons.Default.AccountCircle,
                        onClick = { if (!account.isActive) vm.switchAccount(account.id) },
                        trailing = {
                            if (account.isActive) {
                                IconButton(onClick = { showSignOutConfirm = account.id }) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showSyncPicker) {
        PickerDialog(
            title = "Sync Interval",
            options = SyncInterval.entries.map { it.label },
            selectedIndex = SyncInterval.entries.indexOf(settings.backgroundSyncInterval),
            onSelect = { vm.setSyncInterval(SyncInterval.entries[it]); showSyncPicker = false },
            onDismiss = { showSyncPicker = false }
        )
    }

    if (showServerUrlDialog) {
        var url by remember { mutableStateOf(settings.customServerUrl) }
        AlertDialog(
            onDismissRequest = { showServerUrlDialog = false },
            title = { Text("Custom Server") },
            text = { 
                OutlinedTextField(
                    value = url, 
                    onValueChange = { url = it }, 
                    label = { Text("URL") }, 
                    placeholder = { Text("https://your-bitwarden-server.com") },
                    shape = RoundedCornerShape(12.dp), 
                    modifier = Modifier.fillMaxWidth()
                ) 
            },
            confirmButton = { 
                Button(onClick = { vm.setCustomServerUrl(url); showServerUrlDialog = false }) { Text("Save") } 
            },
            dismissButton = { 
                TextButton(onClick = { showServerUrlDialog = false }) { Text("Cancel") } 
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showExportDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Vault") },
            text = { Text("This will export unencrypted JSON. Proceed with caution.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { 
                Button(onClick = { vm.exportVault(context); showExportDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    showSignOutConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = null },
            title = { Text("Sign out?") },
            text = { Text("Are you sure you want to sign out? You will need your master password to sign back in.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = { vm.signOut(id); showSignOutConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { showSignOutConfirm = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}


/**
 * A standard row for the Settings Hub.
 */
@Composable
private fun HubItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HubDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
