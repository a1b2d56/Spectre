package com.spectre.app.core.sshagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.spectre.app.MainActivity
import com.spectre.app.core.data.datastore.SpectrePreferences
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.LockState
import com.spectre.app.core.security.VaultSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

@AndroidEntryPoint
class SshAgentService : Service() {

    companion object {
        private const val TAG = "SshAgentService"
        
        const val ACTION_RUN_ANDROID_SSH_AGENT = "com.artemchep.keyguard.android.sshagent.ACTION_RUN_ANDROID_SSH_AGENT"
        const val EXTRA_PROTOCOL_VERSION = "com.artemchep.keyguard.extra.SSH_AGENT_PROTOCOL_VERSION"
        const val EXTRA_PROXY_PORT = "com.artemchep.keyguard.extra.SSH_AGENT_PROXY_PORT"
        const val EXTRA_SESSION_ID = "com.artemchep.keyguard.extra.SSH_AGENT_SESSION_ID"
        const val EXTRA_SESSION_SECRET = "com.artemchep.keyguard.extra.SSH_AGENT_SESSION_SECRET"

        private const val NOTIFICATION_ID = 8888
        private const val CHANNEL_ID = "ssh_agent_service_channel"

        fun getIntent(
            context: Context,
            protocolVersion: Int,
            proxyPort: Int,
            sessionId: String,
            sessionSecret: String
        ): Intent = Intent(context, SshAgentService::class.java).apply {
            action = ACTION_RUN_ANDROID_SSH_AGENT
            putExtra(EXTRA_PROTOCOL_VERSION, protocolVersion)
            putExtra(EXTRA_PROXY_PORT, proxyPort)
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_SESSION_SECRET, sessionSecret)
        }
    }

    @Inject
    lateinit var prefs: SpectrePreferences

    @Inject
    lateinit var vaultRepository: VaultRepository

    @Inject
    lateinit var session: VaultSession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var proxyJob: Job? = null

    // Cache of parsed keys mapping public key bytes to ParsedKey structure
    private val parsedKeysCache = mutableMapOf<String, SshKeyHelper.ParsedKey>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_RUN_ANDROID_SSH_AGENT) {
            stopSelf()
            return START_NOT_STICKY
        }

        val protocolVersion = intent.getIntExtra(EXTRA_PROTOCOL_VERSION, -1)
        val proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, -1)
        val sessionIdBase64 = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
        val sessionSecretBase64 = intent.getStringExtra(EXTRA_SESSION_SECRET) ?: ""

        val sessionId = runCatching { Base64.getDecoder().decode(sessionIdBase64) }.getOrNull()
        val sessionSecret = runCatching { Base64.getDecoder().decode(sessionSecretBase64) }.getOrNull()

        if (protocolVersion != SshAgentTcpProtocol.PROTOCOL_VERSION ||
            proxyPort !in 1..65535 ||
            sessionId == null || sessionId.size != SshAgentTcpProtocol.SESSION_ID_LENGTH ||
            sessionSecret == null || sessionSecret.size != SshAgentTcpProtocol.SESSION_SECRET_LENGTH
        ) {
            Log.w(TAG, "Invalid SSH Agent session parameters")
            stopSelf()
            return START_NOT_STICKY
        }

        startServiceForeground()

        proxyJob?.cancel()
        proxyJob = serviceScope.launch {
            runProxyBridge(proxyPort, sessionId, sessionSecret)
        }

        return START_NOT_STICKY
    }

    private fun startServiceForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spectre SSH Agent Active")
            .setContentText("Listening for authentication requests from local terminal")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun runProxyBridge(port: Int, sessionId: ByteArray, sessionSecret: ByteArray) {
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                Log.d(TAG, "Connecting to Termux SSH proxy on localhost:$port")
                socket = Socket("127.0.0.1", port)
                
                val channel = SshAgentTcpProtocol.openAsApp(
                    socket.getInputStream(),
                    socket.getOutputStream(),
                    sessionId,
                    sessionSecret
                )

                Log.d(TAG, "SSH Agent connection established successfully")
                
                while (isActive) {
                    val packet = channel.readPacket() ?: break
                    val response = processPacket(packet)
                    channel.writePacket(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SSH Agent proxy bridge", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "SSH Agent connection lost: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                runCatching { socket?.close() }
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
            }
        }
    }

    private suspend fun processPacket(packet: ByteArray): ByteArray {
        if (packet.isEmpty()) return byteArrayOf(5) // Failure

        val buffer = ByteBuffer.wrap(packet)
        val type = buffer.get().toInt() and 0xFF

        return when (type) {
            11 -> handleRequestIdentities()
            13 -> handleSignRequest(buffer)
            else -> byteArrayOf(5) // Failure
        }
    }

    private suspend fun handleRequestIdentities(): ByteArray {
        val response = ByteArrayOutputStream()
        response.write(12) // SSH2_AGENT_IDENTITIES_ANSWER

        if (session.lockState.value != LockState.UNLOCKED) {
            writeInt(response, 0)
            return response.toByteArray()
        }

        val accountId = session.activeAccountId ?: return response.toByteArray()
        val ciphers = vaultRepository.getAllDecryptedCiphers(accountId)
        
        parsedKeysCache.clear()

        val keys = ciphers.mapNotNull { cipher ->
            val noteText = cipher.notes ?: ""
            val pem = SshKeyHelper.findPrivateKeyInText(noteText) ?: return@mapNotNull null
            val parsed = SshKeyHelper.parsePrivateKey(pem, cipher.name) ?: return@mapNotNull null
            
            parsedKeysCache[Base64.getEncoder().encodeToString(parsed.publicKeyBytes)] = parsed
            parsed
        }

        writeInt(response, keys.size)
        for (key in keys) {
            writeBytes(response, key.publicKeyBytes)
            writeString(response, key.comment)
        }

        return response.toByteArray()
    }

    private suspend fun handleSignRequest(buffer: ByteBuffer): ByteArray {
        val keyBlob = readBytes(buffer)
        val dataToSign = readBytes(buffer)
        val flags = buffer.int

        val keyBase64 = Base64.getEncoder().encodeToString(keyBlob)
        val key = parsedKeysCache[keyBase64] ?: return byteArrayOf(5) // Key not found or locked

        // 1. Request user approval
        val approved = withContext(Dispatchers.Main) {
            val completer = CompletableDeferred<Boolean>()
            SshApprovalCoordinator.requestApproval(
                SshRequest(key.comment, key.fingerprint, "Termux")
            ) { result ->
                completer.complete(result)
            }

            val intent = Intent(applicationContext, SshApprovalActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            
            completer.await()
        }

        if (!approved) {
            return byteArrayOf(5) // Denied
        }

        // 2. Perform signing
        return try {
            val signature = SshKeyHelper.sign(key, dataToSign)
            
            // Encode signature in SSH wire format
            val sigBos = ByteArrayOutputStream()
            writeString(sigBos, key.typeString)
            writeBytes(sigBos, signature)
            
            val response = ByteArrayOutputStream()
            response.write(14) // SSH2_AGENT_SIGN_RESPONSE
            writeBytes(response, sigBos.toByteArray())
            response.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Signing failed", e)
            byteArrayOf(5) // Failure
        }
    }

    private fun writeInt(bos: ByteArrayOutputStream, v: Int) {
        bos.write((v ushr 24) and 0xFF)
        bos.write((v ushr 16) and 0xFF)
        bos.write((v ushr 8) and 0xFF)
        bos.write(v and 0xFF)
    }

    private fun writeBytes(bos: ByteArrayOutputStream, bytes: ByteArray) {
        writeInt(bos, bytes.size)
        bos.write(bytes)
    }

    private fun writeString(bos: ByteArrayOutputStream, str: String) {
        writeBytes(bos, str.encodeToByteArray())
    }

    private fun readBytes(buffer: ByteBuffer): ByteArray {
        val len = buffer.int
        val bytes = ByteArray(len)
        buffer.get(bytes)
        return bytes
    }

    override fun onDestroy() {
        proxyJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SSH Agent Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
