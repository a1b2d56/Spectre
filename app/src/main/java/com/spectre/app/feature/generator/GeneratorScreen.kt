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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.models.PasswordStrength
import com.spectre.app.core.ui.components.*
import com.spectre.app.core.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class GeneratorUiState(
    val result: GeneratedResult? = null,
    val config: GeneratorConfig  = GeneratorConfig(),
    val history: List<String>    = emptyList(),
)

@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val generator: PasswordGenerator,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorUiState())
    val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    init { regenerate() }

    fun regenerate() {
        val result = generator.generate(_state.value.config)
        _state.update { it.copy(result = result) }
    }

    fun updateConfig(config: GeneratorConfig) {
        _state.update { it.copy(config = config) }
        regenerate()
    }

    fun copyToClipboard() {
        val value = _state.value.result?.value ?: return
        val cm    = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Generated password", value))
        // Add to history (last 20)
        _state.update { s ->
            s.copy(history = (listOf(value) + s.history).take(20))
        }
        viewModelScope.launch { _snackbar.emit("Copied to clipboard — clears in 30s") }
        viewModelScope.launch {
            kotlinx.coroutines.delay(30_000)
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun GeneratorScreen(vm: GeneratorViewModel = hiltViewModel()) {
    val state   by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.snackbar.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        snackbarHost  = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpectreTopBar(title = "Generator", subtitle = "Create strong passwords")

            // ── Output card ───────────────────────────────────────────────────
            GeneratedOutputCard(
                result      = state.result,
                onRegenerate = vm::regenerate,
                onCopy      = vm::copyToClipboard,
            )

            // ── Mode tabs ─────────────────────────────────────────────────────
            val config = state.config
            ModeTabs(
                mode     = config.mode,
                onChange = { vm.updateConfig(config.copy(mode = it)) },
            )

            // ── Options ───────────────────────────────────────────────────────
            when (config.mode) {
                GeneratorMode.PASSWORD   -> PasswordOptions(config, vm::updateConfig)
                GeneratorMode.PASSPHRASE -> PassphraseOptions(config, vm::updateConfig)
                GeneratorMode.USERNAME   -> UsernameOptions()
            }

            // ── History ───────────────────────────────────────────────────────
            if (state.history.isNotEmpty()) {
                HistoryCard(history = state.history, onSelect = { /* copy selected */ })
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GeneratedOutputCard(
    result: GeneratedResult?,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
) {
    val strengthColor = when (result?.strength) {
        0    -> StrengthWeak
        1    -> StrengthWeak
        2    -> StrengthFair
        3    -> StrengthGood
        4    -> StrengthStrong
        else -> MaterialTheme.colorScheme.outline
    }

    SpectreCard {
        Text(
            text      = result?.value ?: "—",
            style     = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 20.dp),
            letterSpacing = if (result?.config?.mode == GeneratorMode.PASSWORD) 1.sp else 0.sp,
        )

        Spacer(Modifier.height(12.dp))

        // Strength bar
        result?.let { r ->
            val strengthLabel = when (r.strength) {
                0 -> "Very Weak"; 1 -> "Weak"; 2 -> "Fair"; 3 -> "Strong"; else -> "Very Strong"
            }
            val frac = (r.strength + 1) / 5f
            val animFrac by animateFloatAsState(frac, tween(500), label = "str")
            LinearProgressIndicator(
                progress  = { animFrac },
                modifier  = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color     = strengthColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(strengthLabel, style = MaterialTheme.typography.labelSmall, color = strengthColor)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick  = onRegenerate,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Regenerate")
            }
            Button(
                onClick  = onCopy,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        }
    }
}

@Composable
private fun ModeTabs(mode: GeneratorMode, onChange: (GeneratorMode) -> Unit) {
    val modes = listOf(GeneratorMode.PASSWORD to "Password", GeneratorMode.PASSPHRASE to "Passphrase", GeneratorMode.USERNAME to "Username")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (m, label) ->
            SegmentedButton(
                selected = mode == m,
                onClick  = { onChange(m) },
                shape    = SegmentedButtonDefaults.itemShape(index, modes.size),
                label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun PasswordOptions(config: GeneratorConfig, update: (GeneratorConfig) -> Unit) {
    SpectreCard {
        SectionHeader(title = "Password Options", modifier = Modifier.padding(0.dp))
        Spacer(Modifier.height(8.dp))

        // Length slider
        Text("Length: ${config.length}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value         = config.length.toFloat(),
            onValueChange = { update(config.copy(length = it.toInt())) },
            valueRange    = 8f..128f,
            steps         = 119,
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(Modifier.height(8.dp))

        // Character type toggles
        OptionToggleRow("Uppercase (A-Z)", config.useUppercase) { update(config.copy(useUppercase = it)) }
        OptionToggleRow("Lowercase (a-z)", config.useLowercase) { update(config.copy(useLowercase = it)) }
        OptionToggleRow("Numbers (0-9)",   config.useNumbers)   { update(config.copy(useNumbers = it)) }
        OptionToggleRow("Symbols (!@#…)",  config.useSymbols)   { update(config.copy(useSymbols = it)) }
        OptionToggleRow("Avoid ambiguous characters", config.avoidAmbiguous) { update(config.copy(avoidAmbiguous = it)) }
    }
}

@Composable
private fun PassphraseOptions(config: GeneratorConfig, update: (GeneratorConfig) -> Unit) {
    SpectreCard {
        SectionHeader(title = "Passphrase Options", modifier = Modifier.padding(0.dp))
        Spacer(Modifier.height(8.dp))
        Text("Words: ${config.wordCount}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value         = config.wordCount.toFloat(),
            onValueChange = { update(config.copy(wordCount = it.toInt())) },
            valueRange    = 3f..10f,
            steps         = 6,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = config.separator,
            onValueChange = { update(config.copy(separator = it.take(3))) },
            label         = { Text("Word separator") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        OptionToggleRow("Capitalise words", config.capitalizeWords) { update(config.copy(capitalizeWords = it)) }
        OptionToggleRow("Include number",   config.includeNumber)   { update(config.copy(includeNumber = it)) }
    }
}

@Composable
private fun UsernameOptions() {
    SpectreCard {
        SectionHeader(title = "Username Options", modifier = Modifier.padding(0.dp))
        Spacer(Modifier.height(8.dp))
        Text("Generates a random adjective+noun+number username.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun HistoryCard(history: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SpectreCard {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = "History", count = history.size, modifier = Modifier.padding(0.dp))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                history.forEach { pw ->
                    Text(
                        text     = pw,
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(pw) }.padding(vertical = 6.dp),
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}
