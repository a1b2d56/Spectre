package com.spectre.app.core.sshagent

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.math.BigInteger
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.PublicKeyFactory
import org.bouncycastle.util.encoders.Base64
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec

object SshKeyHelper {

    data class ParsedKey(
        val publicKeyBytes: ByteArray,
        val typeString: String,
        val comment: String,
        val privateKey: Any // Either java.security.PrivateKey or Ed25519PrivateKeyParameters
    ) {
        val fingerprint: String
            get() {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(publicKeyBytes)
                val base64 = Base64.toBase64String(hash).trimEnd('=')
                return "SHA256:$base64"
            }

        val authorizedKey: String
            get() = "$typeString ${Base64.toBase64String(publicKeyBytes)} $comment"
    }

    fun findPrivateKeyInText(text: String): String? {
        val startHeader = "-----BEGIN"
        val endHeader = "-----END"
        val startIndex = text.indexOf(startHeader)
        if (startIndex == -1) return null
        
        val endIndex = text.indexOf(endHeader, startIndex)
        if (endIndex == -1) return null
        
        val nextNewline = text.indexOf("\n", endIndex)
        val finalIndex = if (nextNewline == -1) text.length else nextNewline
        return text.substring(startIndex, finalIndex).trim()
    }

    fun parsePrivateKey(pemText: String, comment: String = "spectre-key"): ParsedKey? {
        return runCatching {
            val reader = StringReader(pemText)
            val pemParser = PEMParser(reader)
            val parsedObject = pemParser.readObject() ?: return null
            
            val converter = JcaPEMKeyConverter()
            
            // Check if it's an OpenSSH v1 format (PKCS#8 or OpenSSH block)
            if (parsedObject is PrivateKeyInfo) {
                val privateKey = converter.getPrivateKey(parsedObject)
                return parseStandardKey(privateKey, comment)
            }
            
            // Standard PEM KeyPair
            val keyPair = converter.getKeyPair(parsedObject as? org.bouncycastle.openssl.PEMKeyPair)
            if (keyPair != null) {
                return parseStandardKey(keyPair.private, comment)
            }
            
            // Ed25519 OpenSSH direct private key (Bouncy Castle representation)
            if (pemText.contains("ENCRYPTED")) {
                return null // Encrypted keys not supported in this inline version yet
            }

            // Raw OpenSSH format parser
            if (pemText.contains("OPENSSH PRIVATE KEY")) {
                return parseOpenSshV1Key(pemText, comment)
            }

            null
        }.getOrNull()
    }

    private fun parseStandardKey(privateKey: PrivateKey, comment: String): ParsedKey? {
        if (privateKey is RSAPrivateCrtKey) {
            val kf = KeyFactory.getInstance("RSA")
            val pubSpec = RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)
            val publicKey = kf.generatePublic(pubSpec) as RSAPublicKey
            val pubBytes = encodeSshRsa(publicKey.publicExponent, publicKey.modulus)
            return ParsedKey(pubBytes, "ssh-rsa", comment, privateKey)
        }
        return null
    }

    private fun parseOpenSshV1Key(pemText: String, comment: String): ParsedKey? {
        // Parse Ed25519 or RSA from OpenSSH v1 raw block
        val clean = pemText.lines()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        val bytes = Base64.decode(clean)
        
        // OpenSSH magic check
        val magic = "openssh-key-v1\u0000".encodeToByteArray()
        if (bytes.size < magic.size || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            return null
        }
        
        // Read buffer
        val buffer = ByteBufferReader(bytes, magic.size)
        val cipherName = buffer.readString()
        val kdfName = buffer.readString()
        buffer.readBytes() // kdfOptions
        val numKeys = buffer.readInt()
        if (numKeys <= 0) return null
        
        val pubKeyBytes = buffer.readBytes() // Public key block
        val privKeyBytes = buffer.readBytes() // Encrypted/Decrypted private key block
        
        if (cipherName != "none") {
            return null // Encrypted private keys not supported in this version
        }
        
        // Read decrypted private key parameters
        val privBuffer = ByteBufferReader(privKeyBytes, 0)
        val checkInt1 = privBuffer.readInt()
        val checkInt2 = privBuffer.readInt()
        if (checkInt1 != checkInt2) return null // Padding check
        
        val keyType = privBuffer.readString()
        if (keyType == "ssh-ed25519") {
            val pubBytes = privBuffer.readBytes() // pubkey
            val privBytes = privBuffer.readBytes() // privkey (64 bytes: 32 private + 32 public)
            val keyComment = privBuffer.readString().ifEmpty { comment }
            
            val seed = privBytes.copyOfRange(0, 32)
            val privParam = Ed25519PrivateKeyParameters(seed, 0)
            
            return ParsedKey(pubKeyBytes, "ssh-ed25519", keyComment, privParam)
        } else if (keyType == "ssh-rsa") {
            val n = privBuffer.readBigInt()
            val e = privBuffer.readBigInt()
            val d = privBuffer.readBigInt()
            val iqmp = privBuffer.readBigInt()
            val p = privBuffer.readBigInt()
            val q = privBuffer.readBigInt()
            val keyComment = privBuffer.readString().ifEmpty { comment }
            
            val kf = KeyFactory.getInstance("RSA")
            val pubSpec = RSAPublicKeySpec(n, e)
            val publicKey = kf.generatePublic(pubSpec) as RSAPublicKey
            val privateKey = kf.generatePrivate(java.security.spec.RSAPrivateCrtKeySpec(n, e, d, p, q, d.mod(p.subtract(BigInteger.ONE)), d.mod(q.subtract(BigInteger.ONE)), iqmp))
            
            return ParsedKey(pubKeyBytes, "ssh-rsa", keyComment, privateKey)
        }
        
        return null
    }

    fun sign(key: ParsedKey, data: ByteArray): ByteArray {
        if (key.privateKey is PrivateKey) {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(key.privateKey)
            signature.update(data)
            return signature.sign()
        } else if (key.privateKey is Ed25519PrivateKeyParameters) {
            val signer = Ed25519Signer()
            signer.init(true, key.privateKey)
            signer.update(data, 0, data.size)
            return signer.generateSignature()
        }
        throw IllegalArgumentException("Unsupported private key type for signing")
    }

    private fun encodeSshRsa(e: BigInteger, n: BigInteger): ByteArray {
        val bos = ByteArrayOutputStream()
        writeString(bos, "ssh-rsa")
        writeBigInt(bos, e)
        writeBigInt(bos, n)
        return bos.toByteArray()
    }

    private fun writeString(bos: ByteArrayOutputStream, str: String) {
        val bytes = str.encodeToByteArray()
        writeInt(bos, bytes.size)
        bos.write(bytes)
    }

    private fun writeBigInt(bos: ByteArrayOutputStream, valInt: BigInteger) {
        val bytes = valInt.toByteArray()
        writeInt(bos, bytes.size)
        bos.write(bytes)
    }

    private fun writeInt(bos: ByteArrayOutputStream, v: Int) {
        bos.write((v ushr 24) and 0xFF)
        bos.write((v ushr 16) and 0xFF)
        bos.write((v ushr 8) and 0xFF)
        bos.write(v and 0xFF)
    }

    private class ByteBufferReader(private val bytes: ByteArray, var pos: Int) {
        fun readInt(): Int {
            val v = ((bytes[pos].toInt() and 0xFF) ushr 24) or
                    ((bytes[pos+1].toInt() and 0xFF) ushr 16) or
                    ((bytes[pos+2].toInt() and 0xFF) ushr 8) or
                    (bytes[pos+3].toInt() and 0xFF)
            val valInt = ((bytes[pos].toInt() and 0xFF) shl 24) or
                    ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            return valInt
        }

        fun readBytes(): ByteArray {
            val len = readInt()
            val result = bytes.copyOfRange(pos, pos + len)
            pos += len
            return result
        }

        fun readString(): String {
            return readBytes().decodeToString()
        }

        fun readBigInt(): BigInteger {
            return BigInteger(readBytes())
        }
    }
}
