package com.spectre.app.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.spectre.app.core.ui.theme.dynamic
import com.spectre.app.core.ui.components.SpectreCard
import com.spectre.app.core.ui.components.PickerDialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(
        val name: String,
        val tagName: String,
        val changelog: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val publishedAt: String,
        val htmlUrl: String
    ) : UpdateState
    object UpToDate : UpdateState
    data class Error(val message: String) : UpdateState
}

@dagger.hilt.android.lifecycle.HiltViewModel
class AboutViewModel @javax.inject.Inject constructor(
    private val updateRepository: com.spectre.app.core.data.repository.UpdateRepository,
    private val prefs: com.spectre.app.core.data.datastore.SpectrePreferences,
) : androidx.lifecycle.ViewModel() {

    val settings: StateFlow<com.spectre.app.core.data.datastore.SpectreSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.spectre.app.core.data.datastore.SpectreSettings())

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun setUpdateCheckInterval(interval: com.spectre.app.core.data.datastore.UpdateCheckInterval) {
        viewModelScope.launch {
            prefs.setUpdateCheckInterval(interval)
        }
    }

    fun checkForUpdates(versionName: String, force: Boolean = false) {
        viewModelScope.launch {
            val currentSettings = settings.value
            val now = System.currentTimeMillis()

            if (!force) {
                val interval = currentSettings.updateCheckInterval
                if (interval == com.spectre.app.core.data.datastore.UpdateCheckInterval.MANUAL) {
                    return@launch
                }
                val intervalMs = interval.days * 24L * 60 * 60 * 1000
                val elapsed = now - currentSettings.lastUpdateCheckTime
                if (elapsed < intervalMs) {
                    return@launch
                }
            }

            _updateState.value = UpdateState.Checking
            updateRepository.checkForUpdate()
                .onSuccess { release ->
                    if (release != null) {
                        prefs.setLastUpdateCheckTime(System.currentTimeMillis())
                        val isNew = updateRepository.isNewerVersion(versionName, release.tagName)
                        if (isNew) {
                            val apkAsset = release.assets.firstOrNull {
                                it.name.endsWith("-release.apk") ||
                                it.name.contains("-release-") ||
                                it.contentType == "application/vnd.android.package-archive"
                            }
                            if (apkAsset != null) {
                                _updateState.value = UpdateState.UpdateAvailable(
                                    name = release.name,
                                    tagName = release.tagName,
                                    changelog = release.body,
                                    downloadUrl = apkAsset.downloadUrl,
                                    sizeBytes = apkAsset.size,
                                    publishedAt = release.publishedAt,
                                    htmlUrl = release.htmlUrl
                                )
                            } else {
                                _updateState.value = UpdateState.UpToDate
                            }
                        } else {
                            _updateState.value = UpdateState.UpToDate
                        }
                    } else {
                        _updateState.value = UpdateState.Error("Empty release response from server")
                    }
                }
                .onFailure {
                    val isNetworkError = it is java.net.UnknownHostException || 
                                         it is java.net.ConnectException || 
                                         it is java.io.InterruptedIOException || 
                                         it.message?.contains("Unable to resolve host") == true
                    
                    if (isNetworkError) {
                        if (force) {
                            _updateState.value = UpdateState.Error("Could not reach update server. You may be offline.")
                        } else {
                            // If autochecking on open, silently treat as up-to-date so we don't display error states to offline users
                            _updateState.value = UpdateState.UpToDate
                        }
                    } else {
                        _updateState.value = UpdateState.Error(it.message ?: "Unknown network error")
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    vm: AboutViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName ?: "Unknown"
    val uriHandler = LocalUriHandler.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()

    var showIntervalPicker by remember { mutableStateOf(false) }

    LaunchedEffect(versionName) {
        vm.checkForUpdates(versionName, force = false)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold.dynamic()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        radius = 1200f,
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Styled premium badge
            Icon(
                painter = painterResource(com.spectre.app.R.drawable.ic_launcher_foreground),
                contentDescription = "Spectre Logo",
                modifier = Modifier.size(128.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Spectre",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold.dynamic()
            )
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Update status section
            UpdateStatusCard(
                updateState = updateState,
                onCheckForUpdates = { vm.checkForUpdates(versionName, force = true) },
                uriHandler = uriHandler
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Settings card
            SpectreCard(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    title = "Automatic update check",
                    subtitle = settings.updateCheckInterval.label,
                    icon = Icons.Default.Update,
                    onClick = { showIntervalPicker = true }
                )
            }
        }
    }

    if (showIntervalPicker) {
        PickerDialog(
            title = "Update Check Frequency",
            options = com.spectre.app.core.data.datastore.UpdateCheckInterval.entries.map { it.label },
            selectedIndex = com.spectre.app.core.data.datastore.UpdateCheckInterval.entries.indexOf(settings.updateCheckInterval),
            onSelect = {
                vm.setUpdateCheckInterval(com.spectre.app.core.data.datastore.UpdateCheckInterval.entries[it])
                showIntervalPicker = false
            },
            onDismiss = { showIntervalPicker = false }
        )
    }
}

@Composable
private fun UpdateStatusCard(
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    var changelogExpanded by remember { mutableStateOf(false) }

    SpectreCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (updateState) {
                is UpdateState.Idle -> {
                    Text(
                        text = "Updates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold.dynamic()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onCheckForUpdates) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for updates")
                    }
                }
                is UpdateState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Checking for updates...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UpdateState.UpToDate -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "You're on the latest version",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onCheckForUpdates) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check again")
                    }
                }
                is UpdateState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onCheckForUpdates) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
                is UpdateState.UpdateAvailable -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New version available!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold.dynamic()
                            )
                            Text(
                                text = "${updateState.name} (${formatReleaseDate(updateState.publishedAt)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Changelog box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Changelog",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val displayChangelog = if (changelogExpanded) {
                                updateState.changelog
                            } else {
                                updateState.changelog.lines().take(6).joinToString("\n")
                            }

                            Text(
                                text = displayChangelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (updateState.changelog.lines().size > 6) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (changelogExpanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { changelogExpanded = !changelogExpanded }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val sizeMb = "%.1f".format(Locale.US, updateState.sizeBytes / (1024.0 * 1024.0))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { uriHandler.openUri(updateState.htmlUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GitHub Page")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { uriHandler.openUri(updateState.downloadUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download ($sizeMb MB)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (painter != null || icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatReleaseDate(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        isoString.substringBefore("T")
    }
}
