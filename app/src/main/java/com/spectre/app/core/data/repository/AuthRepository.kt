package com.spectre.app.core.data.repository

import android.util.Base64
import android.util.Log
import com.spectre.app.core.crypto.BitwardenCrypto
import com.spectre.app.core.crypto.EncString
import com.spectre.app.core.crypto.SymmetricKey
import com.spectre.app.core.data.database.dao.AccountDao
import com.spectre.app.core.data.database.dao.OrganizationDao
import com.spectre.app.core.data.database.entities.AccountEntity
import com.spectre.app.core.data.datastore.SpectrePreferences
import com.spectre.app.core.data.models.Account
import com.spectre.app.core.network.IdentityApi
import com.spectre.app.core.network.TokenStore
import com.spectre.app.core.network.model.PreLoginRequest
import com.spectre.app.core.security.VaultSession
import com.spectre.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SpectreAuth"

sealed class LoginResult {
    data class Success(val accountId: String) : LoginResult()
    object TwoFactorRequired : LoginResult()
    data class TwoFactorWith(val providers: Map<String, Map<String, String?>?>) : LoginResult()
    data class Error(val message: String) : LoginResult()
    object CaptchaRequired : LoginResult()
}

sealed class UnlockResult {
    object Success : UnlockResult()
    data class Error(val message: String) : UnlockResult()
    object InvalidPin : UnlockResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val identityApi: IdentityApi,
    private val accountDao: AccountDao,
    private val organizationDao: OrganizationDao,
    private val session: VaultSession,
    private val tokenStore: TokenStore,
    private val crypto: BitwardenCrypto,
    private val prefs: SpectrePreferences,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val secureRandom = SecureRandom()


    fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveAccount(): Flow<Account?> =
        accountDao.observeActive().map { it?.toDomain() }

    // Local Vault Creation

    /**
     * Creates a purely offline local vault. No network calls.
     * Generates a random user key, encrypts it with the master password,
     * and stores everything locally. The vault is immediately unlocked.
     */
    suspend fun createLocalVault(
        vaultName: String,
        masterPassword: String,
    ): LoginResult = withContext(ioDispatcher) {
        try {
            val accountId = UUID.randomUUID().toString()
            val email = "$vaultName@local"

            // Derive keys from master password (using PBKDF2 with 600k iterations)
            val masterKey = crypto.deriveMasterKey(
                password       = masterPassword,
                email          = email,
                kdfType        = 0,
                kdfIterations  = 600_000,
            )
            val stretchedKey = crypto.stretchMasterKey(masterKey)

            // Generate a random 64-byte user symmetric key
            val rawUserKey = ByteArray(64).also { secureRandom.nextBytes(it) }
            val userKey = SymmetricKey(rawUserKey)

            // Encrypt the user key with the stretched master key
            val encryptedUserKey = crypto.encrypt(rawUserKey, stretchedKey)

            // Clear active flag on other accounts
            accountDao.clearActiveFlag()

            // Store the local account
            accountDao.upsert(AccountEntity(
                id                  = accountId,
                userId              = accountId,
                email               = email,
                name                = vaultName,
                serverUrl           = "local",
                identityUrl         = "local",
                accessToken         = "",
                refreshToken        = null,
                encryptedKey        = encryptedUserKey.encode(),
                encryptedPrivateKey = null,
                kdf                 = 0,
                kdfIterations       = 600_000,
                kdfMemory           = null,
                kdfParallelism      = null,
                isActive            = true,
                isLocal             = true,
            ))
            prefs.setActiveAccountId(accountId)

            // Unlock the session immediately
            session.unlock(accountId, userKey)

            // Wipe sensitive bytes
            masterKey.fill(0)
            rawUserKey.fill(0)

            LoginResult.Success(accountId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create local vault", e)
            LoginResult.Error("Failed to create vault: ${e.message}")
        }
    }

    // Bitwarden Login

    /**
     * Full Bitwarden login flow:
     * 1. Set TokenStore URLs for DynamicUrlInterceptor
     * 2. Pre-login to fetch KDF params
     * 3. Derive master key + hash
     * 4. Authenticate with Bitwarden Identity server
     * 5. Decrypt user key + handle org keys
     * 6. Store account + tokens in DB
     * 7. Unlock session in memory
     */
    suspend fun login(
        email: String,
        masterPassword: String,
        serverUrl: String        = "https://api.bitwarden.com",
        identityUrl: String      = "https://identity.bitwarden.com",
        twoFactorToken: String?  = null,
        twoFactorProvider: Int?  = null,
    ): LoginResult = withContext(ioDispatcher) {
        try {
            val normalizedEmail = email.trim().lowercase()
            Log.d(TAG, "Login attempt for $normalizedEmail → $identityUrl")

            // Step 1: Configure URLs BEFORE any network call
            tokenStore.serverUrl   = serverUrl
            tokenStore.identityUrl = identityUrl

            // Step 2: Fetch KDF params
            val preLoginResponse = identityApi.preLogin(PreLoginRequest(email = normalizedEmail))
            if (!preLoginResponse.isSuccessful) {
                val errBody = preLoginResponse.errorBody()?.string() ?: ""
                Log.e(TAG, "Pre-login failed: ${preLoginResponse.code()} $errBody")
                return@withContext LoginResult.Error(
                    "Could not reach server. Check your internet connection."
                )
            }
            val preLogin = preLoginResponse.body()
                ?: return@withContext LoginResult.Error("Empty pre-login response")

            Log.d(TAG, "KDF: type=${preLogin.kdf}, iterations=${preLogin.kdfIterations}")

            // Validate pre-login KDF parameters
            if (preLogin.kdf == 0) {
                if (preLogin.kdfIterations < 600_000) {
                    return@withContext LoginResult.Error("PBKDF2 iterations must be at least 600,000; contact the server admin.")
                }
            } else if (preLogin.kdf == 1) {
                if (preLogin.kdfIterations < 2) {
                    return@withContext LoginResult.Error("Argon2 iterations must be at least 2; contact the server admin.")
                }
                val m = preLogin.kdfMemory ?: 64
                if (m < 16) {
                    return@withContext LoginResult.Error("Argon2 memory must be at least 16 MiB; contact the server admin.")
                }
                val p = preLogin.kdfParallelism ?: 4
                if (p < 1) {
                    return@withContext LoginResult.Error("Argon2 parallelism must be at least 1; contact the server admin.")
                }
            }

            // Scale Argon2id memory from MB to KB (if less than 1024, it is in MB)
            val m = preLogin.kdfMemory ?: 64
            val memoryKb = if (preLogin.kdf == 1) {
                if (m < 1024) m * 1024 else m
            } else {
                m
            }

            // Step 3: Derive keys
            val masterKey = crypto.deriveMasterKey(
                password       = masterPassword,
                email          = normalizedEmail,
                kdfType        = preLogin.kdf,
                kdfIterations  = preLogin.kdfIterations,
                kdfMemory      = memoryKb,
                kdfParallelism = preLogin.kdfParallelism ?: 4,
            )
            val stretchedKey = crypto.stretchMasterKey(masterKey)
            val masterPwHash = crypto.hashMasterPassword(masterKey, masterPassword)

            // Step 4: Authenticate
            val deviceId = ensureDeviceId()
            val authEmailHeader = Base64.encodeToString(
                normalizedEmail.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )

            Log.d(TAG, "Sending token request with deviceId=$deviceId")

            val tokenResponse = identityApi.getToken(
                authEmail         = authEmailHeader,
                username          = normalizedEmail,
                password          = masterPwHash,
                deviceIdentifier  = deviceId,
                twoFactorToken    = twoFactorToken,
                twoFactorProvider = twoFactorProvider,
            )

            val tokenBody = tokenResponse.body()

            // Check for 2FA requirement
            if (tokenResponse.code() == 400) {
                val errorBody = tokenResponse.errorBody()?.string() ?: ""
                Log.w(TAG, "400 response: $errorBody")
                return@withContext if (
                    errorBody.contains("TwoFactorProviders") ||
                    errorBody.contains("two-factor")
                ) {
                    LoginResult.TwoFactorRequired
                } else {
                    LoginResult.Error(parseErrorMessage(errorBody))
                }
            }

            if (!tokenResponse.isSuccessful || tokenBody == null) {
                val errBody = tokenResponse.errorBody()?.string() ?: ""
                Log.e(TAG, "Login failed: ${tokenResponse.code()} $errBody")
                return@withContext LoginResult.Error(parseErrorMessage(errBody))
            }

            Log.d(TAG, "Login successful, decrypting user key...")

            // Step 5: Decrypt user symmetric key
            val encryptedUserKey = tokenBody.key
                ?: return@withContext LoginResult.Error(
                    "No user key in token response. Is your account fully set up?"
                )

            val userKey = try {
                crypto.decryptUserKey(encryptedUserKey, stretchedKey)
            } catch (e: Exception) {
                Log.e(TAG, "Key decryption failed", e)
                return@withContext LoginResult.Error(
                    "Email or master password is incorrect."
                )
            }

            // Step 6: Handle org keys
            val orgKeys = mutableMapOf<String, SymmetricKey>()
            val encPrivateKey = tokenBody.privateKey
            if (encPrivateKey != null) {
                runCatching { crypto.decryptPrivateKey(encPrivateKey, userKey) }
            }

            // Step 7: Persist account
            val accountId = parseUserIdFromJwt(tokenBody.accessToken)
                ?: UUID.randomUUID().toString()

            accountDao.clearActiveFlag()
            accountDao.upsert(AccountEntity(
                id                  = accountId,
                userId              = accountId,
                email               = normalizedEmail,
                name                = null,
                serverUrl           = serverUrl,
                identityUrl         = identityUrl,
                accessToken         = tokenBody.accessToken,
                refreshToken        = tokenBody.refreshToken,
                encryptedKey        = encryptedUserKey,
                encryptedPrivateKey = tokenBody.privateKey,
                kdf                 = preLogin.kdf,
                kdfIterations       = preLogin.kdfIterations,
                kdfMemory           = preLogin.kdfMemory,
                kdfParallelism      = preLogin.kdfParallelism,
                isActive            = true,
                isLocal             = false,
            ))
            prefs.setActiveAccountId(accountId)

            // Step 8: Populate token store + unlock session
            tokenStore.accessToken  = tokenBody.accessToken
            tokenStore.refreshToken = tokenBody.refreshToken
            tokenStore.refreshCallback = {
                refreshTokens(accountId, tokenBody.refreshToken ?: "")
            }

            session.unlock(accountId, userKey, orgKeys)
            masterKey.fill(0)

            LoginResult.Success(accountId)
        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            LoginResult.Error("Connection error. Please check your internet and try again.")
        }
    }

    /**
     * Parse Bitwarden error bodies into human-friendly messages.
     */
    private fun parseErrorMessage(errorBody: String): String {
        if (errorBody.isBlank()) return "Authentication failed. Please try again."
        return try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(errorBody).jsonObject
            val desc = json["error_description"]?.jsonPrimitive?.content
            val msg  = json["Message"]?.jsonPrimitive?.content
            val err  = json["error"]?.jsonPrimitive?.content
            when {
                desc?.contains("invalid", ignoreCase = true) == true ->
                    "Email or master password is incorrect."
                desc?.contains("two factor", ignoreCase = true) == true ->
                    "Two-factor authentication is required."
                desc != null -> desc
                msg != null  -> msg
                err != null  -> err
                else -> "Authentication failed. Please try again."
            }
        } catch (_: Exception) {
            "Authentication failed. Please try again."
        }
    }

    // Sync Org Keys

    suspend fun syncOrgKeys(accountId: String, userKey: SymmetricKey) {
        withContext(ioDispatcher) {
            val account = accountDao.getById(accountId) ?: return@withContext
            val encPrivateKey = account.encryptedPrivateKey ?: return@withContext

            val privateKeyBytes = runCatching {
                crypto.decryptPrivateKey(encPrivateKey, userKey)
            }.getOrNull() ?: return@withContext

            val orgs = organizationDao.getAll(accountId)
            val orgKeys = mutableMapOf<String, SymmetricKey>()

            for (org in orgs) {
                val encOrgKey = org.encryptedKey ?: continue
                runCatching { orgKeys[org.id] = crypto.decryptOrgKey(encOrgKey, privateKeyBytes) }
            }

            if (orgKeys.isNotEmpty()) session.addOrgKeys(orgKeys)
        }
    }

    // Unlock

    suspend fun unlockWithMasterPassword(
        accountId: String,
        masterPassword: String,
    ): UnlockResult = withContext(ioDispatcher) {
        try {
            val account = accountDao.getById(accountId)
                ?: return@withContext UnlockResult.Error("Account not found")

            val m = account.kdfMemory ?: 64
            val memoryKb = if (account.kdf == 1) {
                if (m < 1024) m * 1024 else m
            } else {
                m
            }

            val masterKey = crypto.deriveMasterKey(
                password       = masterPassword,
                email          = account.email,
                kdfType        = account.kdf,
                kdfIterations  = account.kdfIterations,
                kdfMemory      = memoryKb,
                kdfParallelism = account.kdfParallelism ?: 4,
            )
            val stretchedKey = crypto.stretchMasterKey(masterKey)

            val encKey = account.encryptedKey
                ?: return@withContext UnlockResult.Error("No encrypted key stored")

            val userKey = try {
                crypto.decryptUserKey(encKey, stretchedKey)
            } catch (e: SecurityException) {
                masterKey.fill(0)
                return@withContext UnlockResult.InvalidPin
            } catch (e: Exception) {
                masterKey.fill(0)
                return@withContext UnlockResult.Error("Wrong master password.")
            }

            // Restore token store for synced accounts
            if (!account.isLocal) {
                tokenStore.accessToken  = account.accessToken
                tokenStore.refreshToken = account.refreshToken
                tokenStore.serverUrl    = account.serverUrl
                tokenStore.identityUrl  = account.identityUrl
                tokenStore.refreshCallback = {
                    refreshTokens(accountId, account.refreshToken ?: "")
                }
            }

            session.unlock(accountId, userKey)
            masterKey.fill(0)

            if (!account.isLocal) syncOrgKeys(accountId, userKey)

            UnlockResult.Success
        } catch (e: Exception) {
            UnlockResult.Error(e.message ?: "Unlock failed")
        }
    }

    // Account Management

    suspend fun switchAccount(accountId: String) = withContext(ioDispatcher) {
        session.lock()
        accountDao.clearActiveFlag()
        accountDao.setActive(accountId)
        prefs.setActiveAccountId(accountId)
        session.setAccountExists()
    }

    suspend fun signOut(accountId: String) = withContext(ioDispatcher) {
        session.signOut()
        accountDao.deleteById(accountId)
        prefs.setActiveAccountId(null)
        tokenStore.clear()
    }

    /**
     * Nuclear option: Wipes all local accounts, tokens, and resets security preferences.
     */
    suspend fun purgeAllData() = withContext(ioDispatcher) {
        Log.w(TAG, "PURGE ALL DATA TRIGGERED!")
        session.lock()
        session.signOut()
        accountDao.deleteAll()
        prefs.setActiveAccountId(null)
        prefs.setPanicPin("") // Reset panic pin after use
        tokenStore.clear()
    }

    // Private Helpers

    private suspend fun refreshTokens(accountId: String, refreshToken: String): String? {
        return try {
            val response = identityApi.refreshToken(refreshToken = refreshToken)
            val body = response.body() ?: return null
            tokenStore.accessToken  = body.accessToken
            tokenStore.refreshToken = body.refreshToken ?: refreshToken
            accountDao.updateTokens(accountId, body.accessToken, body.refreshToken ?: refreshToken)
            body.accessToken
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun ensureDeviceId(): String {
        val current = prefs.settings.first().deviceId
        if (current.isNotBlank()) return current
        val newId = UUID.randomUUID().toString()
        prefs.setDeviceId(newId)
        return newId
    }

    private fun parseUserIdFromJwt(token: String): String? = runCatching {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
        val padded  = payload + "=".repeat((4 - payload.length % 4) % 4)
        val decoded = Base64.decode(padded, Base64.URL_SAFE)
        val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(
            String(decoded)
        ).jsonObject
        jsonObj["sub"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun AccountEntity.toDomain() = Account(
        id        = id,
        email     = email,
        name      = name,
        serverUrl = serverUrl,
        premium   = premium,
        lastSync  = lastSync,
        isActive  = isActive,
        isLocal   = isLocal,
    )
}
