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
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val KEY_ALIAS = "spectre_biometric_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "spectre_biometric_prefs"
        private const val KEY_ENCRYPTED_DATA = "encrypted_master_pw"
        private const val KEY_IV = "master_pw_iv"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    private fun getEncryptedData(accountId: String): String? {
        val key = KEY_ENCRYPTED_DATA + "_" + accountId
        if (prefs.contains(key)) {
            return prefs.getString(key, null)
        }
        return prefs.getString(KEY_ENCRYPTED_DATA, null)
    }

    private fun getIv(accountId: String): String? {
        val key = KEY_IV + "_" + accountId
        if (prefs.contains(key)) {
            return prefs.getString(key, null)
        }
        return prefs.getString(KEY_IV, null)
    }

    /**
     * Checks if we have a stored encrypted master password for a specific account.
     */
    fun hasStoredSecret(accountId: String): Boolean {
        return !getEncryptedData(accountId).isNullOrBlank() &&
               !getIv(accountId).isNullOrBlank()
    }

    /**
     * Shows a biometric prompt to authorize ENCRYPTION of the master password.
     */
    fun promptToEncrypt(
        activity: androidx.fragment.app.FragmentActivity,
        accountId: String,
        masterPassword: String,
        onResult: (BiometricResult) -> Unit,
    ) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        val executor     = ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                try {
                    val encryptedBytes = result.cryptoObject?.cipher?.doFinal(
                        masterPassword.toByteArray(Charsets.UTF_8)
                    ) ?: throw Exception("Cipher failed")
                    
                    val iv         = Base64.encodeToString(result.cryptoObject?.cipher?.iv, Base64.NO_WRAP)
                    val cipherText = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

                    prefs.edit()
                        .putString(KEY_ENCRYPTED_DATA + "_" + accountId, cipherText)
                        .putString(KEY_IV + "_" + accountId, iv)
                        .apply()

                    onResult(BiometricResult.Success(ByteArray(0))) // Success indicator
                } catch (e: Exception) {
                    onResult(BiometricResult.Error("Encryption failed: ${e.message}"))
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onResult(BiometricResult.Cancelled)
                } else {
                    onResult(BiometricResult.Error(errString.toString()))
                }
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle("Confirm biometric to enable unlock")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(promptInfo, cryptoObject)
    }

    fun clearSecret(accountId: String) {
        prefs.edit()
            .remove(KEY_ENCRYPTED_DATA + "_" + accountId)
            .remove(KEY_IV + "_" + accountId)
            .apply()
    }

    /**
     * Shows the biometric prompt and — on success — decrypts the stored secret.
     */
    fun promptToUnlock(
        activity: androidx.fragment.app.FragmentActivity,
        accountId: String,
        onResult: (BiometricResult) -> Unit,
    ) {
        val encryptedData = getEncryptedData(accountId)
        val iv = getIv(accountId)

        if (encryptedData.isNullOrBlank() || iv.isNullOrBlank()) {
            onResult(BiometricResult.Error("No biometric data stored"))
            return
        }

        val ivBytes = try { Base64.decode(iv, Base64.NO_WRAP) } catch (e: Exception) { null }
        if (ivBytes == null || ivBytes.size != 16) {
            onResult(BiometricResult.Error("Invalid IV stored"))
            return
        }

        val key    = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(ivBytes))
        } catch (e: Exception) {
            onResult(BiometricResult.Error("Failed to init cipher: ${e.message}"))
            return
        }

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        val executor     = ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                try {
                    val cipher = result.cryptoObject?.cipher ?: throw NullPointerException("Biometric cryptoObject cipher is null")
                    val decrypted = cipher.doFinal(
                        Base64.decode(encryptedData, Base64.NO_WRAP)
                    )
                    onResult(BiometricResult.Success(decrypted))
                } catch (e: Exception) {
                    onResult(BiometricResult.Error("Decryption failed: ${e.message}"))
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onResult(BiometricResult.Cancelled)
                } else {
                    onResult(BiometricResult.Error(errString.toString()))
                }
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

