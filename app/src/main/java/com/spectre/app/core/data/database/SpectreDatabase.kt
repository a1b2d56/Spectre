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

// Removed obsolete Converters class
@Database(
    entities = [
        AccountEntity::class,
        CipherEntity::class,
        FolderEntity::class,
        CollectionEntity::class,
        OrganizationEntity::class,
        SendEntity::class,
        GeneratorHistoryEntity::class,
        IgnoredWatchtowerItemEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class SpectreDatabase : RoomDatabase() {
    abstract fun accountDao():      AccountDao
    abstract fun cipherDao():       CipherDao
    abstract fun folderDao():       FolderDao
    abstract fun collectionDao():   CollectionDao
    abstract fun organizationDao(): OrganizationDao
    abstract fun sendDao():         SendDao
    abstract fun generatorHistoryDao(): GeneratorHistoryDao
    abstract fun ignoredWatchtowerDao(): IgnoredWatchtowerDao

    companion object {
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ignored_watchtower_items ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

// SQLCipher key management

object VaultKeyManager {

    private const val PREFS_FILE     = "spectre_vault_key"
    private const val KEY_DB_PASS    = "db_passphrase"
    private const val KEYSTORE_ALIAS = "spectre_master_key"

    @Suppress("DEPRECATION")
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
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_DB_PASS, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    fun buildSupportFactory(context: Context): SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        val passphrase = getOrCreateDatabaseKey(context)
        return SupportOpenHelperFactory(passphrase)
    }
}
