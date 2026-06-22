package com.spectre.app.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.spectre.app.core.ui.theme.SpectreTheme
import com.spectre.app.core.data.models.UriMatchType
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
    val biometricUnlock: Boolean           = false,
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
    val watchtowerCheckHibp: Boolean       = true,
    val watchtowerScanReused: Boolean      = true,
    val watchtowerScanWeak: Boolean        = true,
    val watchtowerScan2fa: Boolean         = true,
    val panicPin: String?                  = null,
    
    // Advanced Autofill
    val autofillCredentialProvider: Boolean = true,
    val autofillInlineSuggestions: Boolean  = true,
    val autofillManualSelection: Boolean    = true,
    val autofillRespectDisabled: Boolean    = false,
    val autofillAutoCopyOtp: Boolean        = true,
    val autofillOtpNotificationDuration: Int = 30,
    val autofillAutoSave: Boolean           = false,
    val autofillAskToSave: Boolean          = true,
    val autofillMatchDetection: UriMatchType = UriMatchType.DOMAIN,
    val autofillBlacklist: Set<String>      = emptySet(),
    val autofillPrivilegedApps: Set<String> = emptySet(),
    val customServerUrl: String             = "",
    
    // Backups & SSH Agent
    val sshAgentEnabled: Boolean            = false,
    val backupEnabled: Boolean              = false,
    val backupLocalPath: String             = "",
    val backupWebDavUrl: String             = "",
    val backupWebDavUsername: String        = "",
    val backupWebDavPassword: String        = "",
    val backupIntervalHours: Int            = 24,
    val backupPassword: String              = "",
    val fontScale: Float                    = 1.0f,
    val isBold: Boolean                     = false,
    val fontFamily: String                  = "default",
    val linkCleanerEnabled: Boolean         = true,
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
    @param:ApplicationContext private val context: Context,
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
        val WT_HIBP                  = booleanPreferencesKey("wt_hibp")
        val WT_REUSED                = booleanPreferencesKey("wt_reused")
        val WT_WEAK                  = booleanPreferencesKey("wt_weak")
        val WT_2FA                   = booleanPreferencesKey("wt_2fa")
        val PANIC_PIN                = stringPreferencesKey("panic_pin")
        
        val AF_CRED_PROV             = booleanPreferencesKey("af_cred_prov")
        val AF_INLINE                = booleanPreferencesKey("af_inline")
        val AF_MANUAL                = booleanPreferencesKey("af_manual")
        val AF_RESPECT_DISABLED      = booleanPreferencesKey("af_respect_disabled")
        val AF_AUTO_COPY_OTP         = booleanPreferencesKey("af_auto_copy_otp")
        val AF_OTP_NOTIF_DUR         = intPreferencesKey("af_otp_notif_dur")
        val AF_AUTO_SAVE             = booleanPreferencesKey("af_auto_save")
        val AF_ASK_SAVE              = booleanPreferencesKey("af_ask_save")
        val AF_MATCH_DETECTION       = stringPreferencesKey("af_match_detection")
        val AF_BLACKLIST             = stringSetPreferencesKey("af_blacklist")
        val AF_PRIVILEGED_APPS       = stringSetPreferencesKey("af_privileged_apps")
        val CUSTOM_SERVER_URL        = stringPreferencesKey("custom_server_url")
        
        val SSH_AGENT_ENABLED       = booleanPreferencesKey("ssh_agent_enabled")
        val BACKUP_ENABLED           = booleanPreferencesKey("backup_enabled")
        val BACKUP_LOCAL_PATH        = stringPreferencesKey("backup_local_path")
        val BACKUP_WEBDAV_URL        = stringPreferencesKey("backup_webdav_url")
        val BACKUP_WEBDAV_USERNAME   = stringPreferencesKey("backup_webdav_username")
        val BACKUP_WEBDAV_PASSWORD   = stringPreferencesKey("backup_webdav_password")
        val BACKUP_INTERVAL_HOURS    = intPreferencesKey("backup_interval_hours")
        val BACKUP_PASSWORD          = stringPreferencesKey("backup_password")
        val FONT_SCALE               = floatPreferencesKey("font_scale")
        val IS_BOLD                  = booleanPreferencesKey("is_bold")
        val FONT_FAMILY              = stringPreferencesKey("font_family")
        val LINK_CLEANER_ENABLED     = booleanPreferencesKey("link_cleaner_enabled")
    }

    val settings: Flow<SpectreSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            SpectreSettings(
                theme                  = runCatching { SpectreTheme.valueOf(prefs[Keys.THEME] ?: "") }.getOrDefault(SpectreTheme.MIDNIGHT),
                lockTimeout            = runCatching { LockTimeout.valueOf(prefs[Keys.LOCK_TIMEOUT] ?: "") }.getOrDefault(LockTimeout.ONE_MINUTE),
                lockOnBackground       = prefs[Keys.LOCK_ON_BACKGROUND]      ?: true,
                lockOnScreenOff        = prefs[Keys.LOCK_ON_SCREEN_OFF]       ?: true,
                biometricUnlock        = prefs[Keys.BIOMETRIC_UNLOCK]         ?: false,
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
                watchtowerCheckHibp    = prefs[Keys.WT_HIBP]                  ?: true,
                watchtowerScanReused   = prefs[Keys.WT_REUSED]                ?: true,
                watchtowerScanWeak     = prefs[Keys.WT_WEAK]                  ?: true,
                watchtowerScan2fa      = prefs[Keys.WT_2FA]                   ?: true,
                panicPin               = prefs[Keys.PANIC_PIN],
                
                autofillCredentialProvider = prefs[Keys.AF_CRED_PROV]         ?: true,
                autofillInlineSuggestions  = prefs[Keys.AF_INLINE]            ?: true,
                autofillManualSelection    = prefs[Keys.AF_MANUAL]            ?: true,
                autofillRespectDisabled    = prefs[Keys.AF_RESPECT_DISABLED]    ?: false,
                autofillAutoCopyOtp        = prefs[Keys.AF_AUTO_COPY_OTP]        ?: true,
                autofillOtpNotificationDuration = prefs[Keys.AF_OTP_NOTIF_DUR] ?: 30,
                autofillAutoSave           = prefs[Keys.AF_AUTO_SAVE]           ?: false,
                autofillAskToSave          = prefs[Keys.AF_ASK_SAVE]            ?: true,
                autofillMatchDetection     = runCatching<UriMatchType> { UriMatchType.valueOf(prefs[Keys.AF_MATCH_DETECTION] ?: "") }.getOrDefault(UriMatchType.DOMAIN),
                autofillBlacklist          = prefs[Keys.AF_BLACKLIST]           ?: emptySet(),
                autofillPrivilegedApps      = prefs[Keys.AF_PRIVILEGED_APPS]     ?: emptySet(),
                customServerUrl            = prefs[Keys.CUSTOM_SERVER_URL]      ?: "",
                
                sshAgentEnabled        = prefs[Keys.SSH_AGENT_ENABLED]      ?: false,
                backupEnabled          = prefs[Keys.BACKUP_ENABLED]          ?: false,
                backupLocalPath        = prefs[Keys.BACKUP_LOCAL_PATH]        ?: "",
                backupWebDavUrl        = prefs[Keys.BACKUP_WEBDAV_URL]        ?: "",
                backupWebDavUsername   = prefs[Keys.BACKUP_WEBDAV_USERNAME]   ?: "",
                backupWebDavPassword   = prefs[Keys.BACKUP_WEBDAV_PASSWORD]   ?: "",
                backupIntervalHours    = prefs[Keys.BACKUP_INTERVAL_HOURS]    ?: 24,
                backupPassword          = prefs[Keys.BACKUP_PASSWORD]          ?: "",
                fontScale              = prefs[Keys.FONT_SCALE]               ?: 1.0f,
                isBold                 = prefs[Keys.IS_BOLD]                  ?: false,
                fontFamily             = prefs[Keys.FONT_FAMILY]              ?: "default",
                linkCleanerEnabled     = prefs[Keys.LINK_CLEANER_ENABLED]     ?: true,
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

    suspend fun setWtHibp(v: Boolean) = update { it[Keys.WT_HIBP] = v }
    suspend fun setWtReused(v: Boolean) = update { it[Keys.WT_REUSED] = v }
    suspend fun setWtWeak(v: Boolean) = update { it[Keys.WT_WEAK] = v }
    suspend fun setWt2fa(v: Boolean) = update { it[Keys.WT_2FA] = v }
    suspend fun setPanicPin(pin: String?) = update {
        if (pin == null) it.remove(Keys.PANIC_PIN) else it[Keys.PANIC_PIN] = pin
    }

    suspend fun setAfCredentialProvider(v: Boolean) = update { it[Keys.AF_CRED_PROV] = v }
    suspend fun setAfInline(v: Boolean)             = update { it[Keys.AF_INLINE] = v }
    suspend fun setAfManual(v: Boolean)             = update { it[Keys.AF_MANUAL] = v }
    suspend fun setAfRespectDisabled(v: Boolean)    = update { it[Keys.AF_RESPECT_DISABLED] = v }
    suspend fun setAfAutoCopyOtp(v: Boolean)        = update { it[Keys.AF_AUTO_COPY_OTP] = v }
    suspend fun setAfOtpNotifDur(d: Int)            = update { it[Keys.AF_OTP_NOTIF_DUR] = d }
    suspend fun setAfAutoSave(v: Boolean)           = update { it[Keys.AF_AUTO_SAVE] = v }
    suspend fun setAfAskSave(v: Boolean)            = update { it[Keys.AF_ASK_SAVE] = v }
    suspend fun setAfMatchDetection(m: UriMatchType) = update { it[Keys.AF_MATCH_DETECTION] = m.toString() }
    suspend fun setAfBlacklist(uris: Set<String>)   = update { it[Keys.AF_BLACKLIST] = uris }
    suspend fun setAfPrivilegedApps(pkgs: Set<String>) = update { it[Keys.AF_PRIVILEGED_APPS] = pkgs }
    suspend fun setCustomServerUrl(url: String) = update { it[Keys.CUSTOM_SERVER_URL] = url }

    suspend fun setSshAgentEnabled(v: Boolean) = update { it[Keys.SSH_AGENT_ENABLED] = v }
    suspend fun setBackupEnabled(v: Boolean) = update { it[Keys.BACKUP_ENABLED] = v }
    suspend fun setBackupLocalPath(path: String) = update { it[Keys.BACKUP_LOCAL_PATH] = path }
    suspend fun setBackupWebDavUrl(url: String) = update { it[Keys.BACKUP_WEBDAV_URL] = url }
    suspend fun setBackupWebDavUsername(u: String) = update { it[Keys.BACKUP_WEBDAV_USERNAME] = u }
    suspend fun setBackupWebDavPassword(p: String) = update { it[Keys.BACKUP_WEBDAV_PASSWORD] = p }
    suspend fun setBackupIntervalHours(h: Int) = update { it[Keys.BACKUP_INTERVAL_HOURS] = h }
    suspend fun setBackupPassword(p: String) = update { it[Keys.BACKUP_PASSWORD] = p }
    suspend fun setFontScale(s: Float) = update { it[Keys.FONT_SCALE] = s }
    suspend fun setIsBold(b: Boolean) = update { it[Keys.IS_BOLD] = b }
    suspend fun setFontFamily(f: String) = update { it[Keys.FONT_FAMILY] = f }
    suspend fun setLinkCleanerEnabled(v: Boolean) = update { it[Keys.LINK_CLEANER_ENABLED] = v }

    private suspend fun update(transform: (MutablePreferences) -> Unit) {
        context.dataStore.edit(transform)
    }
}

