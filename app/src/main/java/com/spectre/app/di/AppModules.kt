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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JsonModule {
    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient         = true
        encodeDefaults    = false
        coerceInputValues = true
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        dynamicUrlInterceptor: DynamicUrlInterceptor,
    ) = buildOkHttpClient(authInterceptor, dynamicUrlInterceptor)

    /**
     * Single Retrofit instance with a placeholder base URL.
     * DynamicUrlInterceptor rewrites the host on every request so
     * US/EU/custom/Vaultwarden all work without recreating this instance.
     */
    @Provides @Singleton
    fun provideRetrofit(
        okHttpClient: okhttp3.OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.bitwarden.com/")   // placeholder; DynamicUrlInterceptor takes over
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun provideVaultApi(retrofit: Retrofit): VaultApi =
        retrofit.create(VaultApi::class.java)

    @Provides @Singleton
    fun provideIdentityApi(retrofit: Retrofit): IdentityApi =
        retrofit.create(IdentityApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpectreDatabase =
        Room.databaseBuilder(context, SpectreDatabase::class.java, "spectre_vault.db")
            .openHelperFactory(VaultKeyManager.buildSupportFactory(context))
            .addMigrations(SpectreDatabase.MIGRATION_2_3, SpectreDatabase.MIGRATION_3_4)
            .build()

    @Provides fun provideAccountDao(db: SpectreDatabase):      AccountDao      = db.accountDao()
    @Provides fun provideCipherDao(db: SpectreDatabase):       CipherDao       = db.cipherDao()
    @Provides fun provideFolderDao(db: SpectreDatabase):       FolderDao       = db.folderDao()
    @Provides fun provideCollectionDao(db: SpectreDatabase):   CollectionDao   = db.collectionDao()
    @Provides fun provideOrganizationDao(db: SpectreDatabase): OrganizationDao = db.organizationDao()
    @Provides fun provideSendDao(db: SpectreDatabase):         SendDao         = db.sendDao()
    @Provides fun provideGeneratorHistoryDao(db: SpectreDatabase): com.spectre.app.core.data.database.dao.GeneratorHistoryDao = db.generatorHistoryDao()
    @Provides fun provideIgnoredWatchtowerDao(db: SpectreDatabase): com.spectre.app.core.data.database.dao.IgnoredWatchtowerDao = db.ignoredWatchtowerDao()
}
