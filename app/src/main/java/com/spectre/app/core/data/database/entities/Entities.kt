package com.spectre.app.core.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spectre.app.core.data.database.Converters

// ── Account ───────────────────────────────────────────────────────────────────

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val email: String,
    val name: String?,
    val serverUrl: String,
    val identityUrl: String,
    val accessToken: String,   // stored encrypted via SQLCipher
    val refreshToken: String?,
    val encryptedKey: String?,      // user's protected symmetric key
    val encryptedPrivateKey: String?,
    val kdf: Int = 0,
    val kdfIterations: Int = 600_000,
    val kdfMemory: Int? = null,
    val kdfParallelism: Int? = null,
    val premium: Boolean = false,
    val lastSync: Long = 0L,
    val isActive: Boolean = false,
)

// ── Vault Item (Cipher) ───────────────────────────────────────────────────────

@Entity(tableName = "ciphers")
@TypeConverters(Converters::class)
data class CipherEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val organizationId: String?,
    val folderId: String?,
    val type: Int,                  // 1=Login 2=SecureNote 3=Card 4=Identity
    val name: String,               // still encrypted EncString
    val notes: String?,
    val favorite: Boolean = false,
    val reprompt: Int = 0,
    val deletedDate: String?,
    val revisionDate: String,
    val creationDate: String?,
    // Login fields
    val loginUsername: String?,
    val loginPassword: String?,
    val loginPasswordRevDate: String?,
    val loginTotp: String?,
    val loginUris: String?,         // JSON array of {uri, match}
    val loginAutofillOnPageLoad: Boolean?,
    // Card fields
    val cardCardholderName: String?,
    val cardBrand: String?,
    val cardNumber: String?,
    val cardExpMonth: String?,
    val cardExpYear: String?,
    val cardCode: String?,
    // Identity fields
    val identityFirstName: String?,
    val identityLastName: String?,
    val identityEmail: String?,
    val identityPhone: String?,
    val identityAddress1: String?,
    val identityCity: String?,
    val identityPostalCode: String?,
    val identityCountry: String?,
    val identityCompany: String?,
    val identityTitle: String?,
    val identityMiddleName: String?,
    val identityAddress2: String?,
    val identityAddress3: String?,
    val identityState: String?,
    val identitySsn: String?,
    val identityUsername: String?,
    val identityPassportNumber: String?,
    val identityLicenseNumber: String?,
    // Shared data
    val fields: String?,            // JSON array
    val attachments: String?,       // JSON array
    val passwordHistory: String?,   // JSON array
    val collectionIds: String?,     // JSON array
    // Local-only metadata
    val localFaviconUri: String? = null,
    val pendingSync: Boolean = false,   // modified locally, not yet synced
    val localCreatedAt: Long = System.currentTimeMillis(),
)

// ── Folder ────────────────────────────────────────────────────────────────────

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,           // EncString
    val revisionDate: String,
    val pendingSync: Boolean = false,
)

// ── Collection ────────────────────────────────────────────────────────────────

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val organizationId: String,
    val name: String,           // EncString
    val hidePasswords: Boolean = false,
)

// ── Organisation ─────────────────────────────────────────────────────────────

@Entity(tableName = "organizations")
data class OrganizationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val encryptedKey: String?,
    val type: Int = 0,
)

// ── Send ──────────────────────────────────────────────────────────────────────

@Entity(tableName = "sends")
data class SendEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: Int,
    val name: String,
    val notes: String?,
    val key: String,
    val maxAccessCount: Int?,
    val accessCount: Int = 0,
    val expirationDate: String?,
    val deletionDate: String,
    val disabled: Boolean = false,
    val textContent: String?,
    val textHidden: Boolean = false,
    val fileId: String?,
    val fileName: String?,
    val fileSize: String?,
    val revisionDate: String,
)
