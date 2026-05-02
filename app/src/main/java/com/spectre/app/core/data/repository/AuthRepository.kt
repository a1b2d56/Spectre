package com.spectre.app.core.data.repository

import com.spectre.app.core.crypto.BitwardenCrypto
import com.spectre.app.core.crypto.SymmetricKey
import com.spectre.app.core.data.database.dao.AccountDao
import com.spectre.app.core.data.database.dao.OrganizationDao
import com.spectre.app.core.data.database.entities.AccountEntity
import com.spectre.app.core.data.models.Account
import com.spectre.app.core.network.IdentityApi
import com.spectre.app.core.network.TokenStore
import com.spectre.app.core.network.model.PreLoginRequest
import com.spectre.app.core.security.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
    private val prefs: com.spectre.app.core.data.datastore.SpectrePreferences,
) {

    fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveAccount(): Flow<Account?> =
        accountDao.observeActive().map { it?.toDomain() }

    /**
     * Full login flow:
     * 1. Pre-login to fetch KDF params
     * 2. Derive master key + hash
     * 3. Authenticate with Bitwarden Identity server
     * 4. Decrypt user key + private key
     * 5. Store account + tokens in DB
     * 6. Unlock session in memory
     */
    suspend fun login(
        email: String,
        masterPassword: String,
        serverUrl: String        = "https://api.bitwarden.com",
        identityUrl: String      = "https://identity.bitwarden.com",
        twoFactorToken: String?  = null,
        twoFactorProvider: Int?  = null,
    ): LoginResult = withContext(Dispatchers.IO) {

        try {
            // Configure endpoints for self-hosted support
            tokenStore.serverUrl   = serverUrl
            tokenStore.identityUrl = identityUrl

            // 1. Fetch KDF params
            val preLoginResponse = identityApi.preLogin(PreLoginRequest(email = email))
            val preLogin = preLoginResponse.body()
                ?: return@withContext LoginResult.Error("Pre-login failed: ${preLoginResponse.code()}")

            // 2. Derive keys
            val masterKey = crypto.deriveMasterKey(
                password        = masterPassword,
                email           = email,
                kdfType         = preLogin.kdf,
                kdfIterations   = preLogin.kdfIterations,
                kdfMemory       = preLogin.kdfMemory ?: 65_536,
                kdfParallelism  = preLogin.kdfParallelism ?: 4,
            )
            val stretchedKey   = crypto.stretchMasterKey(masterKey)
            val masterPwHash   = crypto.hashMasterPassword(masterKey, masterPassword)

            // 3. Authenticate
            val deviceId = ensureDeviceId()
            val tokenResponse = identityApi.getToken(
                username           = email,
                password           = masterPwHash,
                deviceIdentifier   = deviceId,
                twoFactorToken     = twoFactorToken,
                twoFactorProvider  = twoFactorProvider,
            )

            val tokenBody = tokenResponse.body()

            if (tokenResponse.code() == 400) {
                // Parse 2FA required response
                val errorBody = tokenResponse.errorBody()?.string()
                if (errorBody?.contains("TwoFactorProviders2") == true) {
                    return@withContext LoginResult.TwoFactorRequired
                }
                return@withContext LoginResult.Error(errorBody ?: "Authentication failed")
            }

            if (!tokenResponse.isSuccessful || tokenBody == null) {
                return@withContext LoginResult.Error("Login failed: ${tokenResponse.code()}")
            }

            // 4. Decrypt user key
            val encryptedUserKey = tokenBody.key
                ?: return@withContext LoginResult.Error("No user key in token response")

            val userKey = crypto.decryptUserKey(encryptedUserKey, stretchedKey)

            // 5. Decrypt org keys
            val orgKeys = mutableMapOf<String, SymmetricKey>()
            val encPrivateKey = tokenBody.privateKey
            if (encPrivateKey != null) {
                val privateKeyBytes = crypto.decryptPrivateKey(encPrivateKey, userKey)
                // We'll decrypt org keys when we first sync and have org data
                // For now store private key bytes in session for later org key decryption
                // (handled in VaultRepository.sync via organizationDao)
            }

            // 6. Persist account
            val accountId = UUID.randomUUID().toString()
            accountDao.clearActiveFlag()
            accountDao.upsert(AccountEntity(
                id                  = accountId,
                userId              = tokenBody.accessToken.let { parseUserIdFromJwt(it) } ?: accountId,
                email               = email,
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
            ))
            prefs.setActiveAccountId(accountId)

            // 7. Populate token store + session
            tokenStore.accessToken  = tokenBody.accessToken
            tokenStore.refreshToken = tokenBody.refreshToken
            session.unlock(accountId, userKey, orgKeys)

            // Wipe sensitive key material from stack
            masterKey.fill(0)

            LoginResult.Success(accountId)

        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Unknown error during login")
        }
    }

    /**
     * Unlock an existing account after lock (biometric or master password).
     * Re-derives the user key and restores the in-memory session.
     */
    suspend fun unlockWithMasterPassword(
        accountId: String,
        masterPassword: String,
    ): UnlockResult = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getById(accountId)
                ?: return@withContext UnlockResult.Error("Account not found")

            val masterKey = crypto.deriveMasterKey(
                password       = masterPassword,
                email          = account.email,
                kdfType        = account.kdf,
                kdfIterations  = account.kdfIterations,
                kdfMemory      = account.kdfMemory ?: 65_536,
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
            }

            session.unlock(accountId, userKey)
            tokenStore.accessToken  = account.accessToken
            tokenStore.refreshToken = account.refreshToken
            tokenStore.serverUrl    = account.serverUrl
            tokenStore.identityUrl  = account.identityUrl
            masterKey.fill(0)

            UnlockResult.Success
        } catch (e: Exception) {
            UnlockResult.Error(e.message ?: "Unlock failed")
        }
    }

    suspend fun switchAccount(accountId: String) = withContext(Dispatchers.IO) {
        session.lock()
        accountDao.clearActiveFlag()
        accountDao.setActive(accountId)
        prefs.setActiveAccountId(accountId)
        // User must authenticate again after switch
        session.setAccountExists()
    }

    suspend fun signOut(accountId: String) = withContext(Dispatchers.IO) {
        session.signOut()
        accountDao.deleteById(accountId)
        prefs.setActiveAccountId(null)
    }

    private suspend fun ensureDeviceId(): String {
        var id = prefs.settings.first().deviceId
        if (id.isBlank()) {
            id = UUID.randomUUID().toString()
            prefs.setDeviceId(id)
        }
        return id
    }

    private fun parseUserIdFromJwt(token: String): String? = runCatching {
        val payload = token.split(".")[1]
        val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
        val json    = kotlinx.serialization.json.Json.parseToJsonElement(String(decoded)).jsonObject
        json["sub"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun AccountEntity.toDomain() = Account(
        id        = id,
        email     = email,
        name      = name,
        serverUrl = serverUrl,
        premium   = premium,
        lastSync  = lastSync,
        isActive  = isActive,
    )
}
