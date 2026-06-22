package com.spectre.app.feature.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.crypto.KeePassReader
import com.spectre.app.core.data.models.DecryptedCipher
import com.spectre.app.core.data.models.CipherType
import com.spectre.app.core.data.models.LoginData
import com.spectre.app.core.data.models.LoginUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.spectre.app.di.IoDispatcher
import java.util.UUID
import javax.inject.Inject

data class KeePassUiState(
    val fileUri: Uri?        = null,
    val fileName: String     = "",
    val password: String     = "",
    val showPassword: Boolean = false,
    val keyFilePath: String  = "",
    val isLoading: Boolean   = false,
    val error: String?       = null,
)

@HiltViewModel
class KeePassViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(KeePassUiState())
    val state: StateFlow<KeePassUiState> = _state.asStateFlow()

    private val _success = MutableSharedFlow<List<DecryptedCipher>>()
    val success: SharedFlow<List<DecryptedCipher>> = _success.asSharedFlow()

    fun onFileSelected(uri: Uri, name: String) {
        _state.update { it.copy(fileUri = uri, fileName = name, error = null) }
    }

    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onToggleShowPassword()      = _state.update { it.copy(showPassword = !it.showPassword) }

    fun unlock() {
        val s = _state.value
        val uri = s.fileUri ?: run {
            _state.update { it.copy(error = "Please select a .kdbx file first.") }
            return
        }
        if (s.password.isBlank()) {
            _state.update { it.copy(error = "Please enter the database password.") }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = runCatching {
                val stream  = context.contentResolver.openInputStream(uri)
                    ?: error("Could not open file")
                val entries = KeePassReader.open(stream, s.password).getOrThrow()
                entries.map { entry ->
                    DecryptedCipher(
                        id             = entry.uuid.ifBlank { UUID.randomUUID().toString() },
                        accountId      = "keepass",
                        organizationId = null,
                        folderId       = null,
                        collectionIds  = emptyList(),
                        type           = CipherType.LOGIN,
                        name           = entry.title ?: "Untitled",
                        notes          = entry.notes,
                        favorite       = false,
                        reprompt       = false,
                        deletedDate    = null,
                        revisionDate   = "",
                        creationDate   = null,
                        loginData      = LoginData(
                            username             = entry.username,
                            password             = entry.password,
                            passwordRevisionDate = null,
                            totp                 = entry.totp,
                            uris                 = listOfNotNull(entry.url?.let { LoginUri(it) }),
                        ),
                        cardData       = null,
                        identityData   = null,
                    )
                }
            }
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { _success.emit(it) },
                onFailure = { _state.update { s -> s.copy(error = it.message ?: "Invalid password or corrupted file.") } }
            )
        }
    }
}

@Composable
fun KeePassLoginScreen(
    onBack: () -> Unit,
    onUnlocked: (List<DecryptedCipher>) -> Unit,
    vm: KeePassViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) { vm.success.collect { onUnlocked(it) } }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.onFileSelected(it, it.lastPathSegment ?: "database.kdbx") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open KeePass vault", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            Icon(
                Icons.Filled.FolderOpen,
                null,
                modifier = Modifier.size(56.dp),
                tint     = Color(0xFF4CAF50),
            )
            Spacer(Modifier.height(12.dp))
            Text("KeePass / KDBX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Open a local encrypted database file", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(40.dp))

            // File picker button
            OutlinedButton(
                onClick  = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.AttachFile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.fileUri == null) "Select .kdbx file" else state.fileName)
            }

            if (state.fileUri != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color  = MaterialTheme.colorScheme.primaryContainer,
                    shape  = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.fileName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            com.spectre.app.core.ui.components.IncognitoInput {
                OutlinedTextField(
                    value         = state.password,
                    onValueChange = vm::onPasswordChange,
                    label         = { Text("Database Password") },
                    leadingIcon   = { Icon(Icons.Filled.Lock, null) },
                    trailingIcon  = {
                        IconButton(onClick = vm::onToggleShowPassword) {
                            Icon(if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                    },
                    visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done,
                        // Incognito keyboard — no learning, no suggestions
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); vm.unlock() }),
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(14.dp),
                )
            }

            AnimatedVisibility(visible = state.error != null) {
                state.error?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color  = MaterialTheme.colorScheme.errorContainer,
                        shape  = RoundedCornerShape(12.dp),
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
                onClick  = { focus.clearFocus(); vm.unlock() },
                enabled  = !state.isLoading && state.fileUri != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.LockOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock vault")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
