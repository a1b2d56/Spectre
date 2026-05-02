package com.spectre.app.core.data.repository

import com.spectre.app.core.crypto.BitwardenCrypto
import com.spectre.app.core.crypto.EncString
import com.spectre.app.core.data.database.dao.*
import com.spectre.app.core.data.database.entities.*
import com.spectre.app.core.data.models.*
import com.spectre.app.core.network.VaultApi
import com.spectre.app.core.network.model.*
import com.spectre.app.core.security.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val vaultApi: VaultApi,
    private val cipherDao: CipherDao,
    private val folderDao: FolderDao,
    private val collectionDao: CollectionDao,
    private val organizationDao: OrganizationDao,
    private val sendDao: SendDao,
    private val session: VaultSession,
    private val crypto: BitwardenCrypto,
    private val json: Json,
) {

    // ── Observe decrypted vault ───────────────────────────────────────────────

    fun observeAllCiphers(): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeAll(accountId).map { entities ->
            entities.mapNotNull { decryptCipher(it) }
        }.flowOn(Dispatchers.Default)
    }

    fun observeFavorites(): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeFavorites(accountId).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(Dispatchers.Default)
    }

    fun observeByType(type: CipherType): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeByType(accountId, type.value).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(Dispatchers.Default)
    }

    fun observeTrash(): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeTrash(accountId).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(Dispatchers.Default)
    }

    fun observeFolder(folderId: String): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeByFolder(accountId, folderId).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(Dispatchers.Default)
    }

    fun search(query: String): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.search(accountId, query).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(Dispatchers.Default)
    }

    fun observeById(id: String): Flow<DecryptedCipher?> =
        cipherDao.observeById(id).map { it?.let { decryptCipher(it) } }.flowOn(Dispatchers.Default)

    fun observeFolders(): Flow<List<DecryptedFolder>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return folderDao.observeAll(accountId).map { list ->
            list.mapNotNull { decryptFolder(it) }
        }.flowOn(Dispatchers.Default)
    }

    // ── Full sync ─────────────────────────────────────────────────────────────

    suspend fun sync(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = vaultApi.sync()
            val body     = response.body() ?: error("Empty sync response (${response.code()})")

            // Store orgs for key lookup
            organizationDao.deleteAllForAccount(accountId)
            organizationDao.upsertAll(body.profile.organizations.map { org ->
                OrganizationEntity(
                    id           = org.id,
                    accountId    = accountId,
                    name         = org.name,
                    encryptedKey = org.key,
                    type         = org.type,
                )
            })

            // Folders
            folderDao.deleteAllForAccount(accountId)
            folderDao.upsertAll(body.folders.map { it.toEntity(accountId) })

            // Collections
            collectionDao.deleteAllForAccount(accountId)
            collectionDao.upsertAll(body.collections.map { it.toEntity(accountId) })

            // Ciphers
            cipherDao.deleteAllForAccount(accountId)
            cipherDao.upsertAll(body.ciphers.map { it.toEntity(accountId) })

            // Sends
            sendDao.deleteAllForAccount(accountId)
            sendDao.upsertAll(body.sends.map { it.toEntity(accountId) })
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    suspend fun createCipher(cipher: DecryptedCipher): Result<DecryptedCipher> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request  = encryptCipherRequest(cipher)
                val response = vaultApi.createCipher(request)
                val body     = response.body() ?: error("Create cipher failed (${response.code()})")
                val entity   = body.toEntity(cipher.accountId)
                cipherDao.upsert(entity)
                decryptCipher(entity) ?: error("Failed to decrypt created cipher")
            }
        }

    suspend fun updateCipher(cipher: DecryptedCipher): Result<DecryptedCipher> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request  = encryptCipherRequest(cipher)
                val response = vaultApi.updateCipher(cipher.id, request)
                val body     = response.body() ?: error("Update cipher failed (${response.code()})")
                val entity   = body.toEntity(cipher.accountId)
                cipherDao.upsert(entity)
                decryptCipher(entity) ?: error("Failed to decrypt updated cipher")
            }
        }

    suspend fun deleteCipher(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            vaultApi.softDeleteCipher(id)
            cipherDao.softDelete(id, java.time.Instant.now().toString())
        }
    }

    suspend fun permanentlyDeleteCipher(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            vaultApi.deleteCipher(id)
            cipherDao.hardDelete(id)
        }
    }

    suspend fun restoreCipher(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            vaultApi.restoreCipher(id)
            cipherDao.restore(id)
        }
    }

    suspend fun toggleFavorite(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            vaultApi.toggleFavorite(id)
            cipherDao.toggleFavorite(id)
        }
    }

    // ── Folder CRUD ───────────────────────────────────────────────────────────

    suspend fun createFolder(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val accountId = session.activeAccountId ?: error("No active account")
            val userKey   = session.getUserKey()
            val encName   = crypto.encryptString(name, userKey).encode()
            val response  = vaultApi.createFolder(FolderRequest(name = encName))
            val body      = response.body() ?: error("Create folder failed")
            folderDao.upsert(body.toEntity(accountId))
        }
    }

    suspend fun deleteFolder(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            vaultApi.deleteFolder(id)
            folderDao.deleteById(id)
        }
    }

    // ── Watchtower ────────────────────────────────────────────────────────────

    suspend fun buildWatchtowerReport(accountId: String): WatchtowerReport =
        withContext(Dispatchers.Default) {
            val allLogins = cipherDao.getAllLoginCiphers(accountId)
                .mapNotNull { decryptCipher(it) }
                .filter { !it.isInTrash }

            val passwords  = allLogins.mapNotNull { it.loginData?.password }.filter { it.isNotBlank() }
            val pwCounts   = passwords.groupingBy { it }.eachCount()

            val weakPasswords    = allLogins.filter { isWeakPassword(it.loginData?.password) }
            val reusedPasswords  = allLogins.filter { (pwCounts[it.loginData?.password] ?: 0) > 1 }
            val oldPasswords     = allLogins.filter { isOldPassword(it.loginData?.passwordRevisionDate, 365) }
            val noTotp           = allLogins.filter { it.loginData?.totp.isNullOrBlank() }
            val insecureUrls     = allLogins.filter { cipher ->
                cipher.loginData?.uris?.any { it.uri?.startsWith("http://") == true } == true
            }

            val totalIssues = weakPasswords.size + reusedPasswords.size + (oldPasswords.size / 2)
            val totalScore  = maxOf(0, 100 - (totalIssues * 5))

            WatchtowerReport(
                weakPasswords   = weakPasswords,
                reusedPasswords = reusedPasswords,
                oldPasswords    = oldPasswords,
                noTotp          = noTotp,
                insecureUrls    = insecureUrls,
                totalScore      = totalScore,
            )
        }

    // ── Decrypt helpers ───────────────────────────────────────────────────────

    private fun decryptCipher(entity: CipherEntity): DecryptedCipher? = runCatching {
        val key = if (entity.organizationId != null) {
            session.getOrgKey(entity.organizationId) ?: session.getUserKey()
        } else {
            session.getUserKey()
        }

        val decName  = crypto.decryptOrNull(entity.name, key)  ?: return null
        val decNotes = crypto.decryptOrNull(entity.notes, key)
        val type     = CipherType.fromInt(entity.type)

        val loginData = if (type == CipherType.LOGIN) LoginData(
            username             = crypto.decryptOrNull(entity.loginUsername, key),
            password             = crypto.decryptOrNull(entity.loginPassword, key),
            passwordRevisionDate = entity.loginPasswordRevDate,
            totp                 = crypto.decryptOrNull(entity.loginTotp, key),
            uris                 = parseUris(entity.loginUris, key),
            autofillOnPageLoad   = entity.loginAutofillOnPageLoad,
        ) else null

        val cardData = if (type == CipherType.CARD) CardData(
            cardholderName = crypto.decryptOrNull(entity.cardCardholderName, key),
            brand          = crypto.decryptOrNull(entity.cardBrand, key),
            number         = crypto.decryptOrNull(entity.cardNumber, key),
            expMonth       = crypto.decryptOrNull(entity.cardExpMonth, key),
            expYear        = crypto.decryptOrNull(entity.cardExpYear, key),
            code           = crypto.decryptOrNull(entity.cardCode, key),
        ) else null

        val identityData = if (type == CipherType.IDENTITY) IdentityData(
            title          = crypto.decryptOrNull(entity.identityTitle, key),
            firstName      = crypto.decryptOrNull(entity.identityFirstName, key),
            middleName     = crypto.decryptOrNull(entity.identityMiddleName, key),
            lastName       = crypto.decryptOrNull(entity.identityLastName, key),
            address1       = crypto.decryptOrNull(entity.identityAddress1, key),
            address2       = crypto.decryptOrNull(entity.identityAddress2, key),
            address3       = crypto.decryptOrNull(entity.identityAddress3, key),
            city           = crypto.decryptOrNull(entity.identityCity, key),
            state          = crypto.decryptOrNull(entity.identityState, key),
            postalCode     = crypto.decryptOrNull(entity.identityPostalCode, key),
            country        = crypto.decryptOrNull(entity.identityCountry, key),
            company        = crypto.decryptOrNull(entity.identityCompany, key),
            email          = crypto.decryptOrNull(entity.identityEmail, key),
            phone          = crypto.decryptOrNull(entity.identityPhone, key),
            ssn            = crypto.decryptOrNull(entity.identitySsn, key),
            username       = crypto.decryptOrNull(entity.identityUsername, key),
            passportNumber = crypto.decryptOrNull(entity.identityPassportNumber, key),
            licenseNumber  = crypto.decryptOrNull(entity.identityLicenseNumber, key),
        ) else null

        val strength = loginData?.password?.let { computePasswordStrength(it) }

        DecryptedCipher(
            id              = entity.id,
            accountId       = entity.accountId,
            organizationId  = entity.organizationId,
            folderId        = entity.folderId,
            collectionIds   = entity.collectionIds?.split(",") ?: emptyList(),
            type            = type,
            name            = decName,
            notes           = decNotes,
            favorite        = entity.favorite,
            reprompt        = entity.reprompt == 1,
            deletedDate     = entity.deletedDate,
            revisionDate    = entity.revisionDate,
            creationDate    = entity.creationDate,
            loginData       = loginData,
            cardData        = cardData,
            identityData    = identityData,
            passwordStrength = strength,
            localFaviconUri = entity.localFaviconUri,
        )
    }.getOrNull()

    private fun decryptFolder(entity: FolderEntity): DecryptedFolder? = runCatching {
        val key  = session.getUserKey()
        val name = crypto.decryptOrNull(entity.name, key) ?: return null
        DecryptedFolder(id = entity.id, name = name, revisionDate = entity.revisionDate)
    }.getOrNull()

    private fun parseUris(raw: String?, key: com.spectre.app.core.crypto.SymmetricKey): List<LoginUri> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.map { element ->
                val obj   = element.jsonObject
                val uri   = obj["uri"]?.jsonPrimitive?.content?.let { crypto.decryptOrNull(it, key) }
                val match = obj["match"]?.jsonPrimitive?.intOrNull
                LoginUri(uri = uri, match = UriMatchType.fromInt(match))
            }
        }.getOrDefault(emptyList())
    }

    // ── Encrypt helpers ───────────────────────────────────────────────────────

    private fun encryptCipherRequest(cipher: DecryptedCipher): CipherRequest {
        val key = session.getUserKey()
        fun enc(s: String?) = s?.let { crypto.encryptString(it, key).encode() }

        val loginReq = cipher.loginData?.let { l ->
            val urisJson = json.encodeToString(l.uris.map { u ->
                buildJsonObject {
                    put("uri", enc(u.uri)?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("match", u.match.value)
                }
            })
            LoginRequest(
                username = enc(l.username),
                password = enc(l.password),
                totp     = enc(l.totp),
                uris     = l.uris.map { u -> LoginUriRequest(uri = enc(u.uri), match = u.match.value) },
            )
        }

        return CipherRequest(
            type         = cipher.type.value,
            name         = enc(cipher.name) ?: "",
            notes        = enc(cipher.notes),
            favorite     = cipher.favorite,
            reprompt     = if (cipher.reprompt) 1 else 0,
            folderId     = cipher.folderId,
            organizationId = cipher.organizationId,
            collectionIds  = cipher.collectionIds,
            login        = loginReq,
            card         = cipher.cardData?.let { c ->
                CardRequest(
                    cardholderName = enc(c.cardholderName),
                    brand          = enc(c.brand),
                    number         = enc(c.number),
                    expMonth       = enc(c.expMonth),
                    expYear        = enc(c.expYear),
                    code           = enc(c.code),
                )
            },
            identity = cipher.identityData?.let { i ->
                IdentityRequest(
                    firstName  = enc(i.firstName),
                    lastName   = enc(i.lastName),
                    email      = enc(i.email),
                    phone      = enc(i.phone),
                    address1   = enc(i.address1),
                    city       = enc(i.city),
                    postalCode = enc(i.postalCode),
                    country    = enc(i.country),
                )
            },
            secureNote = if (cipher.type == CipherType.SECURE_NOTE) SecureNoteRequest() else null,
        )
    }

    // ── Password utilities ────────────────────────────────────────────────────

    private fun computePasswordStrength(password: String): PasswordStrength {
        if (password.length < 6) return PasswordStrength.VERY_WEAK
        var score = 0
        if (password.length >= 8)  score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return PasswordStrength.fromScore(minOf(score - 1, 4).coerceAtLeast(0))
    }

    private fun isWeakPassword(password: String?): Boolean {
        if (password.isNullOrBlank()) return true
        return computePasswordStrength(password).score <= 1
    }

    private fun isOldPassword(revisionDate: String?, daysThreshold: Int): Boolean {
        if (revisionDate.isNullOrBlank()) return true
        return runCatching {
            val date = java.time.Instant.parse(revisionDate)
            val now  = java.time.Instant.now()
            java.time.Duration.between(date, now).toDays() > daysThreshold
        }.getOrDefault(false)
    }
}

// ── Entity mappers ────────────────────────────────────────────────────────────

private fun FolderResponse.toEntity(accountId: String) = FolderEntity(
    id           = id,
    accountId    = accountId,
    name         = name,
    revisionDate = revisionDate,
)

private fun CollectionResponse.toEntity(accountId: String) = CollectionEntity(
    id             = id,
    accountId      = accountId,
    organizationId = organizationId,
    name           = name,
    hidePasswords  = hidePasswords,
)

private fun SendResponse.toEntity(accountId: String) = SendEntity(
    id             = id,
    accountId      = accountId,
    type           = type,
    name           = name,
    notes          = notes,
    key            = key,
    maxAccessCount = maxAccessCount,
    accessCount    = accessCount,
    expirationDate = expirationDate,
    deletionDate   = deletionDate,
    disabled       = disabled,
    textContent    = text?.text,
    textHidden     = text?.hidden ?: false,
    fileId         = file?.id,
    fileName       = file?.fileName,
    fileSize       = file?.size,
    revisionDate   = revisionDate,
)

fun CipherResponse.toEntity(accountId: String) = CipherEntity(
    id                   = id,
    accountId            = accountId,
    organizationId       = organizationId,
    folderId             = folderId,
    type                 = type,
    name                 = name,
    notes                = notes,
    favorite             = favorite,
    reprompt             = reprompt,
    deletedDate          = deletedDate,
    revisionDate         = revisionDate,
    creationDate         = creationDate,
    collectionIds        = if (collectionIds.isEmpty()) null else collectionIds.joinToString(","),
    loginUsername        = login?.username,
    loginPassword        = login?.password,
    loginPasswordRevDate = login?.passwordRevisionDate,
    loginTotp            = login?.totp,
    loginUris            = login?.uris?.let { uris ->
        kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonArray(
                uris.map { u ->
                    kotlinx.serialization.json.buildJsonObject {
                        put("uri",   u.uri?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                        put("match", u.match?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                    }
                }
            )
        )
    },
    loginAutofillOnPageLoad  = login?.autofillOnPageLoad,
    cardCardholderName       = card?.cardholderName,
    cardBrand                = card?.brand,
    cardNumber               = card?.number,
    cardExpMonth             = card?.expMonth,
    cardExpYear              = card?.expYear,
    cardCode                 = card?.code,
    identityTitle            = identity?.title,
    identityFirstName        = identity?.firstName,
    identityMiddleName       = identity?.middleName,
    identityLastName         = identity?.lastName,
    identityAddress1         = identity?.address1,
    identityAddress2         = identity?.address2,
    identityAddress3         = identity?.address3,
    identityCity             = identity?.city,
    identityState            = identity?.state,
    identityPostalCode       = identity?.postalCode,
    identityCountry          = identity?.country,
    identityCompany          = identity?.company,
    identityEmail            = identity?.email,
    identityPhone            = identity?.phone,
    identitySsn              = identity?.ssn,
    identityUsername         = identity?.username,
    identityPassportNumber   = identity?.passportNumber,
    identityLicenseNumber    = identity?.licenseNumber,
    fields                   = fields.takeIf { it.isNotEmpty() }?.let { kotlinx.serialization.json.Json.encodeToString(it) },
    attachments              = attachments.takeIf { it.isNotEmpty() }?.let { kotlinx.serialization.json.Json.encodeToString(it) },
    passwordHistory          = passwordHistory.takeIf { it.isNotEmpty() }?.let { kotlinx.serialization.json.Json.encodeToString(it) },
)
