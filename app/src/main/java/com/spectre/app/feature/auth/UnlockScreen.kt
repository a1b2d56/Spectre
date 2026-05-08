package com.spectre.app.feature.auth

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.repository.AuthRepository
import com.spectre.app.core.data.repository.UnlockResult
import com.spectre.app.core.security.BiometricUnlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val biometricUnlock: BiometricUnlock,
    private val prefs: com.spectre.app.core.data.datastore.SpectrePreferences,
) : ViewModel() {

    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.spectre.app.core.data.datastore.SpectreSettings())

    private val _unlocked = MutableSharedFlow<Unit>()
    val unlocked: SharedFlow<Unit> = _unlocked.asSharedFlow()

    private val _error   = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun unlockWithPassword(accountId: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value   = null

            // Check for Panic PIN
            val currentSettings = settings.value
            if (currentSettings.panicPin?.isNotBlank() == true && password == currentSettings.panicPin) {
                authRepository.purgeAllData()
                _unlocked.emit(Unit) // Exit screen to empty state
                return@launch
            }

            when (val result = authRepository.unlockWithMasterPassword(accountId, password)) {
                is UnlockResult.Success   -> _unlocked.emit(Unit)
                is UnlockResult.InvalidPin -> _error.value = "Incorrect master password."
                is UnlockResult.Error     -> _error.value = result.message
            }
            _loading.value = false
        }
    }

    fun unlockWithBiometrics(accountId: String, activity: androidx.fragment.app.FragmentActivity) {
        viewModelScope.launch {
            biometricUnlock.promptToUnlock(activity) { result ->
                if (result is com.spectre.app.core.security.BiometricResult.Success) {
                    val password = String(result.decryptedData)
                    unlockWithPassword(accountId, password)
                }
            }
        }
    }
}

@Composable
fun UnlockScreen(
    accountId: String,
    onUnlocked: () -> Unit,
    vm: UnlockViewModel = hiltViewModel(),
) {
    val context  = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val error    by vm.error.collectAsStateWithLifecycle()
    val loading  by vm.loading.collectAsStateWithLifecycle()

    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    
    val canUseBiometrics = settings.biometricUnlock && 
                          vm.biometricUnlock.isEnrolled() && 
                          vm.biometricUnlock.hasStoredSecret()

    var showPwField  by remember { mutableStateOf(!canUseBiometrics) }

    LaunchedEffect(Unit) {
        vm.unlocked.collect { onUnlocked() }
    }

    // Auto-prompt biometric on first appearance
    LaunchedEffect(canUseBiometrics) {
        if (canUseBiometrics) {
            vm.unlockWithBiometrics(accountId, context as androidx.fragment.app.FragmentActivity)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    radius = 800f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Vault Locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Authenticate to continue", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(40.dp))

            if (canUseBiometrics) {
                FilledTonalButton(
                    onClick  = {
                        vm.unlockWithBiometrics(accountId, context as androidx.fragment.app.FragmentActivity)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Fingerprint, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use Biometrics", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showPwField = !showPwField }) {
                    Text("Use master password instead")
                }
            }

            AnimatedVisibility(
                visible = showPwField,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    // Incognito password field
                    OutlinedTextField(
                        value           = password,
                        onValueChange   = { password = it },
                        label           = { Text("Master Password") },
                        leadingIcon     = { Icon(Icons.Filled.Key, null) },
                        trailingIcon    = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done,
                            autoCorrectEnabled  = false,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { vm.unlockWithPassword(accountId, password) }
                        ),
                        singleLine      = true,
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick  = { vm.unlockWithPassword(accountId, password) },
                        enabled  = password.isNotBlank() && !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                    ) {
                        if (loading)
                            CircularProgressIndicator(Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else
                            Text("Unlock", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            AnimatedVisibility(visible = error != null) {
                error?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
