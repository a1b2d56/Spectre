package com.spectre.app.core.sync

import com.spectre.app.core.data.database.entities.CipherEntity
import com.spectre.app.core.network.model.CipherResponse
import kotlinx.serialization.json.*

/**
 * 3-way cipher merge engine.
 *
 * Resolves [SyncOp.MergeConflict] cases where both the local database
 * and the remote server have changes to the same cipher since the last sync.
 *
 * ### Strategy
 * - **Scalar fields** (name, notes, username, password, TOTP, etc.):
 *   Remote wins on true conflict (safest default for a password manager).
 *
 * - **Collection fields** (URIs, customFields):
 *   Union merge — additions from either side are preserved.
 *
 * - **Metadata** (revisionDate, deletedDate, creationDate):
 *   Always taken from the server response (source of truth for timestamps).
 *
 * The merged [CipherEntity] is written back to the local DB with
 * `pendingSync = true` so the merged result gets pushed to the server on
 * the next push pass inside [VaultRepository.sync].
 */
object CipherMerge {

    fun merge(
        remote: CipherResponse,
        local: CipherEntity,
        accountId: String,
    ): CipherEntity {

        // Scalar fields: remote wins on true conflict
        val mergedName  = pickScalar(remote.name, local.name)
        val mergedNotes = pickScalar(remote.notes, local.notes)
        val mergedFav   = if (local.pendingSync) local.favorite else remote.favorite
        val mergedRep   = if (local.pendingSync) local.reprompt else remote.reprompt

        // Login fields
        val mergedUser  = pickScalar(remote.login?.username, local.loginUsername)
        val mergedPass  = pickScalar(remote.login?.password, local.loginPassword)
        val mergedTotp  = pickScalar(remote.login?.totp,     local.loginTotp)

        // URIs: union merge
        val mergedUris = mergeUriJson(
            remoteUris    = remote.login?.uris?.map { it.uri to it.match } ?: emptyList(),
            localUrisJson = local.loginUris,
        )

        // Custom fields: union merge by name
        val mergedFields = mergeFieldsJson(
            remoteFields = serializeFieldsToJson(remote),
            localFields  = local.fields,
        )

        val mergedCard     = remote.card
        val mergedIdentity = remote.identity

        return local.copy(
            organizationId          = remote.organizationId ?: local.organizationId,
            folderId                = remote.folderId ?: local.folderId,
            collectionIds           = remote.collectionIds.joinToString(",")
                .takeIf { it.isNotBlank() } ?: local.collectionIds,
            type                    = remote.type,
            name                    = mergedName ?: local.name,
            notes                   = mergedNotes,
            favorite                = mergedFav,
            reprompt                = mergedRep,
            deletedDate             = remote.deletedDate,
            revisionDate            = remote.revisionDate,
            creationDate            = remote.creationDate ?: local.creationDate,
            loginUsername           = mergedUser,
            loginPassword           = mergedPass,
            loginPasswordRevDate    = remote.login?.passwordRevisionDate ?: local.loginPasswordRevDate,
            loginTotp               = mergedTotp,
            loginUris               = mergedUris,
            loginAutofillOnPageLoad = remote.login?.autofillOnPageLoad ?: local.loginAutofillOnPageLoad,
            cardCardholderName      = mergedCard?.cardholderName ?: local.cardCardholderName,
            cardBrand               = mergedCard?.brand           ?: local.cardBrand,
            cardNumber              = mergedCard?.number          ?: local.cardNumber,
            cardExpMonth            = mergedCard?.expMonth        ?: local.cardExpMonth,
            cardExpYear             = mergedCard?.expYear         ?: local.cardExpYear,
            cardCode                = mergedCard?.code            ?: local.cardCode,
            identityFirstName       = mergedIdentity?.firstName   ?: local.identityFirstName,
            identityLastName        = mergedIdentity?.lastName    ?: local.identityLastName,
            identityEmail           = mergedIdentity?.email       ?: local.identityEmail,
            identityPhone           = mergedIdentity?.phone       ?: local.identityPhone,
            identityAddress1        = mergedIdentity?.address1    ?: local.identityAddress1,
            identityCity            = mergedIdentity?.city        ?: local.identityCity,
            identityPostalCode      = mergedIdentity?.postalCode  ?: local.identityPostalCode,
            identityCountry         = mergedIdentity?.country     ?: local.identityCountry,
            identityCompany         = mergedIdentity?.company     ?: local.identityCompany,
            identityTitle           = mergedIdentity?.title       ?: local.identityTitle,
            identityMiddleName      = mergedIdentity?.middleName  ?: local.identityMiddleName,
            identityAddress2        = mergedIdentity?.address2    ?: local.identityAddress2,
            identityAddress3        = mergedIdentity?.address3    ?: local.identityAddress3,
            identityState           = mergedIdentity?.state       ?: local.identityState,
            identitySsn             = mergedIdentity?.ssn         ?: local.identitySsn,
            identityUsername        = mergedIdentity?.username    ?: local.identityUsername,
            identityPassportNumber  = mergedIdentity?.passportNumber ?: local.identityPassportNumber,
            identityLicenseNumber   = mergedIdentity?.licenseNumber  ?: local.identityLicenseNumber,
            fields                  = mergedFields,
            attachments             = serializeAttachmentsToJson(remote) ?: local.attachments,
            passwordHistory         = serializeHistoryToJson(remote)     ?: local.passwordHistory,
            pendingSync             = true,
            lastSyncedRevision      = remote.revisionDate,
        )
    }

    // ── Serialization helpers ─────────────────────────────────────────────

    private fun serializeFieldsToJson(remote: CipherResponse): String? {
        if (remote.fields.isEmpty()) return null
        return buildJsonArray {
            remote.fields.forEach { f ->
                add(buildJsonObject {
                    put("type",  f.type)
                    put("name",  f.name  ?: "")
                    put("value", f.value ?: "")
                })
            }
        }.toString()
    }

    private fun serializeAttachmentsToJson(remote: CipherResponse): String? {
        if (remote.attachments.isEmpty()) return null
        return buildJsonArray {
            remote.attachments.forEach { a ->
                add(buildJsonObject {
                    put("id",       a.id)
                    put("url",      a.url      ?: "")
                    put("fileName", a.fileName ?: "")
                    put("key",      a.key      ?: "")
                    put("size",     a.size     ?: "")
                })
            }
        }.toString()
    }

    private fun serializeHistoryToJson(remote: CipherResponse): String? {
        if (remote.passwordHistory.isEmpty()) return null
        return buildJsonArray {
            remote.passwordHistory.forEach { h ->
                add(buildJsonObject {
                    put("password",     h.password)
                    put("lastUsedDate", h.lastUsedDate)
                })
            }
        }.toString()
    }

    // ── Merge helpers ─────────────────────────────────────────────────────

    private fun pickScalar(remote: String?, localCurrent: String?): String? =
        if (remote != null && remote != localCurrent) remote else localCurrent

    /** Union merge of URI lists. Remote wins on URI collision. */
    private fun mergeUriJson(
        remoteUris: List<Pair<String?, Int?>>,
        localUrisJson: String?,
    ): String? {
        val merged = linkedMapOf<String, Int?>()
        remoteUris.forEach { (uri, match) ->
            if (!uri.isNullOrBlank()) merged[uri] = match
        }
        if (!localUrisJson.isNullOrBlank()) {
            runCatching {
                Json.parseToJsonElement(localUrisJson).jsonArray.forEach { el ->
                    val obj = el.jsonObject
                    val uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (uri !in merged) {
                        merged[uri] = obj["match"]?.jsonPrimitive?.intOrNull
                    }
                }
            }
        }
        if (merged.isEmpty()) return null
        return buildJsonArray {
            merged.forEach { (uri, match) ->
                add(buildJsonObject {
                    put("uri", uri)
                    if (match != null) put("match", match)
                })
            }
        }.toString()
    }

    /** Union merge of custom fields by name. Remote wins on name collision. */
    private fun mergeFieldsJson(remoteFields: String?, localFields: String?): String? {
        if (remoteFields == null && localFields == null) return null
        if (remoteFields == null) return localFields
        if (localFields  == null) return remoteFields

        fun parse(json: String): List<JsonObject> = runCatching {
            Json.parseToJsonElement(json).jsonArray
                .filterIsInstance<JsonObject>()
        }.getOrDefault(emptyList())

        val merged = linkedMapOf<String, JsonObject>()
        parse(remoteFields).forEach { field ->
            val name = field["name"]?.jsonPrimitive?.contentOrNull ?: field.toString()
            merged[name] = field
        }
        parse(localFields).forEach { field ->
            val name = field["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            if (name !in merged) merged[name] = field
        }
        return buildJsonArray {
            merged.values.forEach { add(it) }
        }.toString()
    }
}
