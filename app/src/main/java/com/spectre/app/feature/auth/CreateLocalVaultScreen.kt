package com.spectre.app.feature.auth

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
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


data class CreateVaultUiState(
    val vaultName: String      = "",
    val password: String       = "",
    val confirmPassword: String = "",
    val showPassword: Boolean  = false,
    val isLoading: Boolean     = false,
    val error: String?         = null,
)

@HiltViewModel
class CreateVaultViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateVaultUiState())
    val state: StateFlow<CreateVaultUiState> = _state.asStateFlow()

    private val _navigateToVault = MutableSharedFlow<Unit>()
    val navigateToVault: SharedFlow<Unit> = _navigateToVault.asSharedFlow()

    fun onNameChange(v: String)            { _state.update { it.copy(vaultName = v, error = null) } }
    fun onPasswordChange(v: String)        { _state.update { it.copy(password = v, error = null) } }
    fun onConfirmPasswordChange(v: String) { _state.update { it.copy(confirmPassword = v, error = null) } }
    fun onToggleShowPassword()             { _state.update { it.copy(showPassword = !it.showPassword) } }

    fun createVault() {
        val s = _state.value

        // Validation
        if (s.vaultName.isBlank()) {
            _state.update { it.copy(error = "Please enter a name for your vault.") }
            return
        }
        if (s.password.length < 8) {
            _state.update { it.copy(error = "Master password must be at least 8 characters.") }
            return
        }
        if (s.password != s.confirmPassword) {
            _state.update { it.copy(error = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val result = authRepository.createLocalVault(s.vaultName.trim(), s.password)) {
                is LoginResult.Success -> _navigateToVault.emit(Unit)
                is LoginResult.Error   -> _state.update { it.copy(error = result.message, isLoading = false) }
                else                   -> _state.update { it.copy(isLoading = false) }
            }
        }
    }
}

/**
 * Screen for setting up a local vault without a cloud provider.
 */
@Composable
fun CreateLocalVaultScreen(
    onVaultCreated: () -> Unit,
    vm: CreateVaultViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) { vm.navigateToVault.collect { onVaultCreated() } }

    Scaffold(
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
            Spacer(Modifier.height(60.dp))

            // Shield icon
            Icon(
                Icons.Filled.Security, null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Create Your Vault",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Your vault is stored locally on this device.\nYou can sync with Bitwarden later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.height(36.dp))

            // Vault name
            OutlinedTextField(
                value           = state.vaultName,
                onValueChange   = vm::onNameChange,
                label           = { Text("Vault Name") },
                placeholder     = { Text("Personal") },
                leadingIcon     = { Icon(Icons.Filled.Folder, null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction      = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(14.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Master password
            OutlinedTextField(
                value           = state.password,
                onValueChange   = vm::onPasswordChange,
                label           = { Text("Master Password") },
                leadingIcon     = { Icon(Icons.Filled.Lock, null) },
                trailingIcon    = {
                    IconButton(onClick = vm::onToggleShowPassword) {
                        Icon(
                            if (state.showPassword) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility, null,
                        )
                    }
                },
                visualTransformation = if (state.showPassword)
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Next,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(14.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Confirm password
            OutlinedTextField(
                value           = state.confirmPassword,
                onValueChange   = vm::onConfirmPasswordChange,
                label           = { Text("Confirm Master Password") },
                leadingIcon     = { Icon(Icons.Filled.LockReset, null) },
                visualTransformation = if (state.showPassword)
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    vm.createVault()
                }),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(14.dp),
            )

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

            Spacer(Modifier.height(28.dp))

            // Create button
            Button(
                onClick  = { focus.clearFocus(); vm.createVault() },
                enabled  = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                if (state.isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else
                    Text("Create Vault", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Your master password cannot be recovered if forgotten.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
