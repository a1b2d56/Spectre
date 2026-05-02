package com.spectre.app.feature.auth

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
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
) : ViewModel() {

    private val _unlocked    = MutableSharedFlow<Unit>()
    val unlocked: SharedFlow<Unit> = _unlocked.asSharedFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun unlockWithPassword(accountId: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value   = null
            when (val result = authRepository.unlockWithMasterPassword(accountId, password)) {
                is UnlockResult.Success  -> _unlocked.emit(Unit)
                is UnlockResult.InvalidPin -> _error.value = "Incorrect master password."
                is UnlockResult.Error    -> _error.value = result.message
            }
            _loading.value = false
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
    val error    by vm.error.collectAsState()
    val loading  by vm.loading.collectAsState()

    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showPwField  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.unlocked.collect { onUnlocked() }
    }

    // Auto-trigger biometric prompt on appearance
    LaunchedEffect(Unit) {
        if (vm.biometricUnlock.isEnrolled()) {
            // Biometric unlock requires the encrypted user key stored during login.
            // Here we'd pass the stored encrypted bytes — simplified trigger shown.
            // Full implementation in AuthRepository ties biometric to the stored key.
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint     = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Vault Locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Authenticate to continue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(40.dp))

            // Biometric button
            if (vm.biometricUnlock.isEnrolled()) {
                FilledTonalButton(
                    onClick = {
                        vm.biometricUnlock.promptToDecrypt(
                            activity      = context as FragmentActivity,
                            encryptedData = "",   // populated from stored session
                            iv            = "",
                            onResult      = { /* handle result */ },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Fingerprint, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use Biometrics", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showPwField = !showPwField }) {
                    Text("Use master password instead")
                }
            } else {
                showPwField = true
            }

            // Master password field
            AnimatedVisibility(
                visible = showPwField,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it },
                        label         = { Text("Master Password") },
                        leadingIcon   = { Icon(Icons.Filled.Key, null) },
                        trailingIcon  = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.unlockWithPassword(accountId, password) }),
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick  = { vm.unlockWithPassword(accountId, password) },
                        enabled  = password.isNotBlank() && !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("Unlock", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            AnimatedVisibility(visible = error != null) {
                error?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
