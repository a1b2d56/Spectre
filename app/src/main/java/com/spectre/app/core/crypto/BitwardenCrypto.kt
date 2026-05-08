package com.spectre.app.core.crypto

import android.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import com.spectre.app.core.utils.suspendRunCatching
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core Bitwarden cryptographic engine.
 *
 * Implements the full Bitwarden crypto spec:
 *   https://bitwarden.com/help/bitwarden-security-white-paper/
 *
 * Key derivation flow:
 *   Master Password + Email → (PBKDF2 | Argon2id) → Master Key (32 bytes)
 *   Master Key → HKDF-Expand → Stretched Key (64 bytes) [enc || mac]
 *   Master Key + Master Password → PBKDF2 (1 iter) → Master Password Hash (sent to server)
 *   Stretched Key → decrypt EncryptedKey from server → User Symmetric Key (64 bytes)
 *   User Symmetric Key → decrypt individual cipher fields
 */
@Singleton
class BitwardenCrypto @Inject constructor() {

    private val secureRandom = SecureRandom()

    companion object {
        private const val AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding"
        private const val HMAC_SHA256   = "HmacSHA256"
    }

    // Key Derivation

    /**
     * Derives the master key from master password and email.
     *
     * @param kdfType       0 = PBKDF2-SHA256, 1 = Argon2id
     * @param kdfIterations PBKDF2 iteration count (default 600_000 as of 2024)
     * @param kdfMemory     Argon2id memory in KiB (default 64 MiB = 65536)
     * @param kdfParallelism Argon2id parallelism (default 4)
     */
    fun deriveMasterKey(
        password: String,
        email: String,
        kdfType: Int = 0,
        kdfIterations: Int = 600_000,
        kdfMemory: Int = 65_536,
        kdfParallelism: Int = 4,
    ): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val saltBytes     = email.trim().lowercase().toByteArray(Charsets.UTF_8)

        return when (kdfType) {
            0 -> pbkdf2HmacSha256(passwordBytes, saltBytes, kdfIterations, 32)
            1 -> argon2id(passwordBytes, saltBytes, kdfMemory, kdfIterations, kdfParallelism, 32)
            else -> error("Unsupported KDF type: $kdfType")
        }
    }

    /**
     * Stretches the 32-byte master key into a 64-byte symmetric key using HKDF.
     *
     * Per Bitwarden spec, uses HKDF with:
     *   info "enc" → first 32 bytes (encryption key)
     *   info "mac" → next  32 bytes (MAC key)
     */
    fun stretchMasterKey(masterKey: ByteArray): SymmetricKey {
        require(masterKey.size == 32) { "Master key must be 32 bytes" }
        val encKey = hkdfExpand(masterKey, "enc".toByteArray(), 32)
        val macKey = hkdfExpand(masterKey, "mac".toByteArray(), 32)
        return SymmetricKey.fromSplit(encKey, macKey)
    }

    /**
     * Produces the master password hash sent to the Bitwarden server for authentication.
     * This is PBKDF2(masterKey, masterPassword, 1 iteration, SHA-256).
     */
    fun hashMasterPassword(masterKey: ByteArray, password: String): String {
        val hash = pbkdf2HmacSha256(
            key        = masterKey,
            salt       = password.toByteArray(Charsets.UTF_8),
            iterations = 1,
            outputLen  = 32
        )
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    // Symmetric Encryption / Decryption

    /**
     * Decrypts a type-2 EncString (AES-CBC-256 + HMAC-SHA256) using [key].
     * Verifies the MAC before decrypting — throws [SecurityException] on mismatch.
     */
    fun decrypt(encString: EncString, key: SymmetricKey): ByteArray {
        return when (encString.type) {
            0    -> decryptAesCbc(encString.iv, encString.cipherText, key.encKey)
            2    -> {
                verifyMac(encString.iv, encString.cipherText, encString.mac!!, key.macKey)
                decryptAesCbc(encString.iv, encString.cipherText, key.encKey)
            }
            else -> error("Unsupported symmetric type ${encString.type} in decrypt()")
        }
    }

    /** Decrypt directly from a raw EncString string. */
    fun decryptToString(raw: String, key: SymmetricKey): String =
        String(decrypt(EncString.parse(raw), key), Charsets.UTF_8)

    /** Decrypt or return null — safe for optional fields that may be absent. */
    fun decryptOrNull(raw: String?, key: SymmetricKey): String? {
        if (raw.isNullOrBlank()) return null
        return suspendRunCatching { decryptToString(raw, key) }.getOrNull()
    }

    /**
     * Encrypts [plaintext] bytes with [key] → type-2 EncString (AES-CBC + HMAC).
     */
    fun encrypt(plaintext: ByteArray, key: SymmetricKey): EncString {
        val iv         = ByteArray(16).also { secureRandom.nextBytes(it) }
        val cipherText = encryptAesCbc(iv, plaintext, key.encKey)
        val mac        = computeMac(iv, cipherText, key.macKey)
        return EncString(type = 2, iv = iv, cipherText = cipherText, mac = mac)
    }

    fun encryptString(plaintext: String, key: SymmetricKey): EncString =
        encrypt(plaintext.toByteArray(Charsets.UTF_8), key)

    // Protected Key Decryption

    /**
     * Decrypts the user's protected symmetric key received from the server
     * using the stretched master key. Returns the 64-byte user key.
     */
    fun decryptUserKey(encryptedKey: String, stretchedMasterKey: SymmetricKey): SymmetricKey {
        val encString = EncString.parse(encryptedKey)
        val keyBytes  = decrypt(encString, stretchedMasterKey)
        require(keyBytes.size == 64) { "Decrypted user key must be 64 bytes, got ${keyBytes.size}" }
        return SymmetricKey(keyBytes)
    }

    /**
     * Decrypts an organisation key encrypted with the user's RSA public key.
     * The RSA private key itself is stored as an EncString protected by the user key.
     */
    fun decryptOrgKey(encryptedOrgKey: String, rsaPrivateKeyBytes: ByteArray): SymmetricKey {
        val encString = EncString.parse(encryptedOrgKey)
        val keyBytes  = decryptRsa(encString.cipherText, rsaPrivateKeyBytes, encString.type)
        require(keyBytes.size == 64) { "Org key must be 64 bytes" }
        return SymmetricKey(keyBytes)
    }

    /**
     * Decrypts the RSA private key stored as an EncString in the user's vault profile.
     */
    fun decryptPrivateKey(encryptedPrivateKey: String, userKey: SymmetricKey): ByteArray {
        val encString = EncString.parse(encryptedPrivateKey)
        return decrypt(encString, userKey)
    }

    // Private Helpers

    private fun pbkdf2HmacSha256(
        key: ByteArray,
        salt: ByteArray,
        iterations: Int,
        outputLen: Int,
    ): ByteArray {
        val generator = org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator(
            org.bouncycastle.crypto.digests.SHA256Digest()
        )
        generator.init(key, salt, iterations)
        return (generator.generateDerivedParameters(outputLen * 8) as org.bouncycastle.crypto.params.KeyParameter).key
    }

    private fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        outputLen: Int,
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKiB)
            .withParallelism(parallelism)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)

        val output = ByteArray(outputLen)
        generator.generateBytes(password, output, 0, outputLen)
        return output
    }

    /**
     * HKDF-Expand (RFC 5869) using HMAC-SHA256.
     * PRK = masterKey (32 bytes, already extracted).
     */
    private fun hkdfExpand(prk: ByteArray, info: ByteArray, outputLen: Int): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(prk, HMAC_SHA256))

        val result = ByteArray(outputLen)
        var offset = 0
        var t      = ByteArray(0)
        var counter = 1

        while (offset < outputLen) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()

            val copy = minOf(t.size, outputLen - offset)
            System.arraycopy(t, 0, result, offset, copy)
            offset += copy
            counter++
        }
        return result
    }

    private fun encryptAesCbc(iv: ByteArray, plaintext: ByteArray, encKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_CBC_PKCS5)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plaintext)
    }

    private fun decryptAesCbc(iv: ByteArray, cipherText: ByteArray, encKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_CBC_PKCS5)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(cipherText)
    }

    private fun computeMac(iv: ByteArray, cipherText: ByteArray, macKey: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(macKey, HMAC_SHA256))
        mac.update(iv)
        mac.update(cipherText)
        return mac.doFinal()
    }

    private fun verifyMac(
        iv: ByteArray,
        cipherText: ByteArray,
        expectedMac: ByteArray,
        macKey: ByteArray,
    ) {
        val computed = computeMac(iv, cipherText, macKey)
        if (!MessageDigest.isEqual(computed, expectedMac)) {
            throw SecurityException("MAC verification failed — data may be tampered.")
        }
    }

    private fun decryptRsa(cipherText: ByteArray, privateKeyBytes: ByteArray, type: Int): ByteArray {
        val keySpec  = java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(keySpec)

        val padding = when (type) {
            4    -> "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"
            6    -> "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
            else -> error("Unsupported RSA type: $type")
        }
        val cipher = Cipher.getInstance(padding)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(cipherText)
    }
}

