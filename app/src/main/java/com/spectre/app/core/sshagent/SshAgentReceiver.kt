package com.spectre.app.core.sshagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.spectre.app.core.data.datastore.SpectrePreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SshAgentReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SshAgentReceiver"
    }

    @Inject
    lateinit var prefs: SpectrePreferences

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = prefs.settings.first()
                if (!settings.sshAgentEnabled) {
                    Log.d(TAG, "Ignoring SSH Agent broadcast: Service is disabled in settings")
                    return@launch
                }

                val protocolVersion = intent.getIntExtra(SshAgentService.EXTRA_PROTOCOL_VERSION, -1)
                val proxyPort = intent.getIntExtra(SshAgentService.EXTRA_PROXY_PORT, -1)
                val sessionId = intent.getStringExtra(SshAgentService.EXTRA_SESSION_ID) ?: ""
                val sessionSecret = intent.getStringExtra(SshAgentService.EXTRA_SESSION_SECRET) ?: ""

                if (protocolVersion != SshAgentTcpProtocol.PROTOCOL_VERSION ||
                    proxyPort !in 1..65535 ||
                    sessionId.isEmpty() ||
                    sessionSecret.isEmpty()
                ) {
                    Log.w(TAG, "Ignoring invalid SSH Agent broadcast parameters")
                    return@launch
                }

                val serviceIntent = SshAgentService.getIntent(
                    context = context,
                    protocolVersion = protocolVersion,
                    proxyPort = proxyPort,
                    sessionId = sessionId,
                    sessionSecret = sessionSecret
                )

                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process SSH Agent broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
