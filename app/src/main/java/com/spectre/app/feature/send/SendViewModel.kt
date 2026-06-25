package com.spectre.app.feature.send

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectre.app.core.data.database.dao.SendDao
import com.spectre.app.core.data.database.entities.SendEntity
import com.spectre.app.core.security.VaultSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

data class SendUiState(
    val sends: List<SendEntity> = emptyList(),
    val generatedLink: String? = null,
    val isGenerating: Boolean = false,
    val isPremium: Boolean = false,
    val serverUrl: String = "https://send.bitwarden.com",
)

@HiltViewModel
class SendViewModel @Inject constructor(
    private val authRepository: com.spectre.app.core.data.repository.AuthRepository,
    private val vaultRepository: com.spectre.app.core.data.repository.VaultRepository,
    private val sendDao: SendDao,
    private val session: VaultSession,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    init {
        viewModelScope.launch {
            val accountId = session.activeAccountId ?: return@launch
            sendDao.observeAll(accountId).collect { sends ->
                _state.update { it.copy(sends = sends) }
            }
        }
        viewModelScope.launch {
            authRepository.observeActiveAccount().collect { account ->
                _state.update { it.copy(
                    isPremium = account?.premium == true || account?.isLocal == true,
                    serverUrl = account?.serverUrl ?: "https://send.bitwarden.com"
                ) }
            }
        }
    }

    fun generateLink(
        name: String,
        isTextMode: Boolean,
        textPayload: String,
        expirationDays: Float,
        maxAccessCount: Float,
        isPasswordProtected: Boolean
    ) {
        val accountId = session.activeAccountId ?: return
        if (isTextMode && textPayload.isBlank()) return
        if (!isTextMode && !_state.value.isPremium) {
            viewModelScope.launch { _snackbar.emit("Bitwarden Premium is required to send files.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generatedLink = null) }

            val now = Instant.now()
            val expirationDate = if (expirationDays > 0) now.plusSeconds((expirationDays * 86400).toLong()).toString() else null

            val result = vaultRepository.createSend(
                name = name.ifBlank { if (isTextMode) "Secure Text" else "Secure File" },
                type = if (isTextMode) 0 else 1,
                textContent = textPayload,
                hidden = isPasswordProtected,
                expirationDate = expirationDate,
                maxAccessCount = if (maxAccessCount == 0f) null else maxAccessCount.toInt()
            )

            result.onSuccess { send ->
                val serverUrl = _state.value.serverUrl
                val base = getSendBaseUrl(serverUrl)
                val link = if (send.keyBase64 != null) {
                    "$base${send.id}/${send.keyBase64}"
                } else {
                    "$base${send.id}"
                }
                
                _state.update { it.copy(isGenerating = false, generatedLink = link) }
            }.onFailure { e ->
                _state.update { it.copy(isGenerating = false) }
                _snackbar.emit("Failed to create Send: ${e.message}")
            }
        }
    }

    private fun getSendBaseUrl(serverUrl: String): String {
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        return when {
            cleanUrl.contains("bitwarden.eu") || cleanUrl.contains("api.eu.bitwarden.com") || cleanUrl.contains("identity.eu.bitwarden.com") || cleanUrl.contains("vault.eu.bitwarden.com") -> {
                "https://send.bitwarden.eu/#/send/"
            }
            cleanUrl.contains("api.bitwarden.com") || cleanUrl.contains("identity.bitwarden.com") || cleanUrl.contains("vault.bitwarden.com") -> {
                "https://send.bitwarden.com/#/send/"
            }
            else -> {
                val base = if (cleanUrl.endsWith("/api")) cleanUrl.removeSuffix("/api") else cleanUrl
                val finalBase = if (base.endsWith("/")) base else "$base/"
                "${finalBase}#/send/"
            }
        }
    }

    fun copyLink() {
        val link = _state.value.generatedLink ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Spectre Send Link", link))
        com.spectre.app.core.worker.ClipboardClearWorker.enqueue(context, 30L)
        viewModelScope.launch { _snackbar.emit("Link copied to clipboard") }
    }

    fun dismissLink() {
        _state.update { it.copy(generatedLink = null) }
    }
}
