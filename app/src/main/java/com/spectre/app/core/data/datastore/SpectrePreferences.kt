package com.spectre.app.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.spectre.app.core.ui.theme.SpectreTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spectre_prefs")

data class SpectreSettings(
    val theme: SpectreTheme                = SpectreTheme.MIDNIGHT,
    val lockTimeout: LockTimeout           = LockTimeout.ONE_MINUTE,
    val lockOnBackground: Boolean          = true,
    val lockOnScreenOff: Boolean           = true,
    val biometricUnlock: Boolean           = true,
    val clipboardClearSeconds: Int         = 30,
    val screenshotProtection: Boolean      = true,
    val showFavicons: Boolean              = true,
    val defaultGeneratorLength: Int        = 16,
    val defaultGeneratorNumbers: Boolean   = true,
    val defaultGeneratorSymbols: Boolean   = true,
    val defaultGeneratorUppercase: Boolean = true,
    val defaultGeneratorLowercase: Boolean = true,
    val defaultGeneratorPassphrase: Boolean = false,
    val passphraseWordCount: Int           = 4,
    val passphraseSeparator: String        = "-",
    val twoFaOnCopyTotp: Boolean           = true,
    val showPasswordStrengthBar: Boolean   = true,
    val autofillSavePrompt: Boolean        = true,
    val duressEnabled: Boolean             = false,
    val backgroundSyncInterval: SyncInterval = SyncInterval.THIRTY_MINUTES,
    val showBottomNavLabels: Boolean       = true,
    val compactVaultList: Boolean          = false,
    val sortOrder: VaultSortOrder          = VaultSortOrder.NAME_ASC,
    val activeAccountId: String?           = null,
    val deviceId: String                   = "",
)

enum class LockTimeout(val seconds: Int, val label: String) {
    IMMEDIATE(0,      "Immediately"),
    FIFTEEN_SECONDS(15,   "15 seconds"),
    ONE_MINUTE(60,    "1 minute"),
    FIVE_MINUTES(300, "5 minutes"),
    FIFTEEN_MINUTES(900, "15 minutes"),
    ONE_HOUR(3600,    "1 hour"),
    NEVER(-1,         "Never"),
}

enum class SyncInterval(val minutes: Int, val label: String) {
    FIFTEEN_MINUTES(15,  "15 minutes"),
    THIRTY_MINUTES(30,   "30 minutes"),
    ONE_HOUR(60,         "1 hour"),
    FOUR_HOURS(240,      "4 hours"),
    MANUAL(-1,           "Manual only"),
}

enum class VaultSortOrder(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_MODIFIED("Last modified"),
    DATE_CREATED("Date created"),
    TYPE("Type"),
}

@Singleton
class SpectrePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME                    = stringPreferencesKey("theme")
        val LOCK_TIMEOUT             = stringPreferencesKey("lock_timeout")
        val LOCK_ON_BACKGROUND       = booleanPreferencesKey("lock_on_background")
        val LOCK_ON_SCREEN_OFF       = booleanPreferencesKey("lock_on_screen_off")
        val BIOMETRIC_UNLOCK         = booleanPreferencesKey("biometric_unlock")
        val CLIPBOARD_CLEAR_SECONDS  = intPreferencesKey("clipboard_clear_seconds")
        val SCREENSHOT_PROTECTION    = booleanPreferencesKey("screenshot_protection")
        val SHOW_FAVICONS            = booleanPreferencesKey("show_favicons")
        val GENERATOR_LENGTH         = intPreferencesKey("generator_length")
        val GENERATOR_NUMBERS        = booleanPreferencesKey("generator_numbers")
        val GENERATOR_SYMBOLS        = booleanPreferencesKey("generator_symbols")
        val GENERATOR_UPPERCASE      = booleanPreferencesKey("generator_uppercase")
        val GENERATOR_LOWERCASE      = booleanPreferencesKey("generator_lowercase")
        val GENERATOR_PASSPHRASE     = booleanPreferencesKey("generator_passphrase")
        val PASSPHRASE_WORD_COUNT    = intPreferencesKey("passphrase_word_count")
        val PASSPHRASE_SEPARATOR     = stringPreferencesKey("passphrase_separator")
        val SHOW_PASSWORD_STRENGTH   = booleanPreferencesKey("show_password_strength")
        val AUTOFILL_SAVE_PROMPT     = booleanPreferencesKey("autofill_save_prompt")
        val DURESS_ENABLED           = booleanPreferencesKey("duress_enabled")
        val SYNC_INTERVAL            = stringPreferencesKey("sync_interval")
        val SHOW_BOTTOM_NAV_LABELS   = booleanPreferencesKey("show_bottom_nav_labels")
        val COMPACT_VAULT_LIST       = booleanPreferencesKey("compact_vault_list")
        val SORT_ORDER               = stringPreferencesKey("sort_order")
        val ACTIVE_ACCOUNT_ID        = stringPreferencesKey("active_account_id")
        val DEVICE_ID                = stringPreferencesKey("device_id")
    }

    val settings: Flow<SpectreSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            SpectreSettings(
                theme                  = runCatching { SpectreTheme.valueOf(prefs[Keys.THEME] ?: "") }.getOrDefault(SpectreTheme.MIDNIGHT),
                lockTimeout            = runCatching { LockTimeout.valueOf(prefs[Keys.LOCK_TIMEOUT] ?: "") }.getOrDefault(LockTimeout.ONE_MINUTE),
                lockOnBackground       = prefs[Keys.LOCK_ON_BACKGROUND]      ?: true,
                lockOnScreenOff        = prefs[Keys.LOCK_ON_SCREEN_OFF]       ?: true,
                biometricUnlock        = prefs[Keys.BIOMETRIC_UNLOCK]         ?: true,
                clipboardClearSeconds  = prefs[Keys.CLIPBOARD_CLEAR_SECONDS]  ?: 30,
                screenshotProtection   = prefs[Keys.SCREENSHOT_PROTECTION]    ?: true,
                showFavicons           = prefs[Keys.SHOW_FAVICONS]            ?: true,
                defaultGeneratorLength = prefs[Keys.GENERATOR_LENGTH]         ?: 16,
                defaultGeneratorNumbers= prefs[Keys.GENERATOR_NUMBERS]        ?: true,
                defaultGeneratorSymbols= prefs[Keys.GENERATOR_SYMBOLS]        ?: true,
                defaultGeneratorUppercase = prefs[Keys.GENERATOR_UPPERCASE]   ?: true,
                defaultGeneratorLowercase = prefs[Keys.GENERATOR_LOWERCASE]   ?: true,
                defaultGeneratorPassphrase = prefs[Keys.GENERATOR_PASSPHRASE] ?: false,
                passphraseWordCount    = prefs[Keys.PASSPHRASE_WORD_COUNT]    ?: 4,
                passphraseSeparator    = prefs[Keys.PASSPHRASE_SEPARATOR]     ?: "-",
                showPasswordStrengthBar= prefs[Keys.SHOW_PASSWORD_STRENGTH]   ?: true,
                autofillSavePrompt     = prefs[Keys.AUTOFILL_SAVE_PROMPT]     ?: true,
                duressEnabled          = prefs[Keys.DURESS_ENABLED]           ?: false,
                backgroundSyncInterval = runCatching { SyncInterval.valueOf(prefs[Keys.SYNC_INTERVAL] ?: "") }.getOrDefault(SyncInterval.THIRTY_MINUTES),
                showBottomNavLabels    = prefs[Keys.SHOW_BOTTOM_NAV_LABELS]   ?: true,
                compactVaultList       = prefs[Keys.COMPACT_VAULT_LIST]       ?: false,
                sortOrder              = runCatching { VaultSortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: "") }.getOrDefault(VaultSortOrder.NAME_ASC),
                activeAccountId        = prefs[Keys.ACTIVE_ACCOUNT_ID],
                deviceId               = prefs[Keys.DEVICE_ID]                ?: "",
            )
        }

    suspend fun setTheme(theme: SpectreTheme) = update { it[Keys.THEME] = theme.name }
    suspend fun setLockTimeout(t: LockTimeout) = update { it[Keys.LOCK_TIMEOUT] = t.name }
    suspend fun setLockOnBackground(v: Boolean) = update { it[Keys.LOCK_ON_BACKGROUND] = v }
    suspend fun setBiometricUnlock(v: Boolean) = update { it[Keys.BIOMETRIC_UNLOCK] = v }
    suspend fun setClipboardClearSeconds(s: Int) = update { it[Keys.CLIPBOARD_CLEAR_SECONDS] = s }
    suspend fun setScreenshotProtection(v: Boolean) = update { it[Keys.SCREENSHOT_PROTECTION] = v }
    suspend fun setShowFavicons(v: Boolean) = update { it[Keys.SHOW_FAVICONS] = v }
    suspend fun setGeneratorLength(l: Int) = update { it[Keys.GENERATOR_LENGTH] = l }
    suspend fun setGeneratorNumbers(v: Boolean) = update { it[Keys.GENERATOR_NUMBERS] = v }
    suspend fun setGeneratorSymbols(v: Boolean) = update { it[Keys.GENERATOR_SYMBOLS] = v }
    suspend fun setGeneratorPassphrase(v: Boolean) = update { it[Keys.GENERATOR_PASSPHRASE] = v }
    suspend fun setPassphraseWordCount(n: Int) = update { it[Keys.PASSPHRASE_WORD_COUNT] = n }
    suspend fun setPassphraseSeparator(s: String) = update { it[Keys.PASSPHRASE_SEPARATOR] = s }
    suspend fun setCompactVaultList(v: Boolean) = update { it[Keys.COMPACT_VAULT_LIST] = v }
    suspend fun setSortOrder(o: VaultSortOrder) = update { it[Keys.SORT_ORDER] = o.name }
    suspend fun setActiveAccountId(id: String?) = update {
        if (id == null) it.remove(Keys.ACTIVE_ACCOUNT_ID) else it[Keys.ACTIVE_ACCOUNT_ID] = id
    }
    suspend fun setSyncInterval(i: SyncInterval) = update { it[Keys.SYNC_INTERVAL] = i.name }
    suspend fun setDeviceId(id: String) = update { it[Keys.DEVICE_ID] = id }
    suspend fun setDuressEnabled(v: Boolean) = update { it[Keys.DURESS_ENABLED] = v }
    suspend fun setShowBottomNavLabels(v: Boolean) = update { it[Keys.SHOW_BOTTOM_NAV_LABELS] = v }

    private suspend fun update(transform: (MutablePreferences) -> Unit) {
        context.dataStore.edit(transform)
    }
}
