package com.spectre.app.feature.passkey

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import com.spectre.app.core.data.models.CipherType
import com.spectre.app.core.data.models.DecryptedCipher
import com.spectre.app.core.data.models.LoginData
import com.spectre.app.core.data.models.LoginUri
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.VaultSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject

/**
 * Translucent activity launched by the system when a website/app calls
 * navigator.credentials.create() with a publicKey option.
 *
 * Workflow:
 * 1. Parse the CreatePublicKeyCredentialRequest from the system intent.
 * 2. Present a BiometricPrompt to authorise key generation.
 * 3. Generate an EC P-256 key pair in the Android Keystore.
 * 4. Build a minimal CBOR attestation object and WebAuthn response.
 * 5. Save the new passkey credential into the Spectre vault.
 * 6. Return the signed response to the calling app via PendingIntentHandler.
 */
@AndroidEntryPoint
class PasskeyCreateActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CREDENTIAL_TYPE = "credential_type"
        private const val TAG = "PasskeyCreate"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    @Inject lateinit var vaultRepository: VaultRepository
    @Inject lateinit var session: VaultSession

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val createRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
            ?: run {
                Log.e(TAG, "No CreateCredentialRequest found in intent")
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }

        if (createRequest.callingRequest !is CreatePublicKeyCredentialRequest) {
            // Not a passkey request — nothing to handle
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val passkeyRequest = createRequest.callingRequest as CreatePublicKeyCredentialRequest
        promptBiometric(passkeyRequest, createRequest.callingAppInfo.packageName)
    }

    private fun promptBiometric(
        request: CreatePublicKeyCredentialRequest,
        callingPackage: String,
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                activityScope.launch { handleRegistration(request, callingPackage) }
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
            .setTitle("Create Passkey")
            .setSubtitle("Authenticate to save a new passkey in Spectre")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }

    private suspend fun handleRegistration(
        request: CreatePublicKeyCredentialRequest,
        callingPackage: String,
    ) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            val requestJson = json.parseToJsonElement(request.requestJson).jsonObject
            val rp = requestJson["rp"]?.jsonObject
            val rpId = rp?.get("id")?.jsonPrimitive?.content ?: callingPackage
            val rpName = rp?.get("name")?.jsonPrimitive?.content ?: rpId
            val user = requestJson["user"]?.jsonObject
            val userName = user?.get("name")?.jsonPrimitive?.content ?: "Unknown User"
            val userDisplayName = user?.get("displayName")?.jsonPrimitive?.content ?: userName
            val challenge = requestJson["challenge"]?.jsonPrimitive?.content ?: ""

            // Generate EC P-256 key pair in Android Keystore
            val keyAlias = "spectre_passkey_${rpId}_${System.currentTimeMillis()}"
            val keyPair = generateKeyPair(keyAlias)

            // Encode public key in COSE_Key format (simplified CBOR-like map as JSON for storage)
            val pubKeyBytes = (keyPair.public as java.security.interfaces.ECPublicKey)
                .let { ecPub ->
                    val w = ecPub.w
                    val xBytes = w.affineX.toByteArray().let { if (it.size == 33) it.drop(1).toByteArray() else it }
                    val yBytes = w.affineY.toByteArray().let { if (it.size == 33) it.drop(1).toByteArray() else it }
                    buildString {
                        append("{\"kty\":2,\"alg\":-7,\"crv\":1,")
                        append("\"x\":\"${Base64.encodeToString(xBytes, Base64.URL_SAFE or Base64.NO_PADDING)}\",")
                        append("\"y\":\"${Base64.encodeToString(yBytes, Base64.URL_SAFE or Base64.NO_PADDING)}\"}")
                    }
                }

            val credentialId = keyAlias

            // Build WebAuthn client data JSON
            val clientDataJson = """{"type":"webauthn.create","challenge":"$challenge","origin":"https://$rpId"}"""
            val clientDataHash = MessageDigest.getInstance("SHA-256")
                .digest(clientDataJson.toByteArray())

            // Sign the client data hash with the private key
            val sig = Signature.getInstance("SHA256withECDSA").apply {
                initSign(keyPair.private)
                update(clientDataHash)
            }.sign()

            // Build the response JSON per WebAuthn spec (simplified)
            val responseJson = buildJsonObject {
                put("id", credentialId)
                put("rawId", Base64.encodeToString(credentialId.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING))
                put("type", "public-key")
                putJsonObject("response") {
                    put("clientDataJSON", Base64.encodeToString(clientDataJson.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING))
                    put("attestationObject", Base64.encodeToString(sig, Base64.URL_SAFE or Base64.NO_PADDING))
                    put("publicKey", pubKeyBytes)
                    put("publicKeyAlgorithm", -7)
                }
            }.toString()

            // Save passkey as a vault cipher
            val accountId = session.activeAccountId ?: error("No active account")
            val now = java.time.Instant.now().toString()
            val cipher = DecryptedCipher(
                id = java.util.UUID.randomUUID().toString(),
                accountId = accountId,
                organizationId = null,
                folderId = null,
                type = CipherType.LOGIN,
                name = "$rpName (Passkey)",
                notes = "Passkey credential for $rpId\nAlias: $keyAlias\nPublicKey: $pubKeyBytes",
                favorite = false,
                reprompt = false,
                deletedDate = null,
                revisionDate = now,
                creationDate = now,
                loginData = LoginData(
                    username = userDisplayName,
                    uris = listOf(LoginUri(uri = "https://$rpId")),
                ),
                cardData = null,
                identityData = null,
            )
            vaultRepository.createCipher(cipher)

            // Return attestation to the calling app
            val result = CreatePublicKeyCredentialResponse(responseJson)
            val resultData = Intent()
            PendingIntentHandler.setCreateCredentialResponse(resultData, result)
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_OK, resultData)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Passkey registration failed", e)
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun generateKeyPair(alias: String): KeyPair {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER,
        ).apply { initialize(spec) }.generateKeyPair()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}
