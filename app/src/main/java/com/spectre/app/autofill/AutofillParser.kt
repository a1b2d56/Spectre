package com.spectre.app.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.autofill.AutofillId
import javax.inject.Inject

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

/**
 * Recursively traverses an AssistStructure to find autofillable credential fields.
 * Recognises fields via autofill hints (preferred) and input type flags (fallback).
 */
class AutofillParser @Inject constructor() {

    fun parse(structure: AssistStructure): ParsedAutofillStructure {
        val fields  = mutableListOf<AutofillField>()
        var domain: String? = null
        var pkg: String?    = null

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            parseNode(windowNode.rootViewNode, fields)
            if (pkg == null) pkg = windowNode.title?.toString()
            if (domain == null) domain = findWebDomain(windowNode.rootViewNode)
        }

        return ParsedAutofillStructure(fields, pkg, domain)
    }

    private fun findWebDomain(node: AssistStructure.ViewNode): String? {
        if (node.webDomain != null) return node.webDomain
        for (i in 0 until node.childCount) {
            val d = findWebDomain(node.getChildAt(i))
            if (d != null) return d
        }
        return null
    }

    private fun parseNode(
        node: AssistStructure.ViewNode,
        fields: MutableList<AutofillField>,
    ) {
        val autofillId = node.autofillId ?: run {
            for (i in 0 until node.childCount) parseNode(node.getChildAt(i), fields)
            return
        }

        val hints = node.autofillHints?.toList() ?: emptyList()
        val inputType = node.inputType

        val type = when {
            // Prefer explicit autofill hints
            hints.any { it.contains("password", true) } ||
            inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
            inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0 ||
            inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != 0 -> FieldType.PASSWORD

            hints.any { it.contains("username", true) } ||
            hints.any { it.contains("email", true) } ||
            inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS != 0 -> FieldType.USERNAME

            // Fallback: check view resource ID or hint text
            node.idEntry?.let { id ->
                id.contains("user", true) || id.contains("login", true) ||
                id.contains("email", true) || id.contains("account", true)
            } == true -> FieldType.USERNAME

            node.idEntry?.let { id ->
                id.contains("pass", true) || id.contains("pwd", true)
            } == true -> FieldType.PASSWORD

            // Check the hint text on the view itself
            node.hint?.let { hint ->
                hint.contains("password", true) || hint.contains("passwort", true)
            } == true -> FieldType.PASSWORD

            node.hint?.let { hint ->
                hint.contains("username", true) || hint.contains("email", true) ||
                hint.contains("login", true)
            } == true -> FieldType.USERNAME

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
