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
 *
 * Locking the vault calls destroy() on all key material and clears
 * this session — forcing the user through biometric/PIN unlock again.
 */
@Singleton
class VaultSession @Inject constructor() {

    private val _lockState = MutableStateFlow(LockState.NO_ACCOUNT)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    /** The active account's decrypted 64-byte user symmetric key. */
    private var _userKey: SymmetricKey? = null

    /** Per-org keys: orgId → SymmetricKey */
    private val _orgKeys = mutableMapOf<String, SymmetricKey>()

    /** Active account ID */
    var activeAccountId: String? = null
        private set

    val isUnlocked: Boolean get() = _lockState.value == LockState.UNLOCKED

    fun getUserKey(): SymmetricKey =
        _userKey ?: error("Vault is locked — cannot access user key")

    fun getOrgKey(orgId: String): SymmetricKey? = _orgKeys[orgId]

    /**
     * Called after biometric/PIN unlock — stores decrypted keys in memory
     * and transitions to UNLOCKED state.
     */
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
    }

    /**
     * Locks the vault — wipes all key material from memory.
     * Called on timeout, screen off, background, or explicit user lock.
     */
    fun lock() {
        _userKey?.destroy()
        _userKey = null
        _orgKeys.values.forEach { it.destroy() }
        _orgKeys.clear()
        _lockState.value = if (activeAccountId != null) LockState.LOCKED else LockState.NO_ACCOUNT
    }

    /**
     * Full sign-out — clears everything including account ID.
     */
    fun signOut() {
        lock()
        activeAccountId = null
        _lockState.value = LockState.NO_ACCOUNT
    }

    fun setNoAccount() {
        signOut()
    }

    fun setAccountExists() {
        if (_lockState.value == LockState.NO_ACCOUNT) {
            _lockState.value = LockState.LOCKED
        }
    }
}
