package com.spectre.app.feature.watchtower

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.spectre.app.core.data.models.*
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.ui.components.*
import com.spectre.app.core.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class WatchtowerUiState(
    val report: WatchtowerReport? = null,
    val isLoading: Boolean        = true,
    val expandedSection: String?  = null,
)

@HiltViewModel
class WatchtowerViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val session: VaultSession,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchtowerUiState())
    val state: StateFlow<WatchtowerUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val accountId = session.activeAccountId ?: return@launch
            val report    = vaultRepository.buildWatchtowerReport(accountId)
            _state.update { it.copy(report = report, isLoading = false) }
        }
    }

    fun toggleSection(key: String) {
        _state.update { it.copy(expandedSection = if (it.expandedSection == key) null else key) }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun WatchtowerScreen(
    onItemClick: (String) -> Unit,
    vm: WatchtowerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(containerColor = Color.Transparent) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SpectreTopBar(title = "Watchtower", subtitle = "Vault health report") }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Analysing your vault…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                return@LazyColumn
            }

            val report = state.report ?: return@LazyColumn

            // ── Score ring ────────────────────────────────────────────────────
            item {
                SpectreCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Security Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            val (label, color) = when {
                                report.totalScore >= 90 -> "Excellent" to StrengthStrong
                                report.totalScore >= 70 -> "Good"      to StrengthGood
                                report.totalScore >= 50 -> "Fair"      to StrengthFair
                                else                    -> "At Risk"   to StrengthWeak
                            }
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text  = buildString {
                                    if (report.weakPasswords.isNotEmpty()) append("${report.weakPasswords.size} weak password${if (report.weakPasswords.size > 1) "s" else ""}. ")
                                    if (report.reusedPasswords.isNotEmpty()) append("${report.reusedPasswords.size} reused. ")
                                    if (report.exposedPasswords.isNotEmpty()) append("${report.exposedPasswords.size} breached.")
                                    if (isEmpty()) append("Looking good! No critical issues found.")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        WatchtowerScoreRing(score = report.totalScore)
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick  = vm::load,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh report")
                    }
                }
            }

            // ── Issue sections ────────────────────────────────────────────────
            val sections = listOf(
                WatchtowerSection("weak",     "Weak Passwords",    Icons.Filled.LockOpen,    DangerRed,    report.weakPasswords),
                WatchtowerSection("reused",   "Reused Passwords",  Icons.Filled.ContentCopy, WarningAmber, report.reusedPasswords),
                WatchtowerSection("exposed",  "Breached Accounts", Icons.Filled.GppBad,      DangerRed,    report.exposedPasswords),
                WatchtowerSection("old",      "Old Passwords",     Icons.Filled.History,     WarningAmber, report.oldPasswords),
                WatchtowerSection("totp",     "No 2FA Enabled",    Icons.Filled.PhoneAndroid,InfoBlue,     report.noTotp),
                WatchtowerSection("insecure", "Insecure URLs",     Icons.Filled.Http, WarningAmber, report.insecureUrls),
            )

            items(sections) { section ->
                WatchtowerSectionCard(
                    section   = section,
                    expanded  = state.expandedSection == section.key,
                    onToggle  = { vm.toggleSection(section.key) },
                    onItemClick = onItemClick,
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class WatchtowerSection(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val items: List<DecryptedCipher>,
)

@Composable
private fun WatchtowerSectionCard(
    section: WatchtowerSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    val hasIssues = section.items.isNotEmpty()
    val statusColor = if (hasIssues) section.color else SafeGreen

    SpectreCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { if (hasIssues) onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (hasIssues) section.icon else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text  = if (hasIssues) "${section.items.size} item${if (section.items.size > 1) "s" else ""}" else "All clear",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }
            if (hasIssues) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = expanded && hasIssues) {
            Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                section.items.take(10).forEach { cipher ->
                    CipherListItem(
                        cipher  = cipher,
                        onClick = { onItemClick(cipher.id) },
                        compact = true,
                        modifier = Modifier.padding(horizontal = 0.dp),
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
                if (section.items.size > 10) {
                    Text(
                        text     = "+ ${section.items.size - 10} more",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
