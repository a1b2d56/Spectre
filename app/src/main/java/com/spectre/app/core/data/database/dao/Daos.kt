package com.spectre.app.core.data.database.dao

import androidx.room.*
import com.spectre.app.core.data.database.entities.*
import kotlinx.coroutines.flow.Flow

// ── Account DAO ───────────────────────────────────────────────────────────────

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isActive DESC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE accounts SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE accounts SET lastSync = :timestamp WHERE id = :id")
    suspend fun updateLastSync(id: String, timestamp: Long)

    @Query("UPDATE accounts SET accessToken = :token, refreshToken = :refresh WHERE id = :id")
    suspend fun updateTokens(id: String, token: String, refresh: String?)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ── Cipher DAO ────────────────────────────────────────────────────────────────

@Dao
interface CipherDao {
    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND deletedDate IS NULL ORDER BY name ASC")
    fun observeAll(accountId: String): Flow<List<CipherEntity>>

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND deletedDate IS NOT NULL")
    fun observeTrash(accountId: String): Flow<List<CipherEntity>>

    @Query("SELECT * FROM ciphers WHERE id = :id")
    fun observeById(id: String): Flow<CipherEntity?>

    @Query("SELECT * FROM ciphers WHERE id = :id")
    suspend fun getById(id: String): CipherEntity?

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND type = :type AND deletedDate IS NULL")
    fun observeByType(accountId: String, type: Int): Flow<List<CipherEntity>>

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND favorite = 1 AND deletedDate IS NULL")
    fun observeFavorites(accountId: String): Flow<List<CipherEntity>>

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND folderId = :folderId AND deletedDate IS NULL")
    fun observeByFolder(accountId: String, folderId: String): Flow<List<CipherEntity>>

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND folderId IS NULL AND deletedDate IS NULL")
    fun observeNoFolder(accountId: String): Flow<List<CipherEntity>>

    @Query("""
        SELECT * FROM ciphers 
        WHERE accountId = :accountId 
        AND deletedDate IS NULL
        AND (
            name LIKE '%' || :query || '%' OR
            loginUsername LIKE '%' || :query || '%' OR
            loginUris LIKE '%' || :query || '%'
        )
    """)
    fun search(accountId: String, query: String): Flow<List<CipherEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cipher: CipherEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(ciphers: List<CipherEntity>)

    @Query("UPDATE ciphers SET favorite = NOT favorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE ciphers SET deletedDate = :date WHERE id = :id")
    suspend fun softDelete(id: String, date: String)

    @Query("UPDATE ciphers SET deletedDate = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM ciphers WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM ciphers WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("SELECT COUNT(*) FROM ciphers WHERE accountId = :accountId AND deletedDate IS NULL")
    suspend fun countAll(accountId: String): Int

    // Watchtower queries (operate on encrypted data — matching happens after decrypt in repo layer)
    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND type = 1 AND deletedDate IS NULL")
    suspend fun getAllLoginCiphers(accountId: String): List<CipherEntity>

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND pendingSync = 1")
    suspend fun getPendingSync(accountId: String): List<CipherEntity>
}

// ── Folder DAO ────────────────────────────────────────────────────────────────

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY name ASC")
    fun observeAll(accountId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId")
    suspend fun getAll(accountId: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM folders WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}

// ── Collection DAO ────────────────────────────────────────────────────────────

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections WHERE accountId = :accountId")
    fun observeAll(accountId: String): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(collections: List<CollectionEntity>)

    @Query("DELETE FROM collections WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}

// ── Organization DAO ──────────────────────────────────────────────────────────

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM organizations WHERE accountId = :accountId")
    fun observeAll(accountId: String): Flow<List<OrganizationEntity>>

    @Query("SELECT * FROM organizations WHERE id = :id")
    suspend fun getById(id: String): OrganizationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orgs: List<OrganizationEntity>)

    @Query("DELETE FROM organizations WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}

// ── Send DAO ──────────────────────────────────────────────────────────────────

@Dao
interface SendDao {
    @Query("SELECT * FROM sends WHERE accountId = :accountId ORDER BY revisionDate DESC")
    fun observeAll(accountId: String): Flow<List<SendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sends: List<SendEntity>)

    @Query("DELETE FROM sends WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sends WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}
