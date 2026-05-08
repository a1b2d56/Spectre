package com.spectre.app.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Pre-login

@Serializable
data class PreLoginRequest(
    @SerialName("email") val email: String,
)

/**
 * Bitwarden returns camelCase from /accounts/prelogin.
 * All fields have defaults to survive missing/null values gracefully.
 */
@Serializable
data class PreLoginResponse(
    @SerialName("kdf")            val kdf: Int            = 0,
    @SerialName("kdfIterations")  val kdfIterations: Int  = 600_000,
    @SerialName("kdfMemory")      val kdfMemory: Int?     = null,
    @SerialName("kdfParallelism") val kdfParallelism: Int? = null,
)

// Auth tokens

@Serializable
data class TokenResponse(
    @SerialName("access_token")        val accessToken: String,
    @SerialName("expires_in")          val expiresIn: Int              = 3600,
    @SerialName("refresh_token")       val refreshToken: String?       = null,
    @SerialName("token_type")          val tokenType: String           = "Bearer",
    // These come back capitalised from /connect/token JWT payload injection
    @SerialName("Key")                 val key: String?                = null,
    @SerialName("PrivateKey")          val privateKey: String?         = null,
    // KDF params also returned in token response (camelCase)
    @SerialName("kdf")                 val kdf: Int                    = 0,
    @SerialName("kdfIterations")       val kdfIterations: Int          = 600_000,
    @SerialName("kdfMemory")           val kdfMemory: Int?             = null,
    @SerialName("kdfParallelism")      val kdfParallelism: Int?        = null,
    @SerialName("ResetMasterPassword") val resetMasterPassword: Boolean = false,
    @SerialName("TwoFactorToken")      val twoFactorToken: String?     = null,
    // 2FA error fields (returned on 400 when 2FA required)
    @SerialName("error")               val error: String?              = null,
    @SerialName("error_description")   val errorDescription: String?   = null,
    @SerialName("TwoFactorProviders2") val twoFactorProviders:
        Map<String, Map<String, String?>?>?                            = null,
)

// Sync

@Serializable
data class SyncResponse(
    @SerialName("Profile")     val profile: ProfileResponse,
    @SerialName("Folders")     val folders: List<FolderResponse>     = emptyList(),
    @SerialName("Ciphers")     val ciphers: List<CipherResponse>     = emptyList(),
    @SerialName("Collections") val collections: List<CollectionResponse> = emptyList(),
    @SerialName("Sends")       val sends: List<SendResponse>         = emptyList(),
    @SerialName("Policies")    val policies: List<PolicyResponse>    = emptyList(),
)

@Serializable
data class ProfileResponse(
    @SerialName("Id")            val id: String,
    @SerialName("Name")          val name: String?                       = null,
    @SerialName("Email")         val email: String,
    @SerialName("EmailVerified") val emailVerified: Boolean              = false,
    @SerialName("Premium")       val premium: Boolean                    = false,
    @SerialName("Key")           val key: String?                        = null,
    @SerialName("PrivateKey")    val privateKey: String?                 = null,
    @SerialName("Organizations") val organizations: List<OrganizationResponse> = emptyList(),
)

@Serializable
data class OrganizationResponse(
    @SerialName("Id")   val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Key")  val key: String? = null,
    @SerialName("Type") val type: Int    = 0,
)

@Serializable
data class FolderResponse(
    @SerialName("Id")           val id: String,
    @SerialName("Name")         val name: String,
    @SerialName("RevisionDate") val revisionDate: String,
)

@Serializable
data class CollectionResponse(
    @SerialName("Id")             val id: String,
    @SerialName("OrganizationId") val organizationId: String,
    @SerialName("Name")           val name: String,
    @SerialName("HidePasswords")  val hidePasswords: Boolean = false,
)

// Cipher

@Serializable
data class CipherResponse(
    @SerialName("Id")              val id: String,
    @SerialName("OrganizationId")  val organizationId: String?          = null,
    @SerialName("FolderId")        val folderId: String?                 = null,
    @SerialName("CollectionIds")   val collectionIds: List<String>       = emptyList(),
    @SerialName("Type")            val type: Int,
    @SerialName("Name")            val name: String,
    @SerialName("Notes")           val notes: String?                    = null,
    @SerialName("Favorite")        val favorite: Boolean                 = false,
    @SerialName("Reprompt")        val reprompt: Int                     = 0,
    @SerialName("Login")           val login: LoginResponse?             = null,
    @SerialName("Card")            val card: CardResponse?               = null,
    @SerialName("Identity")        val identity: IdentityResponse?       = null,
    @SerialName("SecureNote")      val secureNote: SecureNoteResponse?   = null,
    @SerialName("Fields")          val fields: List<FieldResponse>       = emptyList(),
    @SerialName("Attachments")     val attachments: List<AttachmentResponse> = emptyList(),
    @SerialName("PasswordHistory") val passwordHistory: List<PasswordHistoryResponse> = emptyList(),
    @SerialName("RevisionDate")    val revisionDate: String,
    @SerialName("CreationDate")    val creationDate: String?             = null,
    @SerialName("DeletedDate")     val deletedDate: String?              = null,
)

@Serializable
data class LoginResponse(
    @SerialName("Username")             val username: String?             = null,
    @SerialName("Password")             val password: String?             = null,
    @SerialName("PasswordRevisionDate") val passwordRevisionDate: String? = null,
    @SerialName("Totp")                 val totp: String?                 = null,
    @SerialName("Uris")                 val uris: List<LoginUriResponse>  = emptyList(),
    @SerialName("AutofillOnPageLoad")   val autofillOnPageLoad: Boolean?  = null,
)

@Serializable
data class LoginUriResponse(
    @SerialName("Uri")   val uri: String?  = null,
    @SerialName("Match") val match: Int?   = null,
)

@Serializable
data class CardResponse(
    @SerialName("CardholderName") val cardholderName: String? = null,
    @SerialName("Brand")          val brand: String?          = null,
    @SerialName("Number")         val number: String?         = null,
    @SerialName("ExpMonth")       val expMonth: String?       = null,
    @SerialName("ExpYear")        val expYear: String?        = null,
    @SerialName("Code")           val code: String?           = null,
)

@Serializable
data class IdentityResponse(
    @SerialName("Title")          val title: String?          = null,
    @SerialName("FirstName")      val firstName: String?      = null,
    @SerialName("MiddleName")     val middleName: String?     = null,
    @SerialName("LastName")       val lastName: String?       = null,
    @SerialName("Address1")       val address1: String?       = null,
    @SerialName("Address2")       val address2: String?       = null,
    @SerialName("Address3")       val address3: String?       = null,
    @SerialName("City")           val city: String?           = null,
    @SerialName("State")          val state: String?          = null,
    @SerialName("PostalCode")     val postalCode: String?     = null,
    @SerialName("Country")        val country: String?        = null,
    @SerialName("Company")        val company: String?        = null,
    @SerialName("Email")          val email: String?          = null,
    @SerialName("Phone")          val phone: String?          = null,
    @SerialName("Ssn")            val ssn: String?            = null,
    @SerialName("Username")       val username: String?       = null,
    @SerialName("PassportNumber") val passportNumber: String? = null,
    @SerialName("LicenseNumber")  val licenseNumber: String?  = null,
)

@Serializable
data class SecureNoteResponse(@SerialName("Type") val type: Int = 0)

@Serializable
data class FieldResponse(
    @SerialName("Type")     val type: Int,
    @SerialName("Name")     val name: String?  = null,
    @SerialName("Value")    val value: String? = null,
    @SerialName("LinkedId") val linkedId: Int? = null,
)

@Serializable
data class AttachmentResponse(
    @SerialName("Id")       val id: String,
    @SerialName("Url")      val url: String?      = null,
    @SerialName("FileName") val fileName: String? = null,
    @SerialName("Key")      val key: String?      = null,
    @SerialName("Size")     val size: String?     = null,
)

@Serializable
data class PasswordHistoryResponse(
    @SerialName("Password")     val password: String,
    @SerialName("LastUsedDate") val lastUsedDate: String,
)

// Send

@Serializable
data class SendResponse(
    @SerialName("Id")             val id: String,
    @SerialName("Type")           val type: Int,
    @SerialName("Name")           val name: String,
    @SerialName("Notes")          val notes: String?        = null,
    @SerialName("Key")            val key: String,
    @SerialName("MaxAccessCount") val maxAccessCount: Int?  = null,
    @SerialName("AccessCount")    val accessCount: Int      = 0,
    @SerialName("ExpirationDate") val expirationDate: String? = null,
    @SerialName("DeletionDate")   val deletionDate: String,
    @SerialName("Disabled")       val disabled: Boolean     = false,
    @SerialName("Text")           val text: SendTextResponse? = null,
    @SerialName("File")           val file: SendFileResponse? = null,
    @SerialName("RevisionDate")   val revisionDate: String,
)

@Serializable
data class SendTextResponse(
    @SerialName("Text")   val text: String?   = null,
    @SerialName("Hidden") val hidden: Boolean = false,
)

@Serializable
data class SendFileResponse(
    @SerialName("Id")       val id: String?       = null,
    @SerialName("FileName") val fileName: String? = null,
    @SerialName("Size")     val size: String?     = null,
)

// Policy

@Serializable
data class PolicyResponse(
    @SerialName("Id")             val id: String,
    @SerialName("OrganizationId") val organizationId: String,
    @SerialName("Type")           val type: Int,
    @SerialName("Data")           val data: Map<String, JsonElement> = emptyMap(),
    @SerialName("Enabled")        val enabled: Boolean               = false,
)

// Write models

@Serializable
data class CipherRequest(
    @SerialName("type")           val type: Int,
    @SerialName("name")           val name: String,
    @SerialName("notes")          val notes: String?         = null,
    @SerialName("favorite")       val favorite: Boolean      = false,
    @SerialName("reprompt")       val reprompt: Int          = 0,
    @SerialName("folderId")       val folderId: String?      = null,
    @SerialName("organizationId") val organizationId: String? = null,
    @SerialName("collectionIds")  val collectionIds: List<String> = emptyList(),
    @SerialName("login")          val login: LoginRequest?   = null,
    @SerialName("card")           val card: CardRequest?     = null,
    @SerialName("identity")       val identity: IdentityRequest? = null,
    @SerialName("secureNote")     val secureNote: SecureNoteRequest? = null,
    @SerialName("fields")         val fields: List<FieldRequest>  = emptyList(),
)

@Serializable
data class LoginRequest(
    @SerialName("username") val username: String?              = null,
    @SerialName("password") val password: String?              = null,
    @SerialName("totp")     val totp: String?                  = null,
    @SerialName("uris")     val uris: List<LoginUriRequest>    = emptyList(),
)

@Serializable
data class LoginUriRequest(
    @SerialName("uri")   val uri: String?  = null,
    @SerialName("match") val match: Int?   = null,
)

@Serializable
data class CardRequest(
    @SerialName("cardholderName") val cardholderName: String? = null,
    @SerialName("brand")          val brand: String?          = null,
    @SerialName("number")         val number: String?         = null,
    @SerialName("expMonth")       val expMonth: String?       = null,
    @SerialName("expYear")        val expYear: String?        = null,
    @SerialName("code")           val code: String?           = null,
)

@Serializable
data class IdentityRequest(
    @SerialName("firstName")  val firstName: String?  = null,
    @SerialName("lastName")   val lastName: String?   = null,
    @SerialName("email")      val email: String?      = null,
    @SerialName("phone")      val phone: String?      = null,
    @SerialName("address1")   val address1: String?   = null,
    @SerialName("city")       val city: String?       = null,
    @SerialName("postalCode") val postalCode: String? = null,
    @SerialName("country")    val country: String?    = null,
)

@Serializable
data class SecureNoteRequest(@SerialName("type") val type: Int = 0)

@Serializable
data class FieldRequest(
    @SerialName("type")  val type: Int,
    @SerialName("name")  val name: String?  = null,
    @SerialName("value") val value: String? = null,
)

@Serializable
data class FolderRequest(@SerialName("name") val name: String)

@Serializable
data class SendRequest(
    @SerialName("type")           val type: Int,
    @SerialName("name")           val name: String,
    @SerialName("notes")          val notes: String?        = null,
    @SerialName("key")            val key: String,
    @SerialName("maxAccessCount") val maxAccessCount: Int?  = null,
    @SerialName("expirationDate") val expirationDate: String? = null,
    @SerialName("deletionDate")   val deletionDate: String?   = null,
    @SerialName("disabled")       val disabled: Boolean     = false,
    @SerialName("text")           val text: SendTextRequest? = null,
)

@Serializable
data class SendTextRequest(
    @SerialName("text")   val text: String?   = null,
    @SerialName("hidden") val hidden: Boolean = false,
)
