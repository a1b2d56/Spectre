package com.spectre.app.autofill

import android.app.assist.AssistStructure
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.spectre.app.core.data.models.CipherType
import com.spectre.app.core.data.models.DecryptedCipher
import com.spectre.app.core.data.models.UriMatchType
import com.spectre.app.core.utils.suspendRunCatching
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized autofill logic shared between the AutofillService (unlocked path)
 * and MainActivity (post-unlock authentication path).
 *
 * Responsibilities:
 *  - Match DecryptedCiphers against the requesting app/domain
 *  - Build FillResponse with one Dataset per matching cipher
 *  - Construct SaveInfo for credential-save prompts
 */
@Singleton
class AutofillHelper @Inject constructor() {

    /**
     * Finds ciphers that match the given package name or web domain.
     * Uses the URI match type configured on each cipher for flexible matching.
     */
    fun findMatchingCiphers(
        ciphers: List<DecryptedCipher>,
        packageName: String?,
        webDomain: String?,
    ): List<DecryptedCipher> {
        if (packageName == null && webDomain == null) return emptyList()

        return ciphers
            .filter { it.type == CipherType.LOGIN && !it.isInTrash }
            .filter { cipher ->
                val uris = cipher.loginData?.uris ?: return@filter false
                uris.any { loginUri ->
                    val uri = loginUri.uri ?: return@any false
                    matchesTarget(uri, loginUri.match, packageName, webDomain)
                }
            }
            .sortedByDescending { it.favorite } // Favorites first
    }

    /**
     * Builds a complete FillResponse from matched ciphers and parsed fields.
     * Returns null if no ciphers match or no credential fields were found.
     */
    @Suppress("DEPRECATION")
    fun buildFillResponse(
        matchedCiphers: List<DecryptedCipher>,
        parsedStructure: ParsedAutofillStructure,
        packageName: String,
    ): FillResponse? {
        if (matchedCiphers.isEmpty() || parsedStructure.credentialFields.isEmpty()) return null

        val responseBuilder = FillResponse.Builder()
        val usernameFields = parsedStructure.credentialFields.filter { it.type == FieldType.USERNAME }
        val passwordFields = parsedStructure.credentialFields.filter { it.type == FieldType.PASSWORD }

        for (cipher in matchedCiphers.take(5)) { // Cap at 5 suggestions
            val username = cipher.loginData?.username
            val password = cipher.loginData?.password

            if (username.isNullOrBlank() && password.isNullOrBlank()) continue

            val displayName = cipher.name
            val displaySub  = username ?: "No username"

            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                setTextViewText(android.R.id.text1, displayName)
                setTextViewText(android.R.id.text2, displaySub)
            }

            @Suppress("DEPRECATION")
            val datasetBuilder = Dataset.Builder(presentation)

            // Fill username fields
            if (!username.isNullOrBlank()) {
                usernameFields.forEach { field ->
                    datasetBuilder.setValue(field.autofillId, AutofillValue.forText(username))
                }
            }

            // Fill password fields
            if (!password.isNullOrBlank()) {
                passwordFields.forEach { field ->
                    datasetBuilder.setValue(field.autofillId, AutofillValue.forText(password))
                }
            }

            // Ensure at least one field was set — otherwise Dataset.build() throws
            if (usernameFields.isEmpty() && passwordFields.isEmpty()) continue

            suspendRunCatching { responseBuilder.addDataset(datasetBuilder.build()) }
        }

        // Add SaveInfo so Android prompts to save new credentials
        val saveIds = parsedStructure.credentialFields.map { it.autofillId }.toTypedArray()
        if (saveIds.isNotEmpty()) {
            val saveType = when {
                usernameFields.isNotEmpty() && passwordFields.isNotEmpty() ->
                    SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
                passwordFields.isNotEmpty() -> SaveInfo.SAVE_DATA_TYPE_PASSWORD
                else -> SaveInfo.SAVE_DATA_TYPE_USERNAME
            }
            responseBuilder.setSaveInfo(SaveInfo.Builder(saveType, saveIds).build())
        }

        return suspendRunCatching { responseBuilder.build() }.getOrNull()
    }

    /**
     * Extracts username and password from a SaveRequest's AssistStructure.
     * Returns a pair of (username, password) or null if nothing was found.
     */
    fun extractSavedCredentials(structure: AssistStructure): SavedCredentials? {
        val parser = AutofillParser()
        val parsed = parser.parse(structure)
        if (parsed.credentialFields.isEmpty()) return null

        var username: String? = null
        var password: String? = null

        for (i in 0 until structure.windowNodeCount) {
            val rootNode = structure.getWindowNodeAt(i).rootViewNode
            extractFromNode(rootNode, parsed.credentialFields) { field, value ->
                when (field.type) {
                    FieldType.USERNAME, FieldType.EMAIL -> if (username == null) username = value
                    FieldType.PASSWORD -> if (password == null) password = value
                    else -> {}
                }
            }
        }

        if (username.isNullOrBlank() && password.isNullOrBlank()) return null

        return SavedCredentials(
            username = username,
            password = password,
            domain = parsed.webDomain,
            packageName = parsed.packageName,
        )
    }


    /*
     * Internal utilities for AssistStructure parsing and URI matching.
     */

    private fun extractFromNode(
        node: AssistStructure.ViewNode,
        fields: List<AutofillField>,
        onFound: (AutofillField, String) -> Unit,
    ) {
        val id = node.autofillId
        if (id != null) {
            val field = fields.find { it.autofillId == id }
            if (field != null) {
                val value = node.autofillValue?.textValue?.toString()
                if (!value.isNullOrBlank()) {
                    onFound(field, value)
                }
            }
        }
        for (i in 0 until node.childCount) {
            extractFromNode(node.getChildAt(i), fields, onFound)
        }
    }

    private fun matchesTarget(
        cipherUri: String,
        matchType: UriMatchType,
        targetPackage: String?,
        targetDomain: String?,
    ): Boolean = when (matchType) {
        UriMatchType.NEVER -> false

        UriMatchType.EXACT -> {
            cipherUri.equals(targetDomain, ignoreCase = true) ||
            cipherUri.equals("androidapp://$targetPackage", ignoreCase = true)
        }

        UriMatchType.STARTS_WITH -> {
            targetDomain?.let { cipherUri.startsWith(it, ignoreCase = true) } == true ||
            targetPackage?.let { cipherUri.startsWith("androidapp://$it", ignoreCase = true) } == true
        }

        UriMatchType.REGEX -> {
            suspendRunCatching {
                val regex = Regex(cipherUri, RegexOption.IGNORE_CASE)
                targetDomain?.let { regex.containsMatchIn(it) } == true ||
                targetPackage?.let { regex.containsMatchIn(it) } == true
            }.getOrDefault(false)
        }

        UriMatchType.HOST -> {
            val cipherHost = extractHost(cipherUri)
            val targetHost = targetDomain ?: extractHost("https://$targetPackage")
            cipherHost.equals(targetHost, ignoreCase = true)
        }

        UriMatchType.DOMAIN -> {
            // Default: extract the registrable domain and compare
            val cipherDomain = extractDomain(cipherUri)
            val targetDomainStr = targetDomain?.let { extractDomain(it) }
                ?: targetPackage?.let { extractDomainFromPackage(it) }
            cipherDomain.equals(targetDomainStr, ignoreCase = true) && cipherDomain.isNotBlank()
        }
    }

    private fun extractHost(url: String): String = suspendRunCatching {
        URI(if (url.contains("://")) url else "https://$url").host ?: url
    }.getOrDefault(url)

    private fun extractDomain(url: String): String {
        val host = extractHost(url)
        val parts = host.split(".")
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    private fun extractDomainFromPackage(pkg: String): String {
        // com.google.android.gm -> google.com
        val parts = pkg.split(".")
        return if (parts.size >= 2) "${parts[1]}.${parts[0]}" else pkg
    }
}

data class SavedCredentials(
    val username: String?,
    val password: String?,
    val domain: String?,
    val packageName: String?,
)
