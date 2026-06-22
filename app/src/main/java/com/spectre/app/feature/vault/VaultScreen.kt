package com.spectre.app.feature.vault

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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


enum class VaultFilter { ALL, LOGINS, CARDS, IDENTITIES, NOTES, FAVOURITES, TRASH }

data class VaultUiState(
    val ciphers: List<DecryptedCipher>  = emptyList(),
    val folders: List<DecryptedFolder>  = emptyList(),
    val query: String                   = "",
    val filter: VaultFilter             = VaultFilter.ALL,
    val isLoading: Boolean              = false,
    val isSyncing: Boolean              = false,
    val syncError: String?              = null,
    val selectedFolderId: String?       = null,
)

/**
 * ViewModel for the main Vault screen, handling search, filtering, and syncing.
 */
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
            delay(200)
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
            val flow = if (folderId != null) vaultRepository.observeFolder(folderId)
                       else vaultRepository.observeAllCiphers()
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
                    syncError = result.exceptionOrNull()?.message,
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

/**
 * The main entry point for the user's vault items.
 */
@Composable
fun VaultScreen(
    onItemClick: (String) -> Unit,
    onAddClick: (type: Int) -> Unit,
    modifier: Modifier = Modifier,
    vm: VaultViewModel = hiltViewModel(),
) {
    val state         by vm.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showAddMenu   by remember { mutableStateOf(false) }
    var showFilters   by remember { mutableStateOf(false) }
    var searchActive  by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar   = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        // Incognito search — no keyboard learning
                        OutlinedTextField(
                            value         = state.query,
                            onValueChange = vm::onQueryChange,
                            placeholder   = { Text("Search vault…") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth().padding(end = 8.dp),
                            shape         = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction    = ImeAction.Search,
                                autoCorrectEnabled = false,
                            ),
                        )
                    } else {
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text("Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, "Search")
                        }
                    } else {
                        IconButton(onClick = { searchActive = false; vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, "Close search")
                        }
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Filled.FilterList, "Filters")
                    }
                    IconButton(onClick = vm::sync, enabled = !state.isSyncing) {
                        if (state.isSyncing)
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Filled.Sync, "Sync")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors         = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            AddCipherFab(
                expanded = showAddMenu,
                onToggle = { showAddMenu = !showAddMenu },
                onAdd    = { type -> showAddMenu = false; onAddClick(type) },
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 4.dp) // Subtle horizontal breathing room
        ) {

            if (showFilters) {
                FilterBottomSheet(
                    current = state.filter,
                    onChange = { vm.onFilterChange(it); showFilters = false },
                    onDismiss = { showFilters = false }
                )
            }

            if (state.folders.isNotEmpty() && state.filter == VaultFilter.ALL) {
                FolderChipRow(
                    folders  = state.folders,
                    selected = state.selectedFolderId,
                    onSelect = vm::onFolderSelect,
                )
            }

            state.syncError?.let { err ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color    = MaterialTheme.colorScheme.errorContainer,
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sync failed: $err", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
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
                    subtitle = if (state.query.isNotBlank())
                        "No results for \"${state.query}\""
                    else
                        "Tap ＋ to add your first item, or tap ↻ to sync.",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    items(state.ciphers, key = { it.id }) { cipher ->
                        CipherListItem(
                            cipher     = cipher,
                            onClick    = { onItemClick(cipher.id) },
                            onFavorite = { vm.toggleFavorite(cipher.id) },
                        )
                        HorizontalDivider(
                            modifier  = Modifier.padding(start = 70.dp),
                            thickness = 0.5.dp,
                            color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    current: VaultFilter,
    onChange: (VaultFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val filters = listOf(
        VaultFilter.ALL        to "All Items",
        VaultFilter.LOGINS     to "Logins",
        VaultFilter.CARDS      to "Cards",
        VaultFilter.IDENTITIES to "Identities",
        VaultFilter.NOTES      to "Secure Notes",
        VaultFilter.FAVOURITES to "Favourites",
        VaultFilter.TRASH      to "Trash",
    )
    val icons = listOf(
        Icons.AutoMirrored.Filled.List,
        Icons.Filled.Key,
        Icons.Filled.CreditCard,
        Icons.Filled.Person,
        Icons.AutoMirrored.Filled.StickyNote2,
        Icons.Filled.Star,
        Icons.Filled.Delete,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Filter Vault",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            filters.forEachIndexed { index, (filter, label) ->
                val selected = current == filter
                Surface(
                    onClick = { onChange(filter) },
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icons[index],
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (selected) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
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
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected    = selected == null,
                onClick     = { onSelect(null) },
                label       = { Text("All folders", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, null, Modifier.size(14.dp)) },
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected    = selected == folder.id,
                onClick     = { onSelect(folder.id) },
                label       = { Text(folder.name, style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(14.dp)) },
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
        label        = "fabRot",
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
                    Triple(1, "Login",       Icons.Filled.Key),
                    Triple(3, "Card",        Icons.Filled.CreditCard),
                    Triple(4, "Identity",    Icons.Filled.Person),
                    Triple(2, "Secure Note", Icons.AutoMirrored.Filled.StickyNote2),
                ).forEach { (type, label, icon) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color    = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape    = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        SmallFloatingActionButton(
                            onClick        = { onAdd(type) },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ) { Icon(icon, label, Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        FloatingActionButton(
            onClick        = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier       = Modifier.padding(bottom = 80.dp) // Shift up to clear floating navbar
        ) {
            Icon(
                Icons.Filled.Add,
                "Add item",
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
    }
}
