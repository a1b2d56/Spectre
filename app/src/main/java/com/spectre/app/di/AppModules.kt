package com.spectre.app.di

import android.content.Context
import androidx.room.Room
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.spectre.app.core.data.database.*
import com.spectre.app.core.data.database.dao.*
import com.spectre.app.core.network.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

// ── JSON ──────────────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object JsonModule {
    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys  = true
        isLenient          = true
        encodeDefaults     = false
        coerceInputValues  = true
    }
}

// ── Network ───────────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor) =
        buildOkHttpClient(authInterceptor)

    @Provides @Singleton @Named("vault")
    fun provideVaultRetrofit(
        okHttpClient: okhttp3.OkHttpClient,
        tokenStore: TokenStore,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(tokenStore.serverUrl + "/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton @Named("identity")
    fun provideIdentityRetrofit(
        okHttpClient: okhttp3.OkHttpClient,
        tokenStore: TokenStore,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(tokenStore.identityUrl + "/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun provideVaultApi(@Named("vault") retrofit: Retrofit): VaultApi =
        retrofit.create(VaultApi::class.java)

    @Provides @Singleton
    fun provideIdentityApi(@Named("identity") retrofit: Retrofit): IdentityApi =
        retrofit.create(IdentityApi::class.java)
}

// ── Database ──────────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpectreDatabase =
        Room.databaseBuilder(context, SpectreDatabase::class.java, "spectre_vault.db")
            .openHelperFactory(VaultKeyManager.buildSupportFactory(context))
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()

    @Provides fun provideAccountDao(db: SpectreDatabase): AccountDao = db.accountDao()
    @Provides fun provideCipherDao(db: SpectreDatabase): CipherDao = db.cipherDao()
    @Provides fun provideFolderDao(db: SpectreDatabase): FolderDao = db.folderDao()
    @Provides fun provideCollectionDao(db: SpectreDatabase): CollectionDao = db.collectionDao()
    @Provides fun provideOrganizationDao(db: SpectreDatabase): OrganizationDao = db.organizationDao()
    @Provides fun provideSendDao(db: SpectreDatabase): SendDao = db.sendDao()
}
