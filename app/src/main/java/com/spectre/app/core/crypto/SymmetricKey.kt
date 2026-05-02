package com.spectre.app.core.crypto

/**
 * A Bitwarden symmetric key — always 64 bytes split evenly:
 *   bytes 0–31  → AES-256 encryption key
 *   bytes 32–63 → HMAC-SHA256 MAC key
 */
class SymmetricKey(val keyBytes: ByteArray) {

    init {
        require(keyBytes.size == 64) {
            "SymmetricKey must be 64 bytes (got ${keyBytes.size})"
        }
    }

    val encKey: ByteArray get() = keyBytes.copyOfRange(0, 32)
    val macKey: ByteArray get() = keyBytes.copyOfRange(32, 64)

    /** Wipes the key material from memory. Call when locking the vault. */
    fun destroy() = keyBytes.fill(0)

    companion object {
        /** Convenience: wrap an already-split key back into the 64-byte form. */
        fun fromSplit(encKey: ByteArray, macKey: ByteArray): SymmetricKey {
            require(encKey.size == 32 && macKey.size == 32)
            return SymmetricKey(encKey + macKey)
        }
    }
}
