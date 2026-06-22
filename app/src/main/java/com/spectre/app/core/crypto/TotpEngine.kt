package com.spectre.app.core.crypto

import android.net.Uri
import android.util.Base64
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

data class TotpCode(
    val code: String,
    val period: Int,
    val remainingSeconds: Int,
    val progressFraction: Float,
)

data class TotpConfig(
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int       = 6,
    val period: Int       = 30,
    val issuer: String?   = null,
    val account: String?  = null,
)

@Singleton
class TotpEngine @Inject constructor() {

    /**
     * Parses an otpauth:// URI or a raw Base32 secret.
     */
    fun parseUri(uri: String): TotpConfig? {
        val trimmed = uri.trim()
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            return runCatching {
                val parsed  = Uri.parse(trimmed)
                val secret  = parsed.getQueryParameter("secret") ?: return@runCatching null
                val algo    = parsed.getQueryParameter("algorithm") ?: "SHA1"
                val digits  = parsed.getQueryParameter("digits")?.toIntOrNull() ?: 6
                val period  = parsed.getQueryParameter("period")?.toIntOrNull() ?: 30
                val issuer  = parsed.getQueryParameter("issuer")
                val account = parsed.path?.trimStart('/')
                TotpConfig(secret, algo, digits, period, issuer, account)
            }.getOrNull()
        } else {
            // Raw Base32 secret
            val secret = trimmed.replace(" ", "").uppercase()
            if (secret.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }) {
                return TotpConfig(secret = secret)
            }
        }
        return null
    }

    /**
     * Generates the current TOTP code with timing metadata.
     */
    fun generate(config: TotpConfig): TotpCode? = runCatching {
        val now      = System.currentTimeMillis() / 1000L
        val counter  = now / config.period
        val remaining = config.period - (now % config.period).toInt()
        val progress  = remaining.toFloat() / config.period.toFloat()

        val code = hotp(
            secret  = decodeBase32(config.secret),
            counter = counter,
            digits  = config.digits,
            algo    = config.algorithm,
        )

        TotpCode(
            code              = code,
            period            = config.period,
            remainingSeconds  = remaining,
            progressFraction  = progress,
        )
    }.getOrNull()

    fun generateFromUri(uri: String): TotpCode? {
        val config = parseUri(uri) ?: return null
        return generate(config)
    }

    private fun hotp(secret: ByteArray, counter: Long, digits: Int, algo: String): String {
        if (algo.uppercase() == "MD5") {
            throw IllegalArgumentException("MD5 algorithm is not supported for security reasons.")
        }

        val msg = ByteArray(8)
        for (i in 7 downTo 0) {
            msg[i] = (counter shr ((7 - i) * 8) and 0xFF).toByte()
        }

        val hmac = when (algo.uppercase()) {
            "SHA256" -> HMac(SHA256Digest())
            "SHA512" -> HMac(SHA512Digest())
            else     -> HMac(SHA1Digest())
        }
        hmac.init(KeyParameter(secret))
        hmac.update(msg, 0, msg.size)
        val hash = ByteArray(hmac.macSize)
        hmac.doFinal(hash, 0)

        if (hash.size < 20) {
            throw IllegalArgumentException("HMAC output too short for secure truncation.")
        }

        val offset = (hash.last().toInt() and 0x0F)
        if (offset + 3 >= hash.size) {
            throw IllegalArgumentException("Invalid offset for dynamic truncation.")
        }
        val code   = ((hash[offset].toInt() and 0x7F) shl 24) or
                     ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                     ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                      (hash[offset + 3].toInt() and 0xFF)

        val otp = code % (10.0).pow(digits).toInt()
        return otp.toString().padStart(digits, '0')
    }

    private fun decodeBase32(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean    = input.trim().replace(" ", "").replace("-", "").uppercase()
        var buffer   = 0L
        var bitsLeft = 0
        val result   = mutableListOf<Byte>()

        for (c in clean) {
            if (c == '=') break
            val idx = alphabet.indexOf(c)
            if (idx < 0) continue
            buffer = (buffer shl 5) or idx.toLong()
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                result.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return result.toByteArray()
    }
}

