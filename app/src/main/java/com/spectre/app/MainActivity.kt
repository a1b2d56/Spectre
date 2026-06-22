package com.spectre.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillManager
import android.service.autofill.FillResponse
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.spectre.app.autofill.AutofillHelper
import com.spectre.app.autofill.AutofillParser
import com.spectre.app.core.data.datastore.SpectrePreferences
import com.spectre.app.core.data.datastore.SpectreSettings
import com.spectre.app.core.data.models.*
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.LockState
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.ui.theme.SpectreTheme
import com.spectre.app.core.ui.theme.SpectreAppTheme
import com.spectre.app.core.navigation.Route
import com.spectre.app.navigation.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefs: SpectrePreferences,
    private val session: VaultSession,
    private val vaultRepository: VaultRepository,
    private val authRepository: com.spectre.app.core.data.repository.AuthRepository,
    private val autofillHelper: AutofillHelper,
) : ViewModel() {

    val settings: StateFlow<SpectreSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpectreSettings())

    val activeAccount: StateFlow<Account?> = authRepository.observeActiveAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)


    val startDestination: StateFlow<Route?> = combine(
        prefs.settings,
        session.lockState,
    ) { s: com.spectre.app.core.data.datastore.SpectreSettings, lock: com.spectre.app.core.security.LockState ->
        when {
            s.activeAccountId == null  -> Route.Auth
            lock == LockState.UNLOCKED -> Route.Vault
            lock == LockState.LOCKED   -> Route.Unlock(s.activeAccountId)
            else                       -> Route.Auth
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

    /**
     * Called when the vault unlocks after an autofill authentication request.
     * Fetches matching ciphers and builds a FillResponse to return to the system.
     */
    suspend fun buildAutofillResponse(
        targetPackage: String?,
        targetDomain: String?,
        appPackageName: String,
    ): FillResponse? {
        val accountId = session.activeAccountId ?: return null
        val allCiphers = vaultRepository.getAllDecryptedCiphers(accountId)
        val matched = autofillHelper.findMatchingCiphers(allCiphers, targetPackage, targetDomain)

        if (matched.isEmpty()) return null

        val parser = AutofillParser()
        // Build a minimal ParsedAutofillStructure for the helper
        val fakeStructure = com.spectre.app.autofill.ParsedAutofillStructure(
            credentialFields = emptyList(), // We can't rebuild these without the structure
            packageName = targetPackage,
            webDomain = targetDomain,
        )

        return autofillHelper.buildFillResponse(matched, fakeStructure, appPackageName)
    }

    /**
     * Saves credentials captured by onSaveRequest to the vault.
     */
    fun saveAutofillCredentials(
        username: String?,
        password: String?,
        domain: String?,
        packageName: String?,
    ) {
        val accountId = session.activeAccountId ?: return
        viewModelScope.launch {
            val name = domain ?: packageName ?: "Unknown site"
            val uris = mutableListOf<LoginUri>()
            domain?.let { uris.add(LoginUri(uri = "https://$it")) }
            packageName?.let { uris.add(LoginUri(uri = "androidapp://$it")) }

            val cipher = DecryptedCipher(
                id = java.util.UUID.randomUUID().toString(),
                accountId = accountId,
                organizationId = null,
                folderId = null,
                type = CipherType.LOGIN,
                name = name,
                notes = null,
                favorite = false,
                reprompt = false,
                deletedDate = null,
                revisionDate = java.time.Instant.now().toString(),
                creationDate = java.time.Instant.now().toString(),
                loginData = LoginData(
                    username = username,
                    password = password,
                    uris = uris,
                ),
                cardData = null,
                identityData = null,
            )

            vaultRepository.createCipher(cipher)
        }
    }
}

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {

    @Inject lateinit var session: VaultSession
    @Inject lateinit var prefs: SpectrePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val settings          by vm.settings.collectAsStateWithLifecycle()
            val startDestination  by vm.startDestination.collectAsStateWithLifecycle()

            LaunchedEffect(settings.screenshotProtection) {
                if (settings.screenshotProtection) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Handle autofill save prompt
            val isAutofillSave = intent?.getBooleanExtra("autofill_save", false) == true
            if (isAutofillSave) {
                val saveUsername = intent?.getStringExtra("save_username")
                val savePassword = intent?.getStringExtra("save_password")
                val saveDomain = intent?.getStringExtra("save_domain")
                val savePackage = intent?.getStringExtra("save_package")

                SpectreAppTheme(
                    appTheme = settings.theme,
                    fontScale = settings.fontScale,
                    isBold = settings.isBold,
                    fontFamilyKey = settings.fontFamily
                ) {
                    AutofillSavePrompt(
                        username = saveUsername,
                        password = savePassword,
                        domain = saveDomain,
                        onSave = {
                            vm.saveAutofillCredentials(saveUsername, savePassword, saveDomain, savePackage)
                            finish()
                        },
                        onDismiss = { finish() },
                    )
                }
                return@setContent
            }

            val openPage = intent?.getStringExtra("open_page")

            SpectreAppTheme(
                appTheme = settings.theme,
                fontScale = settings.fontScale,
                isBold = settings.isBold,
                fontFamilyKey = settings.fontFamily
            ) {
                val start = startDestination
                val account by vm.activeAccount.collectAsStateWithLifecycle()
                if (start != null) {
                    SpectreApp(
                        startDestination = start,
                        settings = settings,
                        activeAccount = account,
                        initialPage = openPage
                    )
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        session.recordActivity()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val s = prefs.settings.first()
            session.checkTimeout(s.lockTimeout.seconds)
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            val s = prefs.settings.first()
            if (s.lockOnBackground) {
                session.lock()
            } else {
                session.recordActivity()
            }
        }
    }
}

@Composable
fun SpectreApp(
    startDestination: Route,
    settings: SpectreSettings,
    activeAccount: Account?,
    initialPage: String? = null
) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            SpectreNavGraph(
                navController    = navController,
                startDestination = startDestination,
                activeAccount    = activeAccount,
                initialPage      = initialPage
            )
        }
    }
}


/**
 * Prompt shown when Android's autofill system captures new credentials.
 */
@Composable
private fun AutofillSavePrompt(
    username: String?,
    password: String?,
    domain: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Save, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Save to Spectre?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Would you like to save this credential to your vault?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!domain.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Language, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
                if (!username.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(username, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!password.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("••••••••", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(12.dp),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
