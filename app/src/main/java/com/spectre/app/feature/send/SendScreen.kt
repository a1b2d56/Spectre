package com.spectre.app.feature.send

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spectre.app.core.ui.components.*

@Composable
fun SendScreen(
    vm: SendViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snackbarFlow = vm.snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        snackbarFlow.collect { snackbarHostState.showSnackbar(it) }
    }

    var isTextMode by remember { mutableStateOf(true) }
    var sendName by remember { mutableStateOf("") }
    var textPayload by remember { mutableStateOf("") }
    var expirationDays by remember { mutableFloatStateOf(7f) }
    var maxAccessCount by remember { mutableFloatStateOf(0f) }
    var isPasswordProtected by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            SpectreTopBar(title = "Send")

            Spacer(Modifier.height(8.dp))

            // Mode Selector
            SpectreCard(padding = 4.dp) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val activeColor = MaterialTheme.colorScheme.primary
                    val inactiveColor = Color.Transparent
                    
                    Button(
                        onClick = { isTextMode = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isTextMode) activeColor else inactiveColor),
                        shape = RoundedCornerShape(12.dp),
                        elevation = null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Notes, null, tint = if (isTextMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Text", color = if (isTextMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { 
                            if (state.isPremium) {
                                isTextMode = false 
                            } else {
                                // Handled by vm.generateLink or we can show a message here
                                // For now let's just allow them to click but the button below will be disabled or show error
                                isTextMode = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isTextMode) activeColor else inactiveColor),
                        shape = RoundedCornerShape(12.dp),
                        elevation = null
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FilePresent, null, tint = if (!isTextMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text("File", color = if (!isTextMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!state.isPremium) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(14.dp), tint = if (!isTextMode) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SpectreCard {
                Text("Content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                Text("Send Name (Optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sendName,
                    onValueChange = { sendName = it },
                    placeholder = { Text("e.g. My Secret Note") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))

                if (isTextMode) {
                    Text("Secret Text", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textPayload,
                        onValueChange = { textPayload = it },
                        placeholder = { Text("Enter secret text to share...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var selectedFileName by remember { mutableStateOf<String?>(null) }
                    
                    if (!state.isPremium) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Filled.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Bitwarden Premium Required", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("File sharing is a premium-only feature.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null) {
                                val cursor = context.contentResolver.query(uri, null, null, null, null)
                                cursor?.use {
                                    if (it.moveToFirst()) {
                                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        if (nameIndex != -1) {
                                            selectedFileName = it.getString(nameIndex)
                                        }
                                    }
                                }
                                if (selectedFileName == null) selectedFileName = "File selected"
                            }
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.CloudUpload, null)
                                Spacer(Modifier.height(4.dp))
                                Text(selectedFileName ?: "Select File (Max 100MB)")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SpectreCard {
                Text("Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Deletion Date", style = MaterialTheme.typography.bodyMedium)
                    Text("${expirationDays.toInt()} Days", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = expirationDays,
                    onValueChange = { expirationDays = it },
                    valueRange = 0f..31f,
                    steps = 31
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Max Access Count", style = MaterialTheme.typography.bodyMedium)
                    Text(if (maxAccessCount == 0f) "Unlimited" else "${maxAccessCount.toInt()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = maxAccessCount,
                    onValueChange = { maxAccessCount = it },
                    valueRange = 0f..50f,
                    steps = 49
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Password Protection", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isPasswordProtected, onCheckedChange = { isPasswordProtected = it })
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { 
                    vm.generateLink(sendName, isTextMode, textPayload, expirationDays, maxAccessCount, isPasswordProtected)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                enabled = !state.isGenerating && (
                    (isTextMode && textPayload.isNotBlank()) || 
                    (!isTextMode && state.isPremium)
                )
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Link, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Secure Link")
                }
            }

            Spacer(Modifier.height(120.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        )

        if (state.generatedLink != null) {
            AlertDialog(
                onDismissRequest = vm::dismissLink,
                icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Link Generated") },
                text = {
                    Column {
                        Text("Your secure link is ready to share. Anyone with this link can access the payload.")
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp)
                        ) {
                            Text(state.generatedLink!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = vm::copyLink) { Text("Copy Link") }
                },
                dismissButton = {
                    TextButton(onClick = vm::dismissLink) { Text("Dismiss") }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
