package com.spectre.app.feature.passkey

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderGetCredentialRequest
import androidx.fragment.app.FragmentActivity
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.VaultSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.security.*
import javax.inject.Inject

/**
 * Translucent activity launched by the system when a website/app calls
 * navigator.credentials.get() and the user selects a passkey stored in Spectre.
 *
 * Workflow:
 * 1. Retrieve the ProviderGetCredentialRequest from the system intent.
 * 2. Find the matching cipher in the vault by cipherId or RP ID.
 * 3. Prompt biometric authentication.
 * 4. Sign the WebAuthn challenge using the EC private key from Android Keystore.
 * 5. Return a signed PublicKeyCredential assertion to the calling app.
 */
@AndroidEntryPoint
class PasskeyGetActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CIPHER_ID  = "cipher_id"
        const val EXTRA_RP_ID      = "rp_id"
        const val EXTRA_IS_LOCKED  = "is_locked"
        private const val TAG = "PasskeyGet"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    @Inject lateinit var vaultRepository: VaultRepository
    @Inject lateinit var session: VaultSession

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var cipherId: String? = null
    private var rpId: String? = null
    private var isLocked: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cipherId = intent.getStringExtra(EXTRA_CIPHER_ID)
        rpId     = intent.getStringExtra(EXTRA_RP_ID)
        isLocked = intent.getBooleanExtra(EXTRA_IS_LOCKED, false)

        if (isLocked) {
            // Vault is locked — route user to main activity to unlock, then come back
            Log.i(TAG, "Vault locked, directing to unlock")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val getRequest = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
            ?: run {
                Log.e(TAG, "No ProviderGetCredentialRequest found")
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }

        promptBiometric(getRequest)
    }

    private fun promptBiometric(request: ProviderGetCredentialRequest) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                activityScope.launch { handleAssertion(request) }
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                Log.w(TAG, "Biometric error $code: $msg")
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            override fun onAuthenticationFailed() {
                Log.w(TAG, "Biometric authentication failed")
            }
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sign in with Passkey")
            .setSubtitle("Authenticate to use your Spectre passkey")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }

    private suspend fun handleAssertion(request: ProviderGetCredentialRequest) {
        try {
            // Find the matching public key option
            val pkOption = request.credentialOptions
                .filterIsInstance<GetPublicKeyCredentialOption>()
                .firstOrNull()
                ?: error("No public-key option in request")

            val json = Json { ignoreUnknownKeys = true }
            val requestJson = Json.parseToJsonElement(pkOption.requestJson).jsonObject
            val challenge = requestJson["challenge"]?.jsonPrimitive?.contentOrNull
                ?: error("No challenge in request")
            val resolvedRpId = requestJson["rpId"]?.jsonPrimitive?.contentOrNull ?: rpId
                ?: error("No RP ID")

            // Look up the vault cipher that holds the key alias
            val targetCipher = if (cipherId != null) {
                vaultRepository.observeById(cipherId!!).first()
            } else {
                vaultRepository.observeAllCiphers().first().firstOrNull { cipher ->
                    cipher.loginData?.uris?.any { it.uri?.contains(resolvedRpId, ignoreCase = true) == true } == true
                }
            } ?: error("No matching passkey found for RP ID: $resolvedRpId")

            // Extract the Keystore alias from the cipher notes
            val notes = targetCipher.notes ?: ""
            val keyAlias = notes.lines()
                .firstOrNull { it.startsWith("Alias: ") }
                ?.removePrefix("Alias: ")
                ?.trim()
                ?: error("No Keystore alias found in cipher notes")

            // Load private key from Android Keystore
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey
                ?: error("Private key not found in Keystore: $keyAlias")

            // Build the WebAuthn clientDataJSON
            val clientDataJson = """{"type":"webauthn.get","challenge":"$challenge","origin":"https://$resolvedRpId"}"""
            val clientDataHash = MessageDigest.getInstance("SHA-256")
                .digest(clientDataJson.toByteArray())

            // Sign the challenge
            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initSign(privateKey)
                update(clientDataHash)
            }.sign()

            val credentialIdB64 = android.util.Base64.encodeToString(
                keyAlias.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
            )
            val clientDataB64 = android.util.Base64.encodeToString(
                clientDataJson.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
            )
            val sigB64 = android.util.Base64.encodeToString(
                signature, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
            )

            // Minimal authenticatorData: 37 bytes (rpId hash + flags + counter)
            val rpIdHash = MessageDigest.getInstance("SHA-256").digest(resolvedRpId.toByteArray())
            val authData = ByteArray(37).also { buf ->
                rpIdHash.copyInto(buf, 0)
                buf[32] = 0x05.toByte() // UP (user present) + UV (user verified)
                // counter = 0 (bytes 33-36 stay 0)
            }
            val authDataB64 = android.util.Base64.encodeToString(
                authData, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
            )

            // Build assertion response JSON per WebAuthn spec
            val responseJson = buildJsonObject {
                put("id", credentialIdB64)
                put("rawId", credentialIdB64)
                put("type", "public-key")
                putJsonObject("response") {
                    put("clientDataJSON", clientDataB64)
                    put("authenticatorData", authDataB64)
                    put("signature", sigB64)
                    put("userHandle", JsonNull)
                }
            }.toString()

            val credential = PublicKeyCredential(responseJson)
            val resultData = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                resultData,
                GetCredentialResponse(credential),
            )
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_OK, resultData)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Passkey assertion failed", e)
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}
