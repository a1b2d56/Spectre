package com.spectre.app.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single most important fix in this codebase.
 *
 * Previously Retrofit was created as a singleton with a baked-in base URL from
 * TokenStore at DI init time — meaning it was always "https://api.bitwarden.com"
 * regardless of what region/server the user picked at login.
 *
 * This interceptor rewrites the host/port/scheme on every outgoing request using
 * the current live value from TokenStore, so EU, custom, and Vaultwarden servers
 * all work correctly without recreating the Retrofit instance.
 *
 * Identity endpoints (prelogin, connect/token) → tokenStore.identityUrl
 * All other endpoints (sync, ciphers, etc.)    → tokenStore.serverUrl
 */
@Singleton
class DynamicUrlInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path     = original.url.encodedPath

        val baseUrl = if (isIdentityPath(path)) {
            tokenStore.identityUrl
        } else {
            tokenStore.serverUrl
        }.trimEnd('/')

        val parsed = baseUrl.toHttpUrlOrNull()
            ?: return chain.proceed(original) // fallback: proceed as-is

        val newUrl = original.url.newBuilder()
            .scheme(parsed.scheme)
            .host(parsed.host)
            .port(parsed.port)
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }

    private fun isIdentityPath(path: String): Boolean =
        path.contains("/connect/") ||
        path.contains("/accounts/prelogin") ||
        path.contains("/accounts/register") ||
        path.contains("/accounts/password-hint")
}

/**
 * Attaches Bearer token and handles 401 → refresh.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token   = tokenStore.accessToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Device-Type", "8")   // 8 = Android
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        if (response.code == 401 && tokenStore.refreshToken != null) {
            response.close()
            return runBlocking {
                val refreshed = tokenStore.refreshAccessToken()
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer ${refreshed ?: token}")
                        .build()
                )
            }
        }
        return response
    }
}

@Singleton
class TokenStore @Inject constructor() {
    var accessToken: String?  = null
    var refreshToken: String? = null

    // Default to US; overwritten immediately on every login attempt
    var serverUrl: String   = "https://api.bitwarden.com"
    var identityUrl: String = "https://identity.bitwarden.com"

    var refreshCallback: (suspend () -> String?)? = null

    suspend fun refreshAccessToken(): String? = refreshCallback?.invoke()

    fun clear() {
        accessToken     = null
        refreshToken    = null
        refreshCallback = null
    }
}

fun buildOkHttpClient(
    authInterceptor: AuthInterceptor,
    dynamicUrlInterceptor: DynamicUrlInterceptor,
): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60,  TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    // Order matters: dynamic URL first, then auth header, then logging
    .addInterceptor(dynamicUrlInterceptor)
    .addInterceptor(authInterceptor)
    .addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("Accept",                    "application/json")
                .header("User-Agent",                "Bitwarden-Mobile/2024.1.0 (Android)")
                .header("Bitwarden-Client-Name",     "Android")
                .header("Bitwarden-Client-Version",  "2024.1.0")
                .build()
        )
    }
    .addInterceptor(
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    )
    .build()
