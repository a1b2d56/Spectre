package com.spectre.app.core.data.repository

import com.spectre.app.core.crypto.BitwardenCrypto
import com.spectre.app.core.crypto.EncString
import com.spectre.app.core.crypto.SymmetricKey
import com.spectre.app.core.data.database.dao.*
import com.spectre.app.core.data.database.entities.*
import com.spectre.app.core.data.models.*
import com.spectre.app.core.network.VaultApi
import com.spectre.app.core.network.model.*
import com.spectre.app.core.security.VaultSession
import com.spectre.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID
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
    private val accountDao: AccountDao,
    private val session: VaultSession,
    private val crypto: BitwardenCrypto,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    // Observe decrypted vault

    fun observeAllCiphers(): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeAll(accountId).map { entities ->
            entities.mapNotNull { decryptCipher(it) }
        }.flowOn(ioDispatcher)
    }

    fun observeFavorites(): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeFavorites(accountId).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(ioDispatcher)
    }

    fun observeByType(type: CipherType): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeByType(accountId, type.value).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(ioDispatcher)
    }

    fun observeTrash(): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeTrash(accountId).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(ioDispatcher)
    }

    fun observeFolder(folderId: String): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.observeByFolder(accountId, folderId).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(ioDispatcher)
    }

    fun search(query: String): Flow<List<DecryptedCipher>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return cipherDao.search(accountId, query).map { list ->
            list.mapNotNull { decryptCipher(it) }
        }.flowOn(ioDispatcher)
    }

    fun observeById(id: String): Flow<DecryptedCipher?> =
        cipherDao.observeById(id)
            .map { it?.let { e -> decryptCipher(e) } }
            .flowOn(ioDispatcher)

    fun observeFolders(): Flow<List<DecryptedFolder>> {
        val accountId = session.activeAccountId ?: return flowOf(emptyList())
        return folderDao.observeAll(accountId).map { list ->
            list.mapNotNull { decryptFolder(it) }
        }.flowOn(ioDispatcher)
    }

    /** One-shot fetch of all decrypted ciphers — used by Watchtower. */
    suspend fun getAllDecryptedCiphers(accountId: String): List<DecryptedCipher> =
        withContext(ioDispatcher) {
            cipherDao.getAllLoginCiphers(accountId).mapNotNull { decryptCipher(it) } +
            cipherDao.observeByType(accountId, CipherType.CARD.value).first().mapNotNull { decryptCipher(it) } +
            cipherDao.observeByType(accountId, CipherType.SECURE_NOTE.value).first().mapNotNull { decryptCipher(it) } +
            cipherDao.observeByType(accountId, CipherType.IDENTITY.value).first().mapNotNull { decryptCipher(it) }
        }

    // Full sync

    suspend fun sync(accountId: String): Result<Unit> = withContext(ioDispatcher) {
        val account = accountDao.getById(accountId)
        if (account?.isLocal == true) return@withContext Result.success(Unit)

        runCatching {
            val response = vaultApi.sync()
            val body     = response.body()
                ?: error("Empty sync response (${response.code()}): ${response.errorBody()?.string()}")

            // Update account premium status
            accountDao.updatePremiumStatus(accountId, body.profile.premium)

            // Organisations
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

    // CRUD

    suspend fun createCipher(cipher: DecryptedCipher): Result<DecryptedCipher> =
        withContext(ioDispatcher) {
            val account = accountDao.getById(cipher.accountId)
            val isLocal = account?.isLocal == true

            runCatching {
                val entity = if (isLocal) {
                    // Local-only: create a random UUID and current timestamp
                    val now = java.time.Instant.now().toString()
                    val request = encryptCipherRequest(cipher)
                    CipherResponse(
                        id = UUID.randomUUID().toString(),
                        type = request.type,
                        name = request.name,
                        notes = request.notes,
                        favorite = request.favorite,
                        reprompt = request.reprompt,
                        folderId = request.folderId,
                        organizationId = request.organizationId,
                        collectionIds = request.collectionIds,
                        login = request.login?.let { l -> LoginResponse(username = l.username, password = l.password, totp = l.totp, uris = l.uris.map { u -> LoginUriResponse(uri = u.uri, match = u.match) }) },
                        card = request.card?.let { c -> CardResponse(cardholderName = c.cardholderName, brand = c.brand, number = c.number, expMonth = c.expMonth, expYear = c.expYear, code = c.code) },
                        identity = request.identity?.let { i -> IdentityResponse(firstName = i.firstName, lastName = i.lastName, email = i.email, phone = i.phone, address1 = i.address1, city = i.city, postalCode = i.postalCode, country = i.country) },
                        revisionDate = now,
                        creationDate = now,
                    ).toEntity(cipher.accountId)
                } else {
                    val request  = encryptCipherRequest(cipher)
                    val response = vaultApi.createCipher(request)
                    val body     = response.body()
                        ?: error("Create cipher failed (${response.code()})")
                    body.toEntity(cipher.accountId)
                }
                cipherDao.upsert(entity)
                decryptCipher(entity) ?: error("Failed to decrypt created cipher")
            }
        }

    suspend fun updateCipher(cipher: DecryptedCipher): Result<DecryptedCipher> =
        withContext(ioDispatcher) {
            val account = accountDao.getById(cipher.accountId)
            val isLocal = account?.isLocal == true

            runCatching {
                val entity = if (isLocal) {
                    val now = java.time.Instant.now().toString()
                    val request = encryptCipherRequest(cipher)
                    CipherResponse(
                        id = cipher.id,
                        type = request.type,
                        name = request.name,
                        notes = request.notes,
                        favorite = request.favorite,
                        reprompt = request.reprompt,
                        folderId = request.folderId,
                        organizationId = request.organizationId,
                        collectionIds = request.collectionIds,
                        login = request.login?.let { l -> LoginResponse(username = l.username, password = l.password, totp = l.totp, uris = l.uris.map { u -> LoginUriResponse(uri = u.uri, match = u.match) }) },
                        card = request.card?.let { c -> CardResponse(cardholderName = c.cardholderName, brand = c.brand, number = c.number, expMonth = c.expMonth, expYear = c.expYear, code = c.code) },
                        identity = request.identity?.let { i -> IdentityResponse(firstName = i.firstName, lastName = i.lastName, email = i.email, phone = i.phone, address1 = i.address1, city = i.city, postalCode = i.postalCode, country = i.country) },
                        revisionDate = now,
                        creationDate = cipher.creationDate,
                    ).toEntity(cipher.accountId)
                } else {
                    val request  = encryptCipherRequest(cipher)
                    val response = vaultApi.updateCipher(cipher.id, request)
                    val body     = response.body()
                        ?: error("Update cipher failed (${response.code()})")
                    body.toEntity(cipher.accountId)
                }
                cipherDao.upsert(entity)
                decryptCipher(entity) ?: error("Failed to decrypt updated cipher")
            }
        }

    suspend fun deleteCipher(id: String): Result<Unit> = withContext(ioDispatcher) {
        val accountId = session.activeAccountId ?: return@withContext Result.failure(Exception("No active account"))
        val account = accountDao.getById(accountId)
        val isLocal = account?.isLocal == true

        runCatching {
            if (!isLocal) vaultApi.softDeleteCipher(id)
            cipherDao.softDelete(id, java.time.Instant.now().toString())
        }
    }

    suspend fun permanentlyDeleteCipher(id: String): Result<Unit> = withContext(ioDispatcher) {
        val accountId = session.activeAccountId ?: return@withContext Result.failure(Exception("No active account"))
        val account = accountDao.getById(accountId)
        val isLocal = account?.isLocal == true

        runCatching {
            if (!isLocal) vaultApi.deleteCipher(id)
            cipherDao.hardDelete(id)
        }
    }

    suspend fun restoreCipher(id: String): Result<Unit> = withContext(ioDispatcher) {
        val accountId = session.activeAccountId ?: return@withContext Result.failure(Exception("No active account"))
        val account = accountDao.getById(accountId)
        val isLocal = account?.isLocal == true

        runCatching {
            if (!isLocal) vaultApi.restoreCipher(id)
            cipherDao.restore(id)
        }
    }

    suspend fun toggleFavorite(id: String): Result<Unit> = withContext(ioDispatcher) {
        val accountId = session.activeAccountId ?: return@withContext Result.failure(Exception("No active account"))
        val account = accountDao.getById(accountId)
        val isLocal = account?.isLocal == true

        runCatching {
            if (!isLocal) vaultApi.toggleFavorite(id)
            cipherDao.toggleFavorite(id)
        }
    }

    suspend fun createFolder(name: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val accountId = session.activeAccountId ?: error("No active account")
            val account   = accountDao.getById(accountId)
            val isLocal   = account?.isLocal == true
            val userKey   = session.getUserKey()
            val encName   = crypto.encryptString(name, userKey).encode()
            
            val entity = if (isLocal) {
                FolderResponse(
                    id = UUID.randomUUID().toString(),
                    name = encName,
                    revisionDate = java.time.Instant.now().toString()
                ).toEntity(accountId)
            } else {
                val response  = vaultApi.createFolder(FolderRequest(name = encName))
                val body      = response.body() ?: error("Create folder failed")
                body.toEntity(accountId)
            }
            folderDao.upsert(entity)
        }
    }

    suspend fun deleteFolder(id: String): Result<Unit> = withContext(ioDispatcher) {
        val accountId = session.activeAccountId ?: return@withContext Result.failure(Exception("No active account"))
        val account = accountDao.getById(accountId)
        val isLocal = account?.isLocal == true

        runCatching {
            if (!isLocal) vaultApi.deleteFolder(id)
            folderDao.deleteById(id)
        }
    }

    suspend fun purgeTrash(): Result<Unit> = withContext(ioDispatcher) {
        val accountId = session.activeAccountId ?: return@withContext Result.failure(Exception("No active account"))
        runCatching {
            cipherDao.purgeTrash(accountId)
        }
    }

    suspend fun createSend(
        name: String,
        type: Int,
        textContent: String?,
        hidden: Boolean,
        expirationDate: String?,
        maxAccessCount: Int?,
    ): Result<DecryptedSend> = withContext(ioDispatcher) {
        val accountId = session.activeAccountId ?: return@withContext Result.failure(Exception("No active account"))
        val account = accountDao.getById(accountId)
        val isLocal = account?.isLocal == true

        runCatching {
            // Generate a random key for this Send (independent of vault key)
            val sendKeyBytes = ByteArray(32).apply { java.util.Random().nextBytes(this) }
            val sendKey = SymmetricKey(sendKeyBytes)
            val sendKeyEnc = crypto.encryptString(sendKeyBytes.joinToString("") { "%02x".format(it) }, session.getUserKey()).encode()

            val encryptedText = textContent?.let { crypto.encryptString(it, sendKey).encode() }

            val entity = if (isLocal) {
                val now = java.time.Instant.now().toString()
                SendResponse(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    name = crypto.encryptString(name, session.getUserKey()).encode(),
                    key = sendKeyEnc,
                    maxAccessCount = maxAccessCount,
                    accessCount = 0,
                    expirationDate = expirationDate,
                    deletionDate = expirationDate ?: now,
                    disabled = false,
                    text = if (type == 0) SendTextResponse(text = encryptedText, hidden = hidden) else null,
                    revisionDate = now,
                ).toEntity(accountId)
            } else {
                val request = SendRequest(
                    type = type,
                    name = crypto.encryptString(name, session.getUserKey()).encode(),
                    key = sendKeyEnc,
                    maxAccessCount = maxAccessCount,
                    expirationDate = expirationDate,
                    deletionDate = expirationDate,
                    disabled = false,
                    text = if (type == 0) SendTextRequest(text = encryptedText, hidden = hidden) else null,
                )
                val response = vaultApi.createSend(request)
                val body = response.body() ?: error("Create send failed (${response.code()})")
                body.toEntity(accountId)
            }
            sendDao.upsert(entity)
            decryptSend(entity) ?: error("Failed to decrypt created send")
        }
    }

    private fun decryptSend(entity: SendEntity): DecryptedSend? = runCatching {
        val vaultKey = session.getUserKey()
        val decSendKeyHex = crypto.decryptOrNull(entity.key, vaultKey) ?: return null
        val sendKey = SymmetricKey(decSendKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

        val decName = crypto.decryptOrNull(entity.name, vaultKey) ?: entity.name
        val decText = entity.textContent?.let { crypto.decryptOrNull(it, sendKey) }

        DecryptedSend(
            id = entity.id,
            type = entity.type,
            name = decName,
            notes = entity.notes,
            maxAccessCount = entity.maxAccessCount,
            accessCount = entity.accessCount,
            expirationDate = entity.expirationDate,
            deletionDate = entity.deletionDate,
            disabled = entity.disabled,
            textContent = decText,
            textHidden = entity.textHidden,
            fileName = entity.fileName,
            revisionDate = entity.revisionDate,
        )
    }.getOrNull()

    // Decryption helpers

    private fun decryptCipher(entity: CipherEntity): DecryptedCipher? = runCatching {
        val key = if (entity.organizationId != null)
            session.getOrgKey(entity.organizationId) ?: session.getUserKey()
        else
            session.getUserKey()

        val decName  = crypto.decryptOrNull(entity.name, key) ?: return null
        val decNotes = crypto.decryptOrNull(entity.notes, key)
        val type     = CipherType.fromInt(entity.type)

        val loginData = if (type == CipherType.LOGIN) LoginData(
            username             = crypto.decryptOrNull(entity.loginUsername, key),
            password             = crypto.decryptOrNull(entity.loginPassword, key),
            passwordRevisionDate = entity.loginPasswordRevDate,
            totp                 = crypto.decryptOrNull(entity.loginTotp, key),
            uris                 = parseUrisFromJson(entity.loginUris, key),
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
            collectionIds   = entity.collectionIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
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

    private fun parseUrisFromJson(raw: String?, key: SymmetricKey): List<LoginUri> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.map { element ->
                val obj   = element.jsonObject
                val uriRaw = obj["uri"]?.jsonPrimitive?.contentOrNull
                val match  = obj["match"]?.jsonPrimitive?.intOrNull
                LoginUri(
                    uri   = uriRaw?.let { crypto.decryptOrNull(it, key) } ?: uriRaw,
                    match = UriMatchType.fromInt(match),
                )
            }
        }.getOrDefault(emptyList())
    }

    // Encryption helpers

    private fun encryptCipherRequest(cipher: DecryptedCipher): CipherRequest {
        val key    = session.getUserKey()
        fun enc(s: String?) = s?.let { crypto.encryptString(it, key).encode() }

        return CipherRequest(
            type           = cipher.type.value,
            name           = enc(cipher.name) ?: "",
            notes          = enc(cipher.notes),
            favorite       = cipher.favorite,
            reprompt       = if (cipher.reprompt) 1 else 0,
            folderId       = cipher.folderId,
            organizationId = cipher.organizationId,
            collectionIds  = cipher.collectionIds,
            login          = cipher.loginData?.let { l ->
                LoginRequest(
                    username = enc(l.username),
                    password = enc(l.password),
                    totp     = enc(l.totp),
                    uris     = l.uris.map { u -> LoginUriRequest(uri = enc(u.uri), match = u.match.value) },
                )
            },
            card = cipher.cardData?.let { c ->
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

    private fun computePasswordStrength(password: String): PasswordStrength {
        if (password.length < 6) return PasswordStrength.VERY_WEAK
        var score = 0
        if (password.length >= 8)  score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return PasswordStrength.fromScore((score - 1).coerceAtLeast(0))
    }
}

// Entity mappers

private fun FolderResponse.toEntity(accountId: String) = FolderEntity(
    id = id, accountId = accountId, name = name, revisionDate = revisionDate,
)

private fun CollectionResponse.toEntity(accountId: String) = CollectionEntity(
    id = id, accountId = accountId, organizationId = organizationId,
    name = name, hidePasswords = hidePasswords,
)

private fun SendResponse.toEntity(accountId: String) = SendEntity(
    id = id, accountId = accountId, type = type, name = name, notes = notes,
    key = key, maxAccessCount = maxAccessCount, accessCount = accessCount,
    expirationDate = expirationDate, deletionDate = deletionDate, disabled = disabled,
    textContent = text?.text, textHidden = text?.hidden ?: false,
    fileId = file?.id, fileName = file?.fileName, fileSize = file?.size,
    revisionDate = revisionDate,
)

fun CipherResponse.toEntity(accountId: String) = CipherEntity(
    id                     = id,
    accountId              = accountId,
    organizationId         = organizationId,
    folderId               = folderId,
    type                   = type,
    name                   = name,
    notes                  = notes,
    favorite               = favorite,
    reprompt               = reprompt,
    deletedDate            = deletedDate,
    revisionDate           = revisionDate,
    creationDate           = creationDate,
    collectionIds          = collectionIds.joinToString(",").takeIf { it.isNotBlank() },
    loginUsername          = login?.username,
    loginPassword          = login?.password,
    loginPasswordRevDate   = login?.passwordRevisionDate,
    loginTotp              = login?.totp,
    loginUris              = login?.uris?.let { uris ->
        Json.encodeToString(JsonArray(uris.map { u ->
            buildJsonObject {
                put("uri",   u.uri?.let { JsonPrimitive(it) } ?: JsonNull)
                put("match", u.match?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }))
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
    fields                   = fields.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) },
    attachments              = attachments.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) },
    passwordHistory          = passwordHistory.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) },
)
