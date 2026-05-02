package com.spectre.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

sealed class BiometricResult {
    data class Success(val decryptedData: ByteArray) : BiometricResult()
    data class Error(val message: String) : BiometricResult()
    object Cancelled : BiometricResult()
    object NotEnrolled : BiometricResult()
    object NotAvailable : BiometricResult()
}

@Singleton
class BiometricUnlock @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val KEY_ALIAS = "spectre_biometric_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    fun isAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isEnrolled(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Encrypts [data] using a Keystore-backed AES key — returns the
     * IV + ciphertext as Base64 to persist in EncryptedSharedPreferences.
     * The decrypt cipher requires biometric confirmation.
     */
    fun encryptForBiometric(data: ByteArray): Pair<String, String> {
        val key    = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv         = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cipherText = Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
        return Pair(iv, cipherText)
    }

    /**
     * Shows the biometric prompt and — on success — decrypts [encryptedData]
     * with [iv] using the Keystore key that requires biometric auth.
     */
    fun promptToDecrypt(
        activity: FragmentActivity,
        encryptedData: String,
        iv: String,
        onResult: (BiometricResult) -> Unit,
    ) {
        if (!isEnrolled()) { onResult(BiometricResult.NotEnrolled); return }

        val key    = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            IvParameterSpec(Base64.decode(iv, Base64.NO_WRAP))
        )

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        val executor     = ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val decrypted = result.cryptoObject?.cipher?.doFinal(
                    Base64.decode(encryptedData, Base64.NO_WRAP)
                ) ?: ByteArray(0)
                onResult(BiometricResult.Success(decrypted))
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onResult(BiometricResult.Cancelled)
                } else {
                    onResult(BiometricResult.Error(errString.toString()))
                }
            }
            override fun onAuthenticationFailed() {
                // Don't call onResult here — user can retry
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Spectre")
            .setSubtitle("Confirm biometric to access your vault")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo, cryptoObject)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return keyGen.generateKey()
    }
}
