package com.spectre.app.feature.generator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.models.*
import com.spectre.app.core.ui.components.*
import com.spectre.app.core.ui.theme.*
import com.spectre.app.core.utils.suspendRunCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeneratorUiState(
    val result: GeneratedResult?    = null,
    val config: GeneratorConfig     = GeneratorConfig(),
    val history: List<String>       = emptyList(),
    val availableEmails: List<String> = emptyList(),
)

@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val generator: PasswordGenerator,
    private val historyDao: com.spectre.app.core.data.database.dao.GeneratorHistoryDao,
    private val accountDao: com.spectre.app.core.data.database.dao.AccountDao,
    private val crypto: com.spectre.app.core.crypto.BitwardenCrypto,
    private val session: com.spectre.app.core.security.VaultSession,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorUiState())
    val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    init { 
        regenerate()
        viewModelScope.launch {
            historyDao.observeHistory().collect { entities ->
                val userKey = session.getUserKeyOrNull()
                val decrypted = if (userKey == null) emptyList<String>() else entities.mapNotNull { suspendRunCatching { crypto.decryptToString(it.password, userKey) }.getOrNull() }
                _state.update { it.copy(history = decrypted) }
            }
        }
        // Fetch all emails from available accounts
        viewModelScope.launch {
            accountDao.observeAll().collect { accounts ->
                val emails = accounts.map { it.email }.distinct()
                _state.update { it.copy(availableEmails = emails) }
                
                // If config is empty, use the active one
                if (_state.value.config.emailBase.isBlank()) {
                    session.activeAccountId?.let { id ->
                        accounts.find { it.id == id }?.email?.let { email ->
                            val base = email.substringBefore("@")
                            val domain = email.substringAfter("@")
                            _state.update { it.copy(config = it.config.copy(emailBase = base, emailDomain = domain)) }
                        }
                    }
                }
            }
        }
    }

    fun regenerate() {
        viewModelScope.launch {
            _isGenerating.value = true
            val result = generator.generate(_state.value.config)
            _state.update { it.copy(result = result) }
            _isGenerating.value = false
        }
    }

    fun updateConfig(config: GeneratorConfig) {
        _state.update { it.copy(config = config) }
        regenerate()
    }

    fun copyToClipboard(value: String? = null) {
        val targetValue = value ?: _state.value.result?.value ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Generated password", targetValue))
        com.spectre.app.core.worker.ClipboardClearWorker.enqueue(context, 30L)
        
        if (value == null) { // Only add to history if it's the current result
            viewModelScope.launch {
                val userKey = session.getUserKeyOrNull() ?: return@launch
                val encPw = crypto.encryptString(targetValue, userKey).encode()
                historyDao.insert(com.spectre.app.core.data.database.entities.GeneratorHistoryEntity(password = encPw))
            }
        }
        
        viewModelScope.launch { _snackbar.emit("Copied to clipboard") }
    }
    
    fun clearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }
}

@Composable
fun GeneratorScreen(
    modifier: Modifier = Modifier,
    vm: GeneratorViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.snackbar.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState, snackbar = { SpectreSnackbar(it) }) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SpectreTopBar(title = "Generator")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // Premium Output Card
            PremiumOutputCard(
                result       = state.result,
                isGenerating = isGenerating,
                onRegenerate = vm::regenerate,
                onCopy       = { vm.copyToClipboard() }
            )

            // Tabs for Modes
            ModeTabs(
                mode     = state.config.mode,
                onChange = { vm.updateConfig(state.config.copy(mode = it)) }
            )

            // Options Section
            AnimatedContent(
                targetState = state.config.mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "options"
            ) { mode ->
                when (mode) {
                    GeneratorMode.PASSWORD   -> AdvancedPasswordOptions(state.config, vm::updateConfig)
                    GeneratorMode.PASSPHRASE -> AdvancedPassphraseOptions(state.config, vm::updateConfig)
                    GeneratorMode.USERNAME   -> AdvancedUsernameOptions(state.config, vm::updateConfig)
                    GeneratorMode.PIN        -> AdvancedPinOptions(state.config, vm::updateConfig)
                    GeneratorMode.SSH_KEY    -> AdvancedSshOptions(state.config, vm::updateConfig)
                    GeneratorMode.EMAIL      -> AdvancedEmailOptions(state.config, vm.state.value.availableEmails, vm::updateConfig)
                }
            }

            // History Section
            if (state.history.isNotEmpty()) {
                HistorySection(
                    history = state.history,
                    onCopy  = vm::copyToClipboard,
                    onClear = vm::clearHistory
                )
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
fun colorizePassword(password: String): AnnotatedString {
    val numberColor = Color(0xFF4ADE80) // Green
    val symbolColor = Color(0xFFFBBF24) // Yellow
    val defaultColor = MaterialTheme.colorScheme.onSurface

    return buildAnnotatedString {
        password.forEach { char ->
            when {
                char.isDigit() -> withStyle(SpanStyle(color = numberColor)) { append(char) }
                !char.isLetterOrDigit() -> withStyle(SpanStyle(color = symbolColor)) { append(char) }
                else -> withStyle(SpanStyle(color = defaultColor)) { append(char) }
            }
        }
    }
}

@Composable
private fun PremiumOutputCard(
    result: GeneratedResult?,
    isGenerating: Boolean,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit
) {
    val strengthColor = when (result?.strength) {
        0 -> StrengthWeak
        1 -> StrengthWeak
        2 -> StrengthFair
        3 -> StrengthGood
        4 -> StrengthStrong
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
            .border(
                1.dp, 
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)), 
                RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        // Password Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .animateContentSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = isGenerating to (result?.value ?: "••••••••"),
                animationSpec = tween(200),
                label = "password_crossfade"
            ) { (loading, password) ->
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text(
                        text = if (password == "••••••••") AnnotatedString(password) else colorizePassword(password),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Strength Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when(result?.strength) {
                    0 -> "DANGER"; 1 -> "WEAK"; 2 -> "FAIR"; 3 -> "GOOD"; else -> "EXCELLENT"
                },
                style = MaterialTheme.typography.labelLarge,
                color = strengthColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${result?.value?.length ?: 0} chars",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        
        val frac = ((result?.strength ?: 0) + 1) / 5f
        val animFrac by animateFloatAsState(frac, tween(600), label = "str")
        LinearProgressIndicator(
            progress   = { animFrac },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color      = strengthColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onRegenerate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
            Button(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeTabs(mode: GeneratorMode, onChange: (GeneratorMode) -> Unit) {
    val modes = listOf(
        GeneratorMode.PASSWORD   to "Password",
        GeneratorMode.PASSPHRASE to "Passphrase",
        GeneratorMode.PIN        to "PIN",
        GeneratorMode.USERNAME   to "Username",
        GeneratorMode.SSH_KEY    to "SSH Key",
        GeneratorMode.EMAIL      to "Mail Alias"
    )
    val selectedIndex = modes.indexOfFirst { it.first == mode }.coerceAtLeast(0)

    SecondaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor   = Color.Transparent,
        contentColor     = MaterialTheme.colorScheme.primary,
        edgePadding      = 0.dp,
        divider          = {},
        indicator        = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedIndex),
                color    = MaterialTheme.colorScheme.primary,
                height   = 3.dp
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        modes.forEachIndexed { index, (m, label) ->
            Tab(
                selected = mode == m,
                onClick  = { onChange(m) },
                text     = {
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                },
                selectedContentColor   = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdvancedPasswordOptions(config: GeneratorConfig, update: (GeneratorConfig) -> Unit) {
    SpectreCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Configuration", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(Modifier.height(16.dp))

        // Length Slider (Up to 128)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Length", style = MaterialTheme.typography.bodyMedium)
            Text("${config.length}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value         = config.length.toFloat(),
            onValueChange = { update(config.copy(length = it.toInt())) },
            valueRange    = 8f..128f,
            steps         = 119
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

        // Char Toggles
        OptionSwitch("Uppercase (A-Z)", config.useUppercase) { update(config.copy(useUppercase = it)) }
        OptionSwitch("Lowercase (a-z)", config.useLowercase) { update(config.copy(useLowercase = it)) }
        OptionSwitch("Numbers (0-9)",   config.useNumbers)   { update(config.copy(useNumbers = it)) }
        OptionSwitch("Symbols (!@#$)",  config.useSymbols)   { update(config.copy(useSymbols = it)) }
        
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

        // Min sliders (Advanced)
        Text("Advanced Constraints", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        
        MinCharSlider("Min Numbers", config.minNumbers, 0..10) { update(config.copy(minNumbers = it)) }
        MinCharSlider("Min Symbols", config.minSymbols, 0..10) { update(config.copy(minSymbols = it)) }
        
        OptionSwitch("Avoid Ambiguous (0, O, l, 1)", config.avoidAmbiguous) { update(config.copy(avoidAmbiguous = it)) }
    }
}

@Composable
private fun AdvancedPassphraseOptions(config: GeneratorConfig, update: (GeneratorConfig) -> Unit) {
    SpectreCard {
        Text("Passphrase Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Words", style = MaterialTheme.typography.bodyMedium)
            Text("${config.wordCount}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value         = config.wordCount.toFloat(),
            onValueChange = { update(config.copy(wordCount = it.toInt())) },
            valueRange    = 3f..12f,
            steps         = 8
        )

        OutlinedTextField(
            value = config.separator,
            onValueChange = { update(config.copy(separator = it.take(2))) },
            label = { Text("Separator") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))
        OptionSwitch("Capitalize Words", config.capitalizeWords) { update(config.copy(capitalizeWords = it)) }
        OptionSwitch("Include Number",   config.includeNumber)   { update(config.copy(includeNumber = it)) }
    }
}

@Composable
private fun AdvancedPinOptions(config: GeneratorConfig, update: (GeneratorConfig) -> Unit) {
    SpectreCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Dialpad, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("PIN Configuration", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Length", style = MaterialTheme.typography.bodyMedium)
            Text("${config.length}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value         = config.length.toFloat(),
            onValueChange = { update(config.copy(length = it.toInt())) },
            valueRange    = 3f..32f,
            steps         = 28
        )
    }
}

@Composable
private fun AdvancedUsernameOptions(config: GeneratorConfig, update: (GeneratorConfig) -> Unit) {
    SpectreCard {
        Text("Username Styles", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text("Generates memorable, unique usernames using adjectives and nouns.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdvancedSshOptions(config: GeneratorConfig, update: (GeneratorConfig) -> Unit) {
    SpectreCard {
        Text("SSH Key Type", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Ed25519", "RSA").forEach { type ->
                FilterChip(
                    selected = config.sshKeyType == type,
                    onClick = { update(config.copy(sshKeyType = type)) },
                    label = { Text(type) }
                )
            }
        }
    }
}

@Composable
private fun AdvancedEmailOptions(
    config: GeneratorConfig, 
    emails: List<String>,
    update: (GeneratorConfig) -> Unit
) {
    SpectreCard {
        Text("Email Schema", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        if (emails.isNotEmpty()) {
            Text("Linked Accounts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                emails.forEach { email ->
                    val base = email.substringBefore("@")
                    val domain = email.substringAfter("@")
                    FilterChip(
                        selected = config.emailBase == base && config.emailDomain == domain,
                        onClick = { update(config.copy(emailBase = base, emailDomain = domain)) },
                        label = { Text(email, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = config.emailBase,
            onValueChange = { update(config.copy(emailBase = it)) },
            label = { Text("Base Email") },
            placeholder = { Text("user") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = config.emailDomain,
            onValueChange = { update(config.copy(emailDomain = it)) },
            label = { Text("Domain") },
            placeholder = { Text("gmail.com") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(20.dp))

        Text("Subaddressing Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmailMode.entries.forEach { mode ->
                FilterChip(
                    selected = config.emailType == mode,
                    onClick = { update(config.copy(emailType = mode)) },
                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        SpectreSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MinCharSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$value", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun HistorySection(history: List<String>, onCopy: (String) -> Unit, onClear: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClear) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        }
        
        SpectreCard {
            history.forEachIndexed { index, pw ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onCopy(pw) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pw, 
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (index < history.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                }
            }
        }
    }
}

