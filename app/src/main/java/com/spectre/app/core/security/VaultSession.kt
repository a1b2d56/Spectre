package com.spectre.app.core.security

import com.spectre.app.core.crypto.SymmetricKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class LockState { LOCKED, UNLOCKED, NO_ACCOUNT }

/**
 * Holds all in-memory session state for the active account.
 * Keys NEVER touch disk — they live only here until the vault is locked.
 */
@Singleton
class VaultSession @Inject constructor() {

    private val _lockState = MutableStateFlow(LockState.NO_ACCOUNT)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private var _userKey: SymmetricKey? = null
    private val _orgKeys = mutableMapOf<String, SymmetricKey>()

    var activeAccountId: String? = null
        private set

    var lastActivityTime: Long = System.currentTimeMillis()
        private set

    val isUnlocked: Boolean get() = _lockState.value == LockState.UNLOCKED

    fun getUserKey(): SymmetricKey =
        _userKey ?: error("Vault is locked — cannot access user key")

    fun getUserKeyOrNull(): SymmetricKey? = _userKey

    fun getOrgKey(orgId: String): SymmetricKey? = _orgKeys[orgId]

    fun unlock(
        accountId: String,
        userKey: SymmetricKey,
        orgKeys: Map<String, SymmetricKey> = emptyMap(),
    ) {
        activeAccountId = accountId
        _userKey = userKey
        _orgKeys.clear()
        _orgKeys.putAll(orgKeys)
        _lockState.value = LockState.UNLOCKED
        recordActivity()
    }

    fun recordActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    fun checkTimeout(timeoutSeconds: Int) {
        if (timeoutSeconds < 0) return // Never
        if (_lockState.value != LockState.UNLOCKED) return
        
        val elapsed = (System.currentTimeMillis() - lastActivityTime) / 1000
        if (elapsed >= timeoutSeconds) {
            lock()
        }
    }

    /** Merges in org keys loaded after a sync without re-locking. */
    fun addOrgKeys(orgKeys: Map<String, SymmetricKey>) {
        if (_lockState.value == LockState.UNLOCKED) {
            _orgKeys.putAll(orgKeys)
        }
    }

    fun lock() {
        _userKey?.destroy()
        _userKey = null
        _orgKeys.values.forEach { it.destroy() }
        _orgKeys.clear()
        _lockState.value = if (activeAccountId != null) LockState.LOCKED else LockState.NO_ACCOUNT
    }

    fun signOut() {
        lock()
        activeAccountId = null
        _lockState.value = LockState.NO_ACCOUNT
    }

    fun setNoAccount() {
        if (_lockState.value == LockState.UNLOCKED) return
        activeAccountId = null
        _lockState.value = LockState.NO_ACCOUNT
    }

    fun setAccountExists() {
        if (_lockState.value == LockState.NO_ACCOUNT) {
            _lockState.value = LockState.LOCKED
        }
    }
}
