package com.spectre.app.feature.watchtower

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.scale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.models.*
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.ui.components.*
import com.spectre.app.core.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.time.YearMonth
import javax.inject.Inject

import com.spectre.app.core.data.datastore.SpectrePreferences
import com.spectre.app.di.IoDispatcher


/**
 * Detailed report of security findings in the user's vault.
 */
data class ExtendedWatchtowerReport(
    val weakPasswords: List<DecryptedCipher>      = emptyList(),
    val reusedPasswords: List<DecryptedCipher>    = emptyList(),
    val exposedPasswords: List<DecryptedCipher>   = emptyList(),   // HIBP checked
    val oldPasswords: List<DecryptedCipher>        = emptyList(),   // > 365 days
    val veryOldPasswords: List<DecryptedCipher>    = emptyList(),   // > 2 years
    val noTotp: List<DecryptedCipher>              = emptyList(),
    val insecureUris: List<DecryptedCipher>        = emptyList(),
    val duplicateItems: List<DecryptedCipher>      = emptyList(),   // same user+domain
    val expiredCards: List<DecryptedCipher>        = emptyList(),   // card past expiry
    val incompleteItems: List<DecryptedCipher>     = emptyList(),   // no password or no uri
    val inactive2fa: List<DecryptedCipher>         = emptyList(),   // sites that support 2FA but it's missing
    val totalScore: Int                            = 100,
    val hibpChecked: Boolean                       = false,
    val hibpError: String?                         = null,
)

data class WatchtowerUiState(
    val report: ExtendedWatchtowerReport? = null,
    val isLoading: Boolean                = true,
    val isCheckingHibp: Boolean           = false,
    val expandedSection: String?          = null,
    val ignoredCount: Int                 = 0,
)


/**
 * ViewModel for the Watchtower screen, handling vault analysis and breach checks.
 */
@HiltViewModel
class WatchtowerViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val session: VaultSession,
    private val prefs: SpectrePreferences,
    private val ignoredDao: com.spectre.app.core.data.database.dao.IgnoredWatchtowerDao,
    private val watchtowerUseCase: com.spectre.app.core.domain.WatchtowerUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchtowerUiState())
    val state: StateFlow<WatchtowerUiState> = _state.asStateFlow()

    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.spectre.app.core.data.datastore.SpectreSettings())

    private val httpClient = OkHttpClient()

    init {
        viewModelScope.launch {
            val accountId = session.activeAccountId ?: return@launch
            
            combine(
                settings,
                vaultRepository.observeAllCiphers(),
                ignoredDao.observeAll(accountId)
            ) { currentSettings, allCiphers, ignoredItems ->
                val report = watchtowerUseCase.analyze(currentSettings, allCiphers, ignoredItems)
                _state.update { 
                    it.copy(
                        report = report,
                        isLoading = false,
                        ignoredCount = ignoredItems.size
                    )
                }
            }.collect()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val accountId = session.activeAccountId ?: return@launch
            val allCiphers = vaultRepository.getAllDecryptedCiphers(accountId)
            val ignoredItems = ignoredDao.getAll(accountId)
            val report = watchtowerUseCase.analyze(settings.value, allCiphers, ignoredItems)
            _state.update { 
                it.copy(
                    report = report,
                    isLoading = false,
                    ignoredCount = ignoredItems.size
                )
            }
        }
    }
    
    fun performHibpCheck() {
        viewModelScope.launch {
            val accountId = session.activeAccountId ?: return@launch
            val allCiphers = vaultRepository.getAllDecryptedCiphers(accountId)
            val logins = allCiphers.filter { it.type == CipherType.LOGIN && !it.isInTrash }
            checkHibp(logins)
        }
    }

    /**
     * HIBP k-anonymity API — sends only first 5 chars of SHA-1 hash.
     * Full password never leaves the device.
     */
    fun checkHibp(logins: List<DecryptedCipher>) {
        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isCheckingHibp = true) }

            val breached = mutableListOf<DecryptedCipher>()
            var error: String? = null

            for (cipher in logins) {
                val password = cipher.loginData?.password?.takeIf { it.isNotBlank() } ?: continue
                try {
                    val sha1     = sha1Hex(password)
                    val prefix   = sha1.take(5)
                    val suffix   = sha1.drop(5).uppercase()

                    val request  = Request.Builder()
                        .url("https://api.pwnedpasswords.com/range/$prefix")
                        .header("Add-Padding", "true")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body.string()
                            val found = body.lines().any { line ->
                                val parts = line.split(":")
                                parts.firstOrNull()?.trim()?.uppercase() == suffix &&
                                        (parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L) > 0L
                            }
                            if (found) breached.add(cipher)
                        }
                    }
                    delay(50) // gentle rate limiting
                } catch (e: Exception) {
                    error = "HIBP check interrupted: ${e.message}"
                    break
                }
            }

            _state.update { s ->
                s.copy(
                    isCheckingHibp = false,
                    report = s.report?.copy(
                        exposedPasswords = breached,
                        hibpChecked      = true,
                        hibpError        = error,
                    )
                )
            }
        }
    }

    fun toggleSection(key: String) {
        _state.update { it.copy(expandedSection = if (it.expandedSection == key) null else key) }
    }

    fun setHibpCheck(v: Boolean) = viewModelScope.launch { prefs.setWtHibp(v) }
    fun setReusedScan(v: Boolean) = viewModelScope.launch { prefs.setWtReused(v) }
    fun setWeakScan(v: Boolean) = viewModelScope.launch { prefs.setWtWeak(v) }
    fun set2faScan(v: Boolean) = viewModelScope.launch { prefs.setWt2fa(v) }

    fun ignoreItem(cipherId: String, issueType: String) {
        viewModelScope.launch {
            val accountId = session.activeAccountId ?: return@launch
            ignoredDao.insert(com.spectre.app.core.data.database.entities.IgnoredWatchtowerItemEntity(
                id = "${cipherId}_$issueType",
                accountId = accountId,
                cipherId = cipherId,
                issueType = issueType
            ))
        }
    }

    // Helpers for HIBP
    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes  = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02X".format(it) }
    }
}


/**
 * Main Watchtower screen for vault health and security audits.
 */
@Composable
fun WatchtowerScreen(
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: WatchtowerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier       = modifier,
        containerColor = Color.Transparent,
        topBar = {
            SpectreTopBar(title = "Watchtower", subtitle = "Vault health analysis")
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Analysing vault…", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                return@LazyColumn
            }

            val report = state.report ?: return@LazyColumn

            // Score card
            item {
                SpectreCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Security Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            val (label, color) = when {
                                report.totalScore >= 90 -> "Excellent" to StrengthStrong
                                report.totalScore >= 70 -> "Good"      to StrengthGood
                                report.totalScore >= 50 -> "Fair"      to StrengthFair
                                else                    -> "At Risk"   to StrengthWeak
                            }
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            val totalIssues = report.weakPasswords.size + report.reusedPasswords.size +
                                    report.exposedPasswords.size + report.expiredCards.size
                            Text(
                                if (totalIssues == 0) "No critical issues found."
                                else "$totalIssues issue${if (totalIssues > 1) "s" else ""} need attention.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        WatchtowerScoreRing(score = report.totalScore)
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick  = { vm.refresh() },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick  = { vm.performHibpCheck() },
                            enabled  = !state.isCheckingHibp,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape    = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            if (state.isCheckingHibp) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Filled.GppBad, null, Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Check Breaches", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    report.hibpError?.let { err ->
                        Text("⚠ $err", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (report.hibpChecked && !state.isCheckingHibp && report.hibpError == null) {
                        Text("✓ HIBP breach check complete", style = MaterialTheme.typography.labelSmall,
                            color = StrengthStrong, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // Configuration
            item {
                val settings by vm.settings.collectAsStateWithLifecycle()
                SpectreCard {
                    Text("Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    val configItems = listOf(
                        "Check breached sites (HIBP)" to settings.watchtowerCheckHibp to vm::setHibpCheck,
                        "Scan for reused passwords" to settings.watchtowerScanReused to vm::setReusedScan,
                        "Scan for weak passwords" to settings.watchtowerScanWeak to vm::setWeakScan,
                        "Scan for inactive 2FA" to settings.watchtowerScan2fa to vm::set2faScan
                    )

                    configItems.forEach { (pair, setter) ->
                        val (title, checked) = pair
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(title, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = checked, 
                                onCheckedChange = { setter(it) },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                    
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp).clickable { /* TODO: Open ignored */ },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Ignored Items", style = MaterialTheme.typography.bodyMedium)
                            Text("${state.ignoredCount} hidden", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Issue sections
            val sections = listOf(
                WSection("exposed",    "Breached Passwords",    Icons.Filled.GppBad,          DangerRed,    report.exposedPasswords,  "Passwords found in data breaches (HIBP)"),
                WSection("weak",       "Weak Passwords",        Icons.Filled.LockOpen,        DangerRed,    report.weakPasswords,     "Easy to guess or brute-force"),
                WSection("reused",     "Reused Passwords",      Icons.Filled.ContentCopy,     WarningAmber, report.reusedPasswords,   "Same password used across multiple sites"),
                WSection("expired",    "Expired Cards",         Icons.Filled.CreditCardOff,   DangerRed,    report.expiredCards,      "Payment cards past their expiry date"),
                WSection("old",        "Old Passwords",         Icons.Filled.History,         WarningAmber, report.oldPasswords,      "Not changed in over a year"),
                WSection("veryold",    "Very Old Passwords",    Icons.Filled.HourglassBottom, DangerRed,    report.veryOldPasswords,  "Not changed in over 2 years"),
                WSection("duplicate",  "Duplicate Items",       Icons.Filled.FileCopy,        WarningAmber, report.duplicateItems,    "Same username and domain in multiple entries"),
                WSection("insecure",   "Insecure URLs",         Icons.Filled.Http,     WarningAmber, report.insecureUris,      "Login pages using plain HTTP"),
                WSection("inactive_2fa", "Inactive 2FA",        Icons.Filled.Security,        InfoBlue,     report.inactive2fa,       "High-profile accounts without 2FA"),
                WSection("totp",       "No 2FA Enabled",        Icons.Filled.PhoneAndroid,    InfoBlue,     report.noTotp,            "No authenticator code configured"),
                WSection("incomplete", "Incomplete Items",      Icons.Filled.EditNote,        InfoBlue,     report.incompleteItems,   "Missing password or website URL"),
            )

            items(sections) { section ->
                WSectionCard(
                    s          = section,
                    expanded   = state.expandedSection == section.key,
                    onToggle   = { vm.toggleSection(section.key) },
                    onItemClick = onItemClick,
                    onIgnore   = { cipherId -> vm.ignoreItem(cipherId, section.key) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class WSection(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val items: List<DecryptedCipher>,
    val description: String,
)

@Composable
private fun WSectionCard(
    s: WSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onItemClick: (String) -> Unit,
    onIgnore: (String) -> Unit = {},
) {
    val hasIssues   = s.items.isNotEmpty()
    val statusColor = if (hasIssues) s.color else StrengthStrong

    SpectreCard {
        Row(
            Modifier.fillMaxWidth()
                .clickable(enabled = hasIssues) { onToggle() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (hasIssues) s.icon else Icons.Filled.CheckCircle,
                    null, tint = statusColor, modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (hasIssues) "${s.items.size} item${if (s.items.size > 1) "s" else ""} — ${s.description}"
                    else s.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasIssues) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (hasIssues) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = expanded && hasIssues) {
            Column {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                s.items.take(10).forEach { cipher ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            CipherListItem(
                                cipher  = cipher,
                                onClick = { onItemClick(cipher.id) },
                                compact = true,
                            )
                        }
                        IconButton(
                            onClick = { onIgnore(cipher.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.VisibilityOff,
                                contentDescription = "Ignore this finding",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                }
                if (s.items.size > 10) {
                    Text(
                        "+ ${s.items.size - 10} more",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
