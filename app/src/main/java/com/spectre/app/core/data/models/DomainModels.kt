package com.spectre.app.core.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ── Cipher (decrypted vault item) ─────────────────────────────────────────────

enum class CipherType(val value: Int) {
    LOGIN(1), SECURE_NOTE(2), CARD(3), IDENTITY(4);
    companion object { fun fromInt(v: Int) = entries.firstOrNull { it.value == v } ?: LOGIN }
}

enum class UriMatchType(val value: Int) {
    DOMAIN(0), HOST(1), STARTS_WITH(2), EXACT(3), REGEX(4), NEVER(5);
    companion object { fun fromInt(v: Int?) = entries.firstOrNull { it.value == v } ?: DOMAIN }
}

@Parcelize
data class LoginUri(
    val uri: String?,
    val match: UriMatchType = UriMatchType.DOMAIN,
) : Parcelable

@Parcelize
data class LoginData(
    val username: String?,
    val password: String?,
    val passwordRevisionDate: String?,
    val totp: String?,
    val uris: List<LoginUri> = emptyList(),
    val autofillOnPageLoad: Boolean? = null,
) : Parcelable

@Parcelize
data class CardData(
    val cardholderName: String?,
    val brand: String?,
    val number: String?,
    val expMonth: String?,
    val expYear: String?,
    val code: String?,
) : Parcelable

@Parcelize
data class IdentityData(
    val title: String?,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val address1: String?,
    val address2: String?,
    val address3: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?,
    val company: String?,
    val email: String?,
    val phone: String?,
    val ssn: String?,
    val username: String?,
    val passportNumber: String?,
    val licenseNumber: String?,
) : Parcelable

@Parcelize
data class CipherField(
    val type: Int,
    val name: String?,
    val value: String?,
    val linkedId: Int? = null,
) : Parcelable

@Parcelize
data class CipherAttachment(
    val id: String,
    val url: String?,
    val fileName: String?,
    val size: String?,
) : Parcelable

@Parcelize
data class PasswordHistoryEntry(
    val password: String,
    val lastUsedDate: String,
) : Parcelable

@Parcelize
data class DecryptedCipher(
    val id: String,
    val accountId: String,
    val organizationId: String?,
    val folderId: String?,
    val collectionIds: List<String> = emptyList(),
    val type: CipherType,
    val name: String,
    val notes: String?,
    val favorite: Boolean = false,
    val reprompt: Boolean = false,
    val deletedDate: String?,
    val revisionDate: String,
    val creationDate: String?,
    val loginData: LoginData?,
    val cardData: CardData?,
    val identityData: IdentityData?,
    val fields: List<CipherField> = emptyList(),
    val attachments: List<CipherAttachment> = emptyList(),
    val passwordHistory: List<PasswordHistoryEntry> = emptyList(),
    // Computed / local
    val passwordStrength: PasswordStrength? = null,
    val isBreached: Boolean = false,
    val localFaviconUri: String? = null,
) : Parcelable {
    val primaryUsername: String?
        get() = loginData?.username ?: identityData?.let {
            listOfNotNull(it.firstName, it.lastName).joinToString(" ").takeIf { s -> s.isNotBlank() }
        }

    val subtitle: String?
        get() = when (type) {
            CipherType.LOGIN       -> loginData?.username
            CipherType.CARD        -> loginData?.username ?: cardData?.brand?.let { brand ->
                val num = cardData.number ?: return@let null
                "$brand •••• ${num.takeLast(4)}"
            }
            CipherType.IDENTITY    -> identityData?.let {
                listOfNotNull(it.firstName, it.lastName).joinToString(" ").takeIf { s -> s.isNotBlank() }
            }
            CipherType.SECURE_NOTE -> null
        }

    val primaryUri: String?
        get() = loginData?.uris?.firstOrNull()?.uri

    val isInTrash: Boolean get() = deletedDate != null
}

// ── Password strength ─────────────────────────────────────────────────────────

enum class PasswordStrength(val score: Int, val label: String) {
    VERY_WEAK(0, "Very weak"),
    WEAK(1, "Weak"),
    FAIR(2, "Fair"),
    STRONG(3, "Strong"),
    VERY_STRONG(4, "Very strong");

    companion object {
        fun fromScore(score: Int) = entries.firstOrNull { it.score == score } ?: VERY_WEAK
    }
}

// ── Folder ────────────────────────────────────────────────────────────────────

data class DecryptedFolder(
    val id: String,
    val name: String,
    val revisionDate: String,
)

// ── Send ──────────────────────────────────────────────────────────────────────

data class DecryptedSend(
    val id: String,
    val type: Int,
    val name: String,
    val notes: String?,
    val maxAccessCount: Int?,
    val accessCount: Int,
    val expirationDate: String?,
    val deletionDate: String,
    val disabled: Boolean,
    val textContent: String?,
    val textHidden: Boolean,
    val fileName: String?,
    val revisionDate: String,
)

// ── Watchtower ────────────────────────────────────────────────────────────────

data class WatchtowerReport(
    val weakPasswords: List<DecryptedCipher>   = emptyList(),
    val reusedPasswords: List<DecryptedCipher> = emptyList(),
    val exposedPasswords: List<DecryptedCipher> = emptyList(),
    val oldPasswords: List<DecryptedCipher>    = emptyList(),    // > 1 year
    val noTotp: List<DecryptedCipher>          = emptyList(),
    val inactiveCiphers: List<DecryptedCipher> = emptyList(),   // > 2 years unused
    val insecureUrls: List<DecryptedCipher>    = emptyList(),   // http:// uris
    val totalScore: Int                        = 100,
)

// ── Account ───────────────────────────────────────────────────────────────────

data class Account(
    val id: String,
    val email: String,
    val name: String?,
    val serverUrl: String,
    val premium: Boolean,
    val lastSync: Long,
    val isActive: Boolean,
)
