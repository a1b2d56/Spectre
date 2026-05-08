package com.spectre.app.core.crypto

import android.util.Base64
import com.spectre.app.core.utils.suspendRunCatching

/**
 * Represents a Bitwarden EncString — the wire format for all encrypted vault data.
 *
 * Format: "{type}.{base64_iv}|{base64_ciphertext}[|{base64_mac}]"
 *
 * Supported types:
 *   0 = AES-CBC-256 (no MAC — legacy, avoid)
 *   2 = AES-CBC-256 + HMAC-SHA256 (standard for symmetric cipher keys)
 *   4 = RSA-OAEP-SHA1 (org key encryption)
 *   6 = RSA-OAEP-SHA256
 */
data class EncString(
    val type: Int,
    val iv: ByteArray,
    val cipherText: ByteArray,
    val mac: ByteArray? = null,
) {
    companion object {
        fun parse(raw: String): EncString {
            val dotIdx = raw.indexOf('.')
            require(dotIdx > 0) { "Invalid EncString — missing type prefix: $raw" }

            val type = raw.substring(0, dotIdx).toIntOrNull()
                ?: error("Invalid EncString type: ${raw.substring(0, dotIdx)}")

            val payload = raw.substring(dotIdx + 1)

            return when (type) {
                // AES-CBC (no MAC)
                0 -> {
                    val parts = payload.split("|")
                    require(parts.size == 2) { "Type-0 EncString must have 2 parts" }
                    EncString(
                        type       = type,
                        iv         = Base64.decode(parts[0], Base64.DEFAULT),
                        cipherText = Base64.decode(parts[1], Base64.DEFAULT),
                    )
                }
                // AES-CBC + HMAC-SHA256 (standard symmetric)
                2 -> {
                    val parts = payload.split("|")
                    require(parts.size == 3) { "Type-2 EncString must have 3 parts" }
                    EncString(
                        type       = type,
                        iv         = Base64.decode(parts[0], Base64.DEFAULT),
                        cipherText = Base64.decode(parts[1], Base64.DEFAULT),
                        mac        = Base64.decode(parts[2], Base64.DEFAULT),
                    )
                }
                // RSA-OAEP (types 4 & 6) — no IV, just ciphertext
                4, 6 -> {
                    val parts = payload.split("|")
                    EncString(
                        type       = type,
                        iv         = ByteArray(0),
                        cipherText = Base64.decode(parts[0], Base64.DEFAULT),
                    )
                }
                else -> error("Unsupported EncString type: $type")
            }
        }

        /** Returns null instead of throwing for optional fields. */
        fun parseOrNull(raw: String?): EncString? = raw?.let {
            suspendRunCatching { parse(it) }.getOrNull()
        }
    }

    fun encode(): String {
        val ivB64  = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64  = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        val macB64 = mac?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

        return buildString {
            append(type)
            append('.')
            append(ivB64)
            append('|')
            append(ctB64)
            if (macB64 != null) {
                append('|')
                append(macB64)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncString) return false
        return type == other.type &&
                iv.contentEquals(other.iv) &&
                cipherText.contentEquals(other.cipherText) &&
                (mac == null && other.mac == null || mac != null && other.mac != null && mac.contentEquals(other.mac))
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + cipherText.contentHashCode()
        result = 31 * result + (mac?.contentHashCode() ?: 0)
        return result
    }
}

