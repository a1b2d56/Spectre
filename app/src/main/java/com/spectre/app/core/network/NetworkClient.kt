package com.spectre.app.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the Bearer token to every Vault API request.
 * On 401, attempts a single token refresh before failing.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Device-Type", "0")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        // 401 → attempt token refresh once
        if (response.code == 401 && tokenStore.refreshToken != null) {
            response.close()
            return runBlocking {
                val refreshed = tokenStore.refreshAccessToken()
                val retryRequest = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${refreshed ?: token}")
                    .build()
                chain.proceed(retryRequest)
            }
        }

        return response
    }
}

/**
 * Holds access/refresh tokens in memory (never on disk unencrypted).
 * Persisted to EncryptedSharedPreferences via VaultSession.
 */
@Singleton
class TokenStore @Inject constructor() {
    var accessToken: String?  = null
    var refreshToken: String? = null
    var serverUrl: String     = "https://api.bitwarden.com"
    var identityUrl: String   = "https://identity.bitwarden.com"

    // Injected lazily to avoid circular DI — set by VaultSession after login
    var refreshCallback: (suspend () -> String?)? = null

    suspend fun refreshAccessToken(): String? = refreshCallback?.invoke()

    fun clear() {
        accessToken  = null
        refreshToken = null
        refreshCallback = null
    }
}

fun buildOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        // Custom server URL support — Vaultwarden / self-hosted
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "Spectre/1.0 Android")
                    .build()
            )
        }
        .build()
