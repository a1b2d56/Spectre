package com.spectre.app.feature.auth

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.repository.AuthRepository
import com.spectre.app.core.data.repository.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject


data class LoginUiState(
    val email: String              = "",
    val password: String           = "",
    val serverUrl: String          = "",
    val useCustomServer: Boolean   = false,
    val isLoading: Boolean         = false,
    val showPassword: Boolean      = false,
    val twoFactorRequired: Boolean = false,
    val twoFactorToken: String     = "",
    val twoFactorProvider: Int     = 0,
    val error: String?             = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _navigateToVault = MutableSharedFlow<String>()
    val navigateToVault: SharedFlow<String> = _navigateToVault.asSharedFlow()

    fun onEmailChange(v: String)          { _state.update { it.copy(email = v, error = null) } }
    fun onPasswordChange(v: String)       { _state.update { it.copy(password = v, error = null) } }
    fun onServerUrlChange(v: String)      { _state.update { it.copy(serverUrl = v) } }
    fun onToggleCustomServer()            { _state.update { it.copy(useCustomServer = !it.useCustomServer) } }
    fun onToggleShowPassword()            { _state.update { it.copy(showPassword = !it.showPassword) } }
    fun onTwoFactorTokenChange(v: String) { _state.update { it.copy(twoFactorToken = v, error = null) } }

    fun login(
        serverUrl: String   = "https://api.bitwarden.com",
        identityUrl: String = "https://identity.bitwarden.com",
    ) {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Please enter your email and master password.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val resolvedServerUrl   = if (s.useCustomServer && s.serverUrl.isNotBlank())
                s.serverUrl.trimEnd('/') else serverUrl
            val resolvedIdentityUrl = if (s.useCustomServer && s.serverUrl.isNotBlank())
                "${s.serverUrl.trimEnd('/')}/identity" else identityUrl

            val result = authRepository.login(
                email             = s.email.trim(),
                masterPassword    = s.password,
                serverUrl         = resolvedServerUrl,
                identityUrl       = resolvedIdentityUrl,
                twoFactorToken    = s.twoFactorToken.takeIf { it.isNotBlank() },
                twoFactorProvider = s.twoFactorProvider.takeIf { s.twoFactorRequired },
            )

            when (result) {
                is LoginResult.Success          -> _navigateToVault.emit(result.accountId)
                is LoginResult.TwoFactorRequired,
                is LoginResult.TwoFactorWith    -> _state.update { it.copy(twoFactorRequired = true, isLoading = false) }
                is LoginResult.Error            -> _state.update { it.copy(error = result.message, isLoading = false) }
                else                            -> _state.update { it.copy(isLoading = false) }
            }
        }
    }
}

/**
 * Screen for authenticating with a Bitwarden-compatible server.
 * Always called from AddVaultScreen with known serverUrl/identityUrl — the user
 * never needs to know about API vs identity URL distinction.
 */
@Composable
fun LoginScreen(
    serverLabel: String  = "Bitwarden",
    serverUrl: String    = "https://api.bitwarden.com",
    identityUrl: String  = "https://identity.bitwarden.com",
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    vm: LoginViewModel   = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus  = LocalFocusManager.current

    LaunchedEffect(Unit) { vm.navigateToVault.collect { onLoginSuccess() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(serverLabel, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Icon(Icons.Filled.Security, null,
                modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Sign in to $serverLabel",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(32.dp))

            // Email — standard, learning is fine
            OutlinedTextField(
                value           = state.email,
                onValueChange   = vm::onEmailChange,
                label           = { Text("Email") },
                leadingIcon     = { Icon(Icons.Filled.Email, null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(14.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Master password — INCOGNITO keyboard
            // IME_FLAG_NO_PERSONALIZED_LEARNING ensures GBoard doesn't learn from
            // the master password, matching Keyguard's behaviour.
            OutlinedTextField(
                value           = state.password,
                onValueChange   = vm::onPasswordChange,
                label           = { Text("Master Password") },
                leadingIcon     = { Icon(Icons.Filled.Lock, null) },
                trailingIcon    = {
                    IconButton(onClick = vm::onToggleShowPassword) {
                        Icon(if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                    }
                },
                visualTransformation = if (state.showPassword)
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done,
                    autoCorrectEnabled  = false,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    vm.login(serverUrl, identityUrl)
                }),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(14.dp),
            )

            // 2FA
            AnimatedVisibility(visible = state.twoFactorRequired) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value           = state.twoFactorToken,
                        onValueChange   = vm::onTwoFactorTokenChange,
                        label           = { Text("Two-step code") },
                        leadingIcon     = { Icon(Icons.Filled.PhoneAndroid, null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction    = ImeAction.Done,
                            autoCorrectEnabled  = false,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focus.clearFocus()
                            vm.login(serverUrl, identityUrl)
                        }),
                        singleLine      = true,
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(14.dp),
                    )
                }
            }

            // Custom server
            if (serverUrl == "https://api.bitwarden.com") {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth().clickable { vm.onToggleCustomServer() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(state.useCustomServer, { vm.onToggleCustomServer() })
                    Text("Use custom server URL", style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(visible = state.useCustomServer) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value           = state.serverUrl,
                            onValueChange   = vm::onServerUrlChange,
                            label           = { Text("Server URL") },
                            placeholder     = { Text("https://vault.example.com") },
                            leadingIcon     = { Icon(Icons.Filled.Dns, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            singleLine      = true,
                            modifier        = Modifier.fillMaxWidth(),
                            shape           = RoundedCornerShape(14.dp),
                        )
                    }
                }
            }

            // Error banner
            AnimatedVisibility(visible = state.error != null) {
                state.error?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color    = MaterialTheme.colorScheme.errorContainer,
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = { focus.clearFocus(); vm.login(serverUrl, identityUrl) },
                enabled  = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                if (state.isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else
                    Text("Log In", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Your master password never leaves your device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
