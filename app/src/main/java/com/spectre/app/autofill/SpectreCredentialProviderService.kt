package com.spectre.app.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.VaultSession
import com.spectre.app.feature.passkey.PasskeyCreateActivity
import com.spectre.app.feature.passkey.PasskeyGetActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * Spectre Credential Provider Service (Android 14+).
 *
 * Extends the Jetpack [CredentialProviderService] so that all Jetpack
 * credential types ([PublicKeyCredentialEntry], [CreateEntry], etc.) are
 * accepted by the response builders without platform-type mismatches.
 *
 * Abstract method signatures taken verbatim from
 * androidx.credentials.provider.CredentialProviderService in credentials:1.6.0:
 *   - onBeginGetCredentialRequest  → OutcomeReceiver<…, GetCredentialException>
 *   - onBeginCreateCredentialRequest → OutcomeReceiver<…, CreateCredentialException>
 *   - onClearCredentialStateRequest  → OutcomeReceiver<Void?, ClearCredentialException>
 *                                      with ProviderClearCredentialStateRequest
 */
@AndroidEntryPoint
class SpectreCredentialProviderService : CredentialProviderService() {

    @Inject lateinit var vaultRepository: VaultRepository
    @Inject lateinit var session: VaultSession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Begin Get Credential (login / assertion) ──────────────────────────

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        serviceScope.launch {
            try {
                callback.onResult(buildGetResponse(request))
            } catch (e: Exception) {
                callback.onResult(BeginGetCredentialResponse())
            }
        }
        cancellationSignal.setOnCancelListener { serviceScope.coroutineContext.cancel() }
    }

    private suspend fun buildGetResponse(
        request: BeginGetCredentialRequest,
    ): BeginGetCredentialResponse {
        val entries  = mutableListOf<PublicKeyCredentialEntry>()
        val isLocked = !session.isUnlocked

        for (option in request.beginGetCredentialOptions) {
            if (option !is BeginGetPublicKeyCredentialOption) continue

            val rpId = runCatching {
                Json.parseToJsonElement(option.requestJson)
                    .jsonObject["rpId"]?.jsonPrimitive?.content
            }.getOrNull() ?: continue

            if (isLocked) {
                entries += PublicKeyCredentialEntry.Builder(
                    context       = this@SpectreCredentialProviderService,
                    username      = "Spectre — Vault Locked",
                    pendingIntent = buildGetPendingIntent(null, rpId, isLocked = true),
                    beginGetPublicKeyCredentialOption = option,
                ).setDisplayName("Tap to unlock and autofill").build()
                break
            }

            vaultRepository.observeAllCiphers().first()
                .filter { cipher ->
                    cipher.loginData?.uris?.any {
                        it.uri?.contains(rpId, ignoreCase = true) == true
                    } == true
                }
                .forEach { cipher ->
                    entries += PublicKeyCredentialEntry.Builder(
                        context       = this@SpectreCredentialProviderService,
                        username      = cipher.name,
                        pendingIntent = buildGetPendingIntent(cipher.id, rpId, isLocked = false),
                        beginGetPublicKeyCredentialOption = option,
                    ).setDisplayName(cipher.primaryUsername ?: cipher.name).build()
                }
        }

        return BeginGetCredentialResponse(credentialEntries = entries)
    }

    // ── Begin Create Credential (registration) ────────────────────────────

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        serviceScope.launch {
            try {
                val pi = buildCreatePendingIntent(request.type)
                val entry = CreateEntry.Builder(
                    accountName   = "Spectre Password Manager",
                    pendingIntent = pi,
                ).build()
                callback.onResult(BeginCreateCredentialResponse(createEntries = listOf(entry)))
            } catch (e: Exception) {
                callback.onResult(BeginCreateCredentialResponse())
            }
        }
        cancellationSignal.setOnCancelListener { serviceScope.coroutineContext.cancel() }
    }

    // ── Clear credential state ────────────────────────────────────────────

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── PendingIntent helpers ─────────────────────────────────────────────

    private fun buildGetPendingIntent(
        cipherId: String?,
        rpId: String,
        isLocked: Boolean,
    ): PendingIntent {
        val intent = Intent(this, PasskeyGetActivity::class.java).apply {
            cipherId?.let { putExtra(PasskeyGetActivity.EXTRA_CIPHER_ID, it) }
            putExtra(PasskeyGetActivity.EXTRA_RP_ID, rpId)
            putExtra(PasskeyGetActivity.EXTRA_IS_LOCKED, isLocked)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            (cipherId ?: rpId).hashCode(),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun buildCreatePendingIntent(credentialType: String): PendingIntent {
        val intent = Intent(this, PasskeyCreateActivity::class.java).apply {
            putExtra(PasskeyCreateActivity.EXTRA_CREDENTIAL_TYPE, credentialType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            credentialType.hashCode(),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
