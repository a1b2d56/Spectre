package com.spectre.app.feature.untracker

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spectre.app.core.data.datastore.SpectrePreferences
import com.spectre.app.core.network.LinkCleaner
import com.spectre.app.core.ui.theme.SpectreAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class UntrackActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: SpectrePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = runBlocking { prefs.settings.first() }
        if (!settings.linkCleanerEnabled) {
            if (intent.action == Intent.ACTION_PROCESS_TEXT) {
                val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                if (!readOnly) {
                    val origText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                    setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, origText))
                }
            }
            finish()
            return
        }

        val intent = intent
        val text: String?
        val onShareText: (String) -> Unit
        val onSetProcessedText: ((String) -> Unit)?

        @SuppressLint("InlinedApi")
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                onShareText = { shareText(it) }
                val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                onSetProcessedText = if (!readOnly) {
                    { setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, it)) }
                } else {
                    null
                }
            }
            Intent.ACTION_SEND -> {
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                onShareText = { shareText(it, intent) }
                onSetProcessedText = null
            }
            else -> {
                text = null
                onSetProcessedText = null
                onShareText = {}
            }
        }

        if (text == null) {
            finish()
            return
        }

        setContent {
            val settings by prefs.settings.collectAsState(initial = com.spectre.app.core.data.datastore.SpectreSettings())
            SpectreAppTheme(
                appTheme = settings.theme,
                fontScale = settings.fontScale,
                isBold = settings.isBold,
                fontFamilyKey = settings.fontFamily
            ) {
                UntrackBottomSheet(
                    text = text,
                    onShareText = onShareText,
                    onSetProcessedText = onSetProcessedText,
                    onDismiss = this::finish
                )
            }
        }
    }

    private fun shareText(text: String, originalIntent: Intent? = null) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .apply {
                        if (originalIntent != null) {
                            putExtras(originalIntent)
                        }
                    }
                    .putExtra(Intent.EXTRA_TEXT, text),
                null
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && originalIntent != null) {
                    putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(originalIntent.component))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UntrackBottomSheet(
    text: String,
    onShareText: (String) -> Unit,
    onSetProcessedText: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var isProcessing by remember { mutableStateOf(true) }
    var untrackedText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    LaunchedEffect(text) {
        withContext(Dispatchers.Default) {
            untrackedText = LinkCleaner.untrack(text)
            isProcessing = false
        }
    }

    val dismiss: () -> Unit = {
        coroutineScope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "LINK CLEANER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "Original Link",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Cleaned Link",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = if (isProcessing) Alignment.Center else Alignment.TopStart
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    SelectionContainer {
                        Text(
                            text = untrackedText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isProcessing) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Cleaned Link", untrackedText))
                            dismiss()
                        },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy")
                    }

                    Button(
                        onClick = {
                            onShareText(untrackedText)
                            dismiss()
                        },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }

                    if (onSetProcessedText != null) {
                        Button(
                            onClick = {
                                onSetProcessedText(untrackedText)
                                dismiss()
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Confirm")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = dismiss,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
