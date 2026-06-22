package com.spectre.app.core.sshagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spectre.app.core.ui.theme.SpectreAppTheme

object SshApprovalCoordinator {
    var pendingRequest: SshRequest? = null
    private var resultCallback: ((Boolean) -> Unit)? = null

    fun requestApproval(
        request: SshRequest,
        callback: (Boolean) -> Unit
    ) {
        pendingRequest = request
        resultCallback = callback
    }

    fun complete(approved: Boolean) {
        resultCallback?.invoke(approved)
        resultCallback = null
        pendingRequest = null
    }
}

data class SshRequest(
    val keyName: String,
    val fingerprint: String,
    val appName: String?
)

class SshApprovalActivity : androidx.fragment.app.FragmentActivity() {

    private var isPromptShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val request = SshApprovalCoordinator.pendingRequest
        if (request == null) {
            finish()
            return
        }

        setContent {
            SpectreAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f) // Dim background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )

                                Text(
                                    text = "SSH Signature Request",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                val clientApp = request.appName ?: "Local Terminal (Termux)"
                                Text(
                                    text = "$clientApp is requesting an SSH signature.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Key Name: ${request.keyName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Fingerprint:\n${request.fingerprint}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { deny() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Deny")
                                    }

                                    Button(
                                        onClick = { triggerBiometrics() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Approve")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto trigger biometrics
        if (!isPromptShown) {
            triggerBiometrics()
        }
    }

    private fun triggerBiometrics() {
        isPromptShown = true
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    approve()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        deny()
                    }
                }

                override fun onAuthenticationFailed() {
                    // Fail count incremented, wait for success
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Approve SSH Signature")
            .setSubtitle("Authenticate to unlock key and sign request")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun approve() {
        SshApprovalCoordinator.complete(true)
        finish()
    }

    private fun deny() {
        SshApprovalCoordinator.complete(false)
        finish()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        deny()
    }
}
