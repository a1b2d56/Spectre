package com.spectre.app.core.data.database

import android.content.Context
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.spectre.app.core.data.database.dao.*
import com.spectre.app.core.data.database.entities.*
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom
import android.util.Base64

// ── Type Converters ───────────────────────────────────────────────────────────

class Converters {
    @TypeConverter fun fromList(value: List<String>?): String? = value?.joinToString(",")
    @TypeConverter fun toList(value: String?): List<String>? = value?.split(",")?.filter { it.isNotEmpty() }
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [
        AccountEntity::class,
        CipherEntity::class,
        FolderEntity::class,
        CollectionEntity::class,
        OrganizationEntity::class,
        SendEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SpectreDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun cipherDao(): CipherDao
    abstract fun folderDao(): FolderDao
    abstract fun collectionDao(): CollectionDao
    abstract fun organizationDao(): OrganizationDao
    abstract fun sendDao(): SendDao
}

// ── SQLCipher Key Manager ─────────────────────────────────────────────────────

/**
 * Generates a cryptographically random database passphrase and stores it
 * in Keystore-backed EncryptedSharedPreferences — identical to Planora's
 * DatabaseKeyManager but namespaced for Spectre.
 */
object VaultKeyManager {

    private const val PREFS_FILE    = "spectre_vault_key"
    private const val KEY_DB_PASS   = "db_passphrase"
    private const val KEYSTORE_ALIAS = "spectre_master_key"

    fun getOrCreateDatabaseKey(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context, KEYSTORE_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(false)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val existing = prefs.getString(KEY_DB_PASS, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        // First launch — generate 32 random bytes
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_DB_PASS, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    fun buildSupportFactory(context: Context): SupportSQLiteOpenHelper.Factory {
        val passphrase = getOrCreateDatabaseKey(context)
        return SupportOpenHelperFactory(passphrase)
    }
}
