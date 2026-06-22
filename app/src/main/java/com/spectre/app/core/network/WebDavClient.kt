package com.spectre.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(
        baseUrl: String,
        username: String,
        password: String,
        fileName: String,
        fileBytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fullUrl = if (baseUrl.endsWith("/")) {
                "$baseUrl$fileName"
            } else {
                "$baseUrl/$fileName"
            }

            val requestBody = fileBytes.toRequestBody("application/zip".toMediaType())
            val request = Request.Builder()
                .url(fullUrl)
                .put(requestBody)
                .header("Authorization", Credentials.basic(username, password))
                .header("User-Agent", "Spectre Android Backup")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("WebDAV upload failed with code ${response.code}: ${response.message}"))
                }
            }
        }.getOrElse {
            Result.failure(it)
        }
    }
}
