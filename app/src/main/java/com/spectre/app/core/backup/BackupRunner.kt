package com.spectre.app.core.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.network.WebDavClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    private val webDavClient: WebDavClient
) {
    private val random = SecureRandom()

    suspend fun runBackup(
        accountId: String,
        backupPassword: String,
        destFolderUri: String?,
        webDavUrl: String?,
        webDavUser: String?,
        webDavPassword: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val ciphers = vaultRepository.getAllDecryptedCiphers(accountId)
            if (ciphers.isEmpty()) {
                return@withContext Result.failure(Exception("No ciphers found to backup"))
            }

            // 1. Serialize to JSON
            val json = Json {
                prettyPrint = true
                encodeDefaults = true
            }
            val jsonString = json.encodeToString(ciphers)
            val jsonBytes = jsonString.encodeToByteArray()

            // 2. Gzip compress the JSON bytes
            val compressedStream = ByteArrayOutputStream()
            GZIPOutputStream(compressedStream).use { gzip ->
                gzip.write(jsonBytes)
            }
            val compressedBytes = compressedStream.toByteArray()

            // 3. Encrypt using AES-256-GCM
            val salt = ByteArray(16)
            random.nextBytes(salt)
            val iv = ByteArray(12)
            random.nextBytes(iv)

            // Derive key from password
            val pbkdf2Factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(backupPassword.toCharArray(), salt, 100000, 256)
            val secretKeyBytes = pbkdf2Factory.generateSecret(spec).encoded
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val cipherText = cipher.doFinal(compressedBytes)

            // 4. Assemble backup payload: SALT (16 bytes) + IV (12 bytes) + CIPHERTEXT
            val backupPayload = ByteArrayOutputStream().use { bos ->
                bos.write(salt)
                bos.write(iv)
                bos.write(cipherText)
                bos.toByteArray()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "spectre_backup_$timestamp.enc"
            var successMessage = ""

            // 5. Write to local storage (if URI provided)
            if (!destFolderUri.isNullOrEmpty()) {
                val folderUri = Uri.parse(destFolderUri)
                val pickedDir = DocumentFile.fromTreeUri(context, folderUri)
                    ?: throw IOException("Failed to access local backup directory")
                val backupFile = pickedDir.createFile("application/octet-stream", fileName)
                    ?: throw IOException("Failed to create local backup file")
                
                context.contentResolver.openOutputStream(backupFile.uri)?.use { os ->
                    os.write(backupPayload)
                } ?: throw IOException("Failed to open output stream for local backup file")
                successMessage += "Local backup saved. "
            }

            // 6. Write to WebDAV (if credentials provided)
            if (!webDavUrl.isNullOrEmpty() && !webDavUser.isNullOrEmpty() && !webDavPassword.isNullOrEmpty()) {
                webDavClient.uploadFile(
                    baseUrl = webDavUrl,
                    username = webDavUser,
                    password = webDavPassword,
                    fileName = fileName,
                    fileBytes = backupPayload
                ).getOrThrow()
                successMessage += "WebDAV cloud backup uploaded."
            }

            if (successMessage.isEmpty()) {
                throw Exception("No backup destinations configured")
            }

            successMessage.trim()
        }
    }
}
