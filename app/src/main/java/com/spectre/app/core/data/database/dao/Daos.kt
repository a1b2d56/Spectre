package com.spectre.app.core.data.database.dao

import androidx.room.*
import com.spectre.app.core.data.database.entities.*
import kotlinx.coroutines.flow.Flow

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

    @Query("UPDATE accounts SET premium = :premium WHERE id = :id")
    suspend fun updatePremiumStatus(id: String, premium: Boolean)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

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

    @Query("UPDATE ciphers SET favorite = CASE WHEN favorite = 1 THEN 0 ELSE 1 END WHERE id = :id")
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

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND type = 1 AND deletedDate IS NULL")
    suspend fun getAllLoginCiphers(accountId: String): List<CipherEntity>

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId AND pendingSync = 1")
    suspend fun getPendingSync(accountId: String): List<CipherEntity>

    @Query("SELECT * FROM ciphers WHERE accountId = :accountId")
    suspend fun getAllForAccount(accountId: String): List<CipherEntity>

    @Query("DELETE FROM ciphers WHERE accountId = :accountId AND deletedDate IS NOT NULL")
    suspend fun purgeTrash(accountId: String)

    @Query("DELETE FROM ciphers")
    suspend fun deleteAll()
}

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

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections WHERE accountId = :accountId")
    fun observeAll(accountId: String): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(collections: List<CollectionEntity>)

    @Query("DELETE FROM collections WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("DELETE FROM collections")
    suspend fun deleteAll()
}

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM organizations WHERE accountId = :accountId")
    fun observeAll(accountId: String): Flow<List<OrganizationEntity>>

    @Query("SELECT * FROM organizations WHERE accountId = :accountId")
    suspend fun getAll(accountId: String): List<OrganizationEntity>

    @Query("SELECT * FROM organizations WHERE id = :id")
    suspend fun getById(id: String): OrganizationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orgs: List<OrganizationEntity>)

    @Query("DELETE FROM organizations WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("DELETE FROM organizations")
    suspend fun deleteAll()
}

@Dao
interface SendDao {
    @Query("SELECT * FROM sends WHERE accountId = :accountId ORDER BY revisionDate DESC")
    fun observeAll(accountId: String): Flow<List<SendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(send: SendEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sends: List<SendEntity>)

    @Query("DELETE FROM sends WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sends WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("DELETE FROM sends")
    suspend fun deleteAll()
}

@Dao
interface GeneratorHistoryDao {
    @Query("SELECT * FROM generator_history ORDER BY timestamp DESC LIMIT 50")
    fun observeHistory(): Flow<List<GeneratorHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: GeneratorHistoryEntity)

    @Query("DELETE FROM generator_history")
    suspend fun clearAll()
}

@Dao
interface IgnoredWatchtowerDao {
    @Query("SELECT * FROM ignored_watchtower_items WHERE accountId = :accountId")
    fun observeAll(accountId: String): Flow<List<IgnoredWatchtowerItemEntity>>
    
    @Query("SELECT * FROM ignored_watchtower_items WHERE accountId = :accountId")
    suspend fun getAll(accountId: String): List<IgnoredWatchtowerItemEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IgnoredWatchtowerItemEntity)
    
    @Query("DELETE FROM ignored_watchtower_items WHERE accountId = :accountId AND cipherId = :cipherId AND issueType = :issueType")
    suspend fun remove(accountId: String, cipherId: String, issueType: String)

    @Query("DELETE FROM ignored_watchtower_items")
    suspend fun deleteAll()
}
