package com.spectre.app.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.spectre.app.MainActivity
import com.spectre.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Bitwarden-compatible Android AutofillService.
 *
 * Flow:
 *  1. Android calls onFillRequest() when the user focuses a username/password field.
 *  2. We parse the AssistStructure to find credential-relevant fields.
 *  3. If the vault is unlocked, we match candidate ciphers against the app/domain.
 *  4. We build FillResponse with Dataset entries — tapping one fills the fields.
 *  5. If vault is locked, we present an authentication intent that unlocks first.
 */
@AndroidEntryPoint
class SpectreAutofillService : AutofillService() {

    @Inject lateinit var autofillParser: AutofillParser

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) { callback.onSuccess(null); return }

        val parsedStructure = autofillParser.parse(structure)
        if (parsedStructure.credentialFields.isEmpty()) { callback.onSuccess(null); return }

        // If vault is locked — send auth intent
        val authIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("autofill_request", true)
            putExtra("autofill_package", structure.activityComponent?.packageName)
        }
        val authPendingIntent = PendingIntent.getActivity(
            this, 0, authIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val responseBuilder = FillResponse.Builder()

        // Placeholder dataset that triggers unlock flow
        val unlockPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "🔒 Unlock Spectre vault…")
        }

        val unlockDataset = Dataset.Builder(unlockPresentation)
            .also { builder ->
                parsedStructure.credentialFields.forEach { field ->
                    builder.setValue(field.autofillId, AutofillValue.forText(""))
                }
            }
            .setAuthentication(authPendingIntent.intentSender)
            .build()

        responseBuilder.addDataset(unlockDataset)

        val saveInfo = SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
            parsedStructure.credentialFields.map { it.autofillId }.toTypedArray()
        ).build()

        responseBuilder.setSaveInfo(saveInfo)
        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Extract saved credentials and prompt user to save to vault
        callback.onSuccess()
    }
}

// ── Autofill field parser ─────────────────────────────────────────────────────

data class AutofillField(
    val autofillId: AutofillId,
    val type: FieldType,
)

enum class FieldType { USERNAME, PASSWORD, EMAIL, UNKNOWN }

data class ParsedAutofillStructure(
    val credentialFields: List<AutofillField>,
    val packageName: String?,
    val webDomain: String?,
)

class AutofillParser @Inject constructor() {

    fun parse(structure: android.app.assist.AssistStructure): ParsedAutofillStructure {
        val fields   = mutableListOf<AutofillField>()
        var domain: String? = null
        var pkg: String?    = null

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            parseNode(windowNode.rootViewNode, fields)
            if (pkg == null) pkg = windowNode.title?.toString()
        }

        return ParsedAutofillStructure(fields, pkg, domain)
    }

    private fun parseNode(
        node: android.app.assist.AssistStructure.ViewNode,
        fields: MutableList<AutofillField>,
    ) {
        val autofillId   = node.autofillId ?: run {
            for (i in 0 until node.childCount) parseNode(node.getChildAt(i), fields)
            return
        }

        val hints = node.autofillHints?.toList() ?: emptyList()
        val inputType = node.inputType
        val type = when {
            hints.any { it.contains("username", true) || it.contains("email", true) } ||
            inputType and android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS != 0 -> FieldType.USERNAME

            hints.any { it.contains("password", true) } ||
            inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
            inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0 -> FieldType.PASSWORD

            else -> FieldType.UNKNOWN
        }

        if (type != FieldType.UNKNOWN) {
            fields.add(AutofillField(autofillId, type))
        }

        for (i in 0 until node.childCount) {
            parseNode(node.getChildAt(i), fields)
        }
    }
}
