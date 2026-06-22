package com.spectre.app.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Size
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.spectre.app.MainActivity
import com.spectre.app.R
import com.spectre.app.core.data.models.CipherType
import com.spectre.app.core.data.models.DecryptedCipher
import com.spectre.app.core.data.models.LoginData
import com.spectre.app.core.data.models.LoginUri
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.LockState
import com.spectre.app.core.security.VaultSession
import com.spectre.app.core.utils.suspendRunCatching
import com.spectre.app.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Production Android AutofillService for Spectre.
 *
 * Flow:
 *  1. Android calls onFillRequest() when the user focuses a credential field.
 *  2. We parse the AssistStructure to find username/password fields.
 *  3. If vault is UNLOCKED → query matching ciphers → build and return FillResponse.
 *  4. If vault is LOCKED → return an auth Dataset that launches MainActivity to unlock.
 *  5. onSaveRequest() extracts typed credentials and prompts user to save to vault.
 */
@AndroidEntryPoint
class SpectreAutofillService : AutofillService() {

    @Inject lateinit var autofillParser: AutofillParser
    @Inject lateinit var autofillHelper: AutofillHelper
    @Inject lateinit var session: VaultSession
    @Inject lateinit var vaultRepository: VaultRepository
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private val serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) { callback.onSuccess(null); return }

        val job = serviceScope.launch {
            try {
                val parsedStructure = withContext(ioDispatcher) {
                    autofillParser.parse(structure)
                }
                if (parsedStructure.credentialFields.isEmpty()) {
                    callback.onSuccess(null)
                    return@launch
                }

                val response = if (session.isUnlocked) {
                    buildUnlockedResponse(request, parsedStructure)
                } else {
                    buildLockedResponse(request, parsedStructure, structure)
                }
                callback.onSuccess(response)
            } catch (e: Exception) {
                callback.onSuccess(null)
            }
        }

        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    /**
     * Vault is UNLOCKED — fetch ciphers, match against app/domain, return populated datasets.
     */
    private suspend fun buildUnlockedResponse(
        request: FillRequest,
        parsed: ParsedAutofillStructure,
    ): FillResponse? {
        val accountId = session.activeAccountId ?: return null

        val allCiphers = vaultRepository.getAllDecryptedCiphers(accountId)
        val matched = autofillHelper.findMatchingCiphers(
            ciphers = allCiphers,
            packageName = parsed.packageName,
            webDomain = parsed.webDomain,
        )

        if (matched.isEmpty()) return buildFallbackResponse(request, parsed)

        return autofillHelper.buildFillResponse(
            matchedCiphers = matched,
            parsedStructure = parsed,
            packageName = packageName,
        )
    }

    /**
     * No matching ciphers found — show a "Search Spectre Vault" option that opens the app.
     */
    @Suppress("DEPRECATION")
    private fun buildFallbackResponse(
        request: FillRequest,
        parsed: ParsedAutofillStructure,
    ): FillResponse? {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("autofill_request", true)
            putExtra("autofill_search", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "🔍 Search Spectre vault…")
        }

        @Suppress("DEPRECATION")
        val builder = Dataset.Builder(presentation)
        parsed.credentialFields.forEach { field ->
            builder.setValue(field.autofillId, null, presentation)
        }
        builder.setAuthentication(pendingIntent.intentSender)

        val responseBuilder = FillResponse.Builder()
        suspendRunCatching { responseBuilder.addDataset(builder.build()) }

        addSaveInfo(responseBuilder, parsed)
        return suspendRunCatching { responseBuilder.build() }.getOrNull()
    }

    /**
     * Vault is LOCKED — present an authentication dataset that opens the unlock screen.
     */
    @Suppress("DEPRECATION")
    private fun buildLockedResponse(
        request: FillRequest,
        parsed: ParsedAutofillStructure,
        structure: android.app.assist.AssistStructure,
    ): FillResponse? {
        val authIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("autofill_request", true)
            putExtra("autofill_package", parsed.packageName)
            putExtra("autofill_domain", parsed.webDomain)
        }
        val authPendingIntent = PendingIntent.getActivity(
            this, 0, authIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val responseBuilder = FillResponse.Builder()
        val inlineRequest = request.inlineSuggestionsRequest

        val unlockPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "🔒 Unlock Spectre vault…")
        }

        val datasetBuilder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && inlineRequest != null) {
            val specs = inlineRequest.inlinePresentationSpecs
            val spec = specs.firstOrNull()
                ?: InlinePresentationSpec.Builder(Size(100, 50), Size(400, 100)).build()
            val attribution = PendingIntent.getActivity(
                this, 0, Intent(), PendingIntent.FLAG_IMMUTABLE
            )
            val slice = InlineSuggestionUi.newContentBuilder(attribution)
                .setTitle("Unlock Spectre")
                .build()
                .slice
            val inlinePresentation = InlinePresentation(slice, spec, false)
            Dataset.Builder().setInlinePresentation(inlinePresentation)
        } else {
            Dataset.Builder(unlockPresentation)
        }

        parsed.credentialFields.forEach { field ->
            datasetBuilder.setValue(field.autofillId, null, unlockPresentation)
        }
        datasetBuilder.setAuthentication(authPendingIntent.intentSender)

        runCatching { responseBuilder.addDataset(datasetBuilder.build()) }
        addSaveInfo(responseBuilder, parsed)

        return runCatching { responseBuilder.build() }.getOrNull()
    }

    /**
     * onSaveRequest — Android calls this when the user submits credentials in another app.
     * We extract the username/password and launch Spectre to prompt saving.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) { callback.onSuccess(); return }

        val credentials = autofillHelper.extractSavedCredentials(structure)
        if (credentials == null) { callback.onSuccess(); return }

        // Launch the app with the extracted credentials to show a "Save to Vault?" prompt
        val saveIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("autofill_save", true)
            putExtra("save_username", credentials.username)
            putExtra("save_password", credentials.password)
            putExtra("save_domain", credentials.domain)
            putExtra("save_package", credentials.packageName)
        }

        try {
            startActivity(saveIntent)
        } catch (_: Exception) {
            // If we can't launch the activity, silently succeed
        }

        callback.onSuccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun addSaveInfo(builder: FillResponse.Builder, parsed: ParsedAutofillStructure) {
        val usernameIds = parsed.credentialFields
            .filter { it.type == FieldType.USERNAME }
            .map { it.autofillId }
        val passwordIds = parsed.credentialFields
            .filter { it.type == FieldType.PASSWORD }
            .map { it.autofillId }

        val allIds = (usernameIds + passwordIds).toTypedArray()
        if (allIds.isEmpty()) return

        val saveType = when {
            usernameIds.isNotEmpty() && passwordIds.isNotEmpty() ->
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
            passwordIds.isNotEmpty() -> SaveInfo.SAVE_DATA_TYPE_PASSWORD
            else -> SaveInfo.SAVE_DATA_TYPE_USERNAME
        }

        builder.setSaveInfo(
            SaveInfo.Builder(saveType, allIds)
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                .build()
        )
    }
}
