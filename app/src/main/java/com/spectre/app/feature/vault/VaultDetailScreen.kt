package com.spectre.app.feature.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.crypto.TotpEngine
import com.spectre.app.core.data.models.*
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.ui.components.*
import com.spectre.app.core.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val totpEngine: TotpEngine,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val cipherId = savedStateHandle.get<String>("cipherId") ?: ""

    val cipher: StateFlow<DecryptedCipher?> = vaultRepository
        .observeById(cipherId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _totp = MutableStateFlow<com.spectre.app.core.crypto.TotpCode?>(null)
    val totp: StateFlow<com.spectre.app.core.crypto.TotpCode?> = _totp.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var totpJob: Job? = null

    init {
        viewModelScope.launch {
            cipher.collect { c ->
                val uri = c?.loginData?.totp
                if (!uri.isNullOrBlank()) startTotpRefresh(uri) else totpJob?.cancel()
            }
        }
    }

    private fun startTotpRefresh(uri: String) {
        totpJob?.cancel()
        totpJob = viewModelScope.launch {
            while (isActive) {
                _totp.value = totpEngine.generateFromUri(uri)
                delay(1_000)
            }
        }
    }

    fun copyToClipboard(label: String, value: String, clearAfter: Int = 30) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        viewModelScope.launch {
            _snackbar.emit("$label copied — clears in ${clearAfter}s")
            delay(clearAfter * 1_000L)
            // Clear clipboard
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { vaultRepository.toggleFavorite(cipherId) }
    }

    fun softDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.deleteCipher(cipherId)
            onDeleted()
        }
    }

    fun permanentlyDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.permanentlyDeleteCipher(cipherId)
            onDeleted()
        }
    }

    fun restore() {
        viewModelScope.launch { vaultRepository.restoreCipher(cipherId) }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun VaultDetailScreen(
    cipherId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    vm: VaultDetailViewModel = hiltViewModel(),
) {
    val cipher  by vm.cipher.collectAsState()
    val totp    by vm.totp.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.snackbar.collect { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    Scaffold(
        topBar = {
            DetailTopBar(
                cipher    = cipher,
                onBack    = onBack,
                onEdit    = onEdit,
                onFavourite = { vm.toggleFavorite() },
                onDelete  = { vm.softDelete(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { padding ->
        val c = cipher
        if (c == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Header card ───────────────────────────────────────────────────
            ItemHeaderCard(cipher = c)

            // ── Trash banner ──────────────────────────────────────────────────
            if (c.isInTrash) {
                Surface(
                    color  = MaterialTheme.colorScheme.errorContainer,
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("In Trash", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Row {
                            TextButton(onClick = { vm.restore() }) { Text("Restore") }
                            TextButton(onClick = { vm.permanentlyDelete(onBack) }) {
                                Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // ── TOTP card ─────────────────────────────────────────────────────
            totp?.let { t ->
                SpectreCard {
                    SectionHeader(title = "Authenticator code", modifier = Modifier.padding(0.dp))
                    Spacer(Modifier.height(8.dp))
                    TotpCountdownChip(
                        code             = t.code,
                        remainingSeconds = t.remainingSeconds,
                        period           = t.period,
                        onClick          = { vm.copyToClipboard("TOTP Code", t.code, 10) },
                        modifier         = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Type-specific fields ──────────────────────────────────────────
            when (c.type) {
                CipherType.LOGIN    -> LoginDetailCard(c, totp, vm::copyToClipboard)
                CipherType.CARD     -> CardDetailCard(c, vm::copyToClipboard)
                CipherType.IDENTITY -> IdentityDetailCard(c, vm::copyToClipboard)
                CipherType.SECURE_NOTE -> Unit
            }

            // ── Notes ─────────────────────────────────────────────────────────
            c.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                SpectreCard {
                    SectionHeader(title = "Notes", modifier = Modifier.padding(0.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ── Custom fields ─────────────────────────────────────────────────
            if (c.fields.isNotEmpty()) {
                SpectreCard {
                    SectionHeader(title = "Custom Fields", modifier = Modifier.padding(0.dp))
                    c.fields.forEach { field ->
                        field.name?.let { name ->
                            FieldCopyRow(
                                label     = name,
                                value     = field.value ?: "",
                                onCopy    = { vm.copyToClipboard(name, field.value ?: "") },
                                sensitive = field.type == 1,
                            )
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }

            // ── Password history ──────────────────────────────────────────────
            if (c.passwordHistory.isNotEmpty()) {
                PasswordHistoryCard(history = c.passwordHistory, onCopy = { vm.copyToClipboard("Password", it) })
            }

            // ── Attachments ───────────────────────────────────────────────────
            if (c.attachments.isNotEmpty()) {
                SpectreCard {
                    SectionHeader(title = "Attachments (${c.attachments.size})", modifier = Modifier.padding(0.dp))
                    c.attachments.forEach { att ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.AttachFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(att.fileName ?: "Attachment", style = MaterialTheme.typography.bodyMedium)
                                att.size?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            IconButton(onClick = { /* download */ }) {
                                Icon(Icons.Filled.Download, "Download")
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }

            // ── Metadata ──────────────────────────────────────────────────────
            MetadataCard(cipher = c)

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(
    cipher: DecryptedCipher?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onFavourite: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(cipher?.name ?: "", maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
        },
        actions = {
            cipher?.let { c ->
                IconButton(onClick = onFavourite) {
                    Icon(
                        if (c.favorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                        "Favourite",
                        tint = if (c.favorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Move to Trash") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ItemHeaderCard(cipher: DecryptedCipher) {
    val (icon, tint) = when (cipher.type) {
        CipherType.LOGIN       -> Icons.Filled.Key to MaterialTheme.colorScheme.primary
        CipherType.SECURE_NOTE -> Icons.Filled.StickyNote2 to MaterialTheme.colorScheme.secondary
        CipherType.CARD        -> Icons.Filled.CreditCard to Color(0xFF22D3EE)
        CipherType.IDENTITY    -> Icons.Filled.Person to Color(0xFF4ADE80)
    }
    SpectreCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(cipher.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                cipher.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        cipher.passwordStrength?.let { strength ->
            Spacer(Modifier.height(12.dp))
            PasswordStrengthBar(strength = strength)
        }
    }
}

@Composable
private fun LoginDetailCard(
    cipher: DecryptedCipher,
    totp: com.spectre.app.core.crypto.TotpCode?,
    onCopy: (String, String) -> Unit,
) {
    val login = cipher.loginData ?: return
    SpectreCard {
        SectionHeader(title = "Login", modifier = Modifier.padding(0.dp))
        login.username?.takeIf { it.isNotBlank() }?.let { u ->
            FieldCopyRow("Username", u, { onCopy("Username", u) })
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
        login.password?.takeIf { it.isNotBlank() }?.let { p ->
            FieldCopyRow("Password", p, { onCopy("Password", p) }, sensitive = true)
        }
        if (login.uris.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            SectionHeader(title = "URLs", modifier = Modifier.padding(top = 8.dp, bottom = 0.dp, start = 0.dp, end = 0.dp))
            login.uris.forEach { uri ->
                uri.uri?.let { u ->
                    FieldCopyRow("URI", u, { onCopy("URI", u) })
                }
            }
        }
    }
}

@Composable
private fun CardDetailCard(cipher: DecryptedCipher, onCopy: (String, String) -> Unit) {
    val card = cipher.cardData ?: return
    SpectreCard {
        SectionHeader(title = "Card Details", modifier = Modifier.padding(0.dp))
        card.cardholderName?.let { FieldCopyRow("Cardholder Name", it, { onCopy("Cardholder Name", it) }) }
        card.brand?.let { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Brand", it, { onCopy("Brand", it) }) }
        card.number?.let { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Number", it, { onCopy("Card Number", it) }, sensitive = true) }
        val expiry = listOfNotNull(card.expMonth, card.expYear).joinToString("/")
        if (expiry.isNotBlank()) { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Expiry", expiry, { onCopy("Expiry", expiry) }) }
        card.code?.let { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Security Code", it, { onCopy("CVV", it) }, sensitive = true) }
    }
}

@Composable
private fun IdentityDetailCard(cipher: DecryptedCipher, onCopy: (String, String) -> Unit) {
    val id = cipher.identityData ?: return
    SpectreCard {
        SectionHeader(title = "Identity", modifier = Modifier.padding(0.dp))
        val fullName = listOfNotNull(id.firstName, id.middleName, id.lastName).joinToString(" ")
        if (fullName.isNotBlank()) FieldCopyRow("Full Name", fullName, { onCopy("Name", fullName) })
        id.email?.let { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Email", it, { onCopy("Email", it) }) }
        id.phone?.let { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Phone", it, { onCopy("Phone", it) }) }
        id.company?.let { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Company", it, { onCopy("Company", it) }) }
        val address = listOfNotNull(id.address1, id.city, id.state, id.postalCode, id.country).joinToString(", ")
        if (address.isNotBlank()) { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Address", address, { onCopy("Address", address) }) }
        id.passportNumber?.let { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); FieldCopyRow("Passport No.", it, { onCopy("Passport", it) }, sensitive = true) }
    }
}

@Composable
private fun PasswordHistoryCard(history: List<PasswordHistoryEntry>, onCopy: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SpectreCard {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = "Password History", count = history.size, modifier = Modifier.padding(0.dp))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                history.take(10).forEach { entry ->
                    FieldCopyRow(
                        label     = entry.lastUsedDate.take(10),
                        value     = entry.password,
                        onCopy    = { onCopy(entry.password) },
                        sensitive = true,
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
private fun MetadataCard(cipher: DecryptedCipher) {
    SpectreCard {
        SectionHeader(title = "Metadata", modifier = Modifier.padding(0.dp))
        Spacer(Modifier.height(8.dp))
        MetaRow("Item ID", cipher.id.take(8) + "…")
        cipher.creationDate?.let { MetaRow("Created", it.take(10)) }
        MetaRow("Last modified", cipher.revisionDate.take(10))
        cipher.organizationId?.let { MetaRow("Organisation", it.take(8) + "…") }
        cipher.folderId?.let { MetaRow("Folder ID", it.take(8) + "…") }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
