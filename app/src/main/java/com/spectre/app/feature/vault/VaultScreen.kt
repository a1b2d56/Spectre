package com.spectre.app.feature.vault

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.models.*
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

enum class VaultFilter { ALL, LOGINS, CARDS, IDENTITIES, NOTES, FAVOURITES, TRASH }

data class VaultUiState(
    val ciphers: List<DecryptedCipher>  = emptyList(),
    val folders: List<DecryptedFolder>  = emptyList(),
    val query: String                   = "",
    val filter: VaultFilter             = VaultFilter.ALL,
    val isLoading: Boolean              = false,
    val isSyncing: Boolean              = false,
    val syncError: String?              = null,
    val lastSync: Long                  = 0L,
    val selectedFolderId: String?       = null,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val session: VaultSession,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState(isLoading = true))
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init { observeVault() }

    private fun observeVault() {
        viewModelScope.launch {
            combine(
                vaultRepository.observeAllCiphers(),
                vaultRepository.observeFolders(),
            ) { ciphers, folders ->
                _state.update { it.copy(ciphers = ciphers, folders = folders, isLoading = false) }
            }.collect()
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) { observeVault(); return }
        searchJob = viewModelScope.launch {
            delay(200) // debounce
            vaultRepository.search(q).collect { results ->
                _state.update { it.copy(ciphers = results) }
            }
        }
    }

    fun onFilterChange(f: VaultFilter) {
        _state.update { it.copy(filter = f, selectedFolderId = null) }
        viewModelScope.launch {
            when (f) {
                VaultFilter.ALL        -> vaultRepository.observeAllCiphers()
                VaultFilter.LOGINS     -> vaultRepository.observeByType(CipherType.LOGIN)
                VaultFilter.CARDS      -> vaultRepository.observeByType(CipherType.CARD)
                VaultFilter.IDENTITIES -> vaultRepository.observeByType(CipherType.IDENTITY)
                VaultFilter.NOTES      -> vaultRepository.observeByType(CipherType.SECURE_NOTE)
                VaultFilter.FAVOURITES -> vaultRepository.observeFavorites()
                VaultFilter.TRASH      -> vaultRepository.observeTrash()
            }.collect { list -> _state.update { it.copy(ciphers = list) } }
        }
    }

    fun onFolderSelect(folderId: String?) {
        _state.update { it.copy(selectedFolderId = folderId) }
        viewModelScope.launch {
            val flow = if (folderId != null)
                vaultRepository.observeFolder(folderId)
            else
                vaultRepository.observeAllCiphers()
            flow.collect { list -> _state.update { it.copy(ciphers = list) } }
        }
    }

    fun sync() {
        val accountId = session.activeAccountId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncError = null) }
            val result = vaultRepository.sync(accountId)
            _state.update {
                it.copy(
                    isSyncing = false,
                    syncError = (result.exceptionOrNull()?.message),
                    lastSync  = System.currentTimeMillis(),
                )
            }
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { vaultRepository.toggleFavorite(id) }
    }

    fun softDelete(id: String) {
        viewModelScope.launch { vaultRepository.deleteCipher(id) }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun VaultScreen(
    onItemClick: (String) -> Unit,
    onAddClick: (type: Int) -> Unit,
    vm: VaultViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showAddMenu  by remember { mutableStateOf(false) }
    var showFilters  by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            VaultTopBar(
                query       = state.query,
                isSyncing   = state.isSyncing,
                onQueryChange = vm::onQueryChange,
                onSync      = vm::sync,
                onFilterToggle = { showFilters = !showFilters },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AddCipherFab(
                expanded  = showAddMenu,
                onToggle  = { showAddMenu = !showAddMenu },
                onAdd     = { type -> showAddMenu = false; onAddClick(type) },
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Filter chips
            AnimatedVisibility(visible = showFilters) {
                FilterChipRow(
                    current  = state.filter,
                    onChange = vm::onFilterChange,
                )
            }

            // Folder chips
            if (state.folders.isNotEmpty() && state.filter == VaultFilter.ALL) {
                FolderChipRow(
                    folders  = state.folders,
                    selected = state.selectedFolderId,
                    onSelect = vm::onFolderSelect,
                )
            }

            // Error banner
            state.syncError?.let { err ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color    = MaterialTheme.colorScheme.errorContainer,
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sync failed: $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.ciphers.isEmpty() -> EmptyState(
                    icon     = Icons.Outlined.Lock,
                    title    = "No items",
                    subtitle = if (state.query.isNotBlank()) "No results for \"${state.query}\"" else "Tap + to add your first item",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> CipherList(
                    ciphers       = state.ciphers,
                    onItemClick   = onItemClick,
                    onFavorite    = vm::toggleFavorite,
                    onDelete      = vm::softDelete,
                )
            }
        }
    }
}

@Composable
private fun VaultTopBar(
    query: String,
    isSyncing: Boolean,
    onQueryChange: (String) -> Unit,
    onSync: () -> Unit,
    onFilterToggle: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var searchActive by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (searchActive) {
                OutlinedTextField(
                    value         = query,
                    onValueChange = onQueryChange,
                    placeholder   = { Text("Search vault…") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                )
            } else {
                Text("Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        actions = {
            if (!searchActive) {
                IconButton(onClick = { searchActive = true }) {
                    Icon(Icons.Filled.Search, "Search")
                }
            } else {
                IconButton(onClick = { searchActive = false; onQueryChange("") }) {
                    Icon(Icons.Filled.Close, "Close search")
                }
            }
            IconButton(onClick = onFilterToggle) {
                Icon(Icons.Filled.FilterList, "Filters")
            }
            IconButton(onClick = onSync, enabled = !isSyncing) {
                if (isSyncing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Sync, "Sync")
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors         = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun FilterChipRow(current: VaultFilter, onChange: (VaultFilter) -> Unit) {
    val filters = listOf(
        VaultFilter.ALL        to "All",
        VaultFilter.LOGINS     to "Logins",
        VaultFilter.CARDS      to "Cards",
        VaultFilter.IDENTITIES to "Identities",
        VaultFilter.NOTES      to "Notes",
        VaultFilter.FAVOURITES to "Favourites",
        VaultFilter.TRASH      to "Trash",
    )
    LazyRow(
        contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { (filter, label) ->
            FilterChip(
                selected = current == filter,
                onClick  = { onChange(filter) },
                label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun FolderChipRow(
    folders: List<DecryptedFolder>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick  = { onSelect(null) },
                label    = { Text("All folders", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, null, Modifier.size(14.dp)) },
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected = selected == folder.id,
                onClick  = { onSelect(folder.id) },
                label    = { Text(folder.name, style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(14.dp)) },
            )
        }
    }
}

@Composable
private fun CipherList(
    ciphers: List<DecryptedCipher>,
    onItemClick: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(ciphers, key = { it.id }) { cipher ->
            CipherListItem(
                cipher     = cipher,
                onClick    = { onItemClick(cipher.id) },
                onFavorite = { onFavorite(cipher.id) },
            )
            HorizontalDivider(
                modifier  = Modifier.padding(start = 70.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
private fun AddCipherFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: (Int) -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue  = if (expanded) 45f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label        = "fabRotation",
    )

    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit    = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple(1, "Login",    Icons.Filled.Key),
                    Triple(3, "Card",     Icons.Filled.CreditCard),
                    Triple(4, "Identity", Icons.Filled.Person),
                    Triple(2, "Secure Note", Icons.Filled.StickyNote2),
                ).forEach { (type, label, icon) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color  = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape  = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                        SmallFloatingActionButton(
                            onClick           = { onAdd(type) },
                            containerColor    = MaterialTheme.colorScheme.primaryContainer,
                        ) { Icon(icon, label, Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        FloatingActionButton(
            onClick        = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add item",
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
    }
}
