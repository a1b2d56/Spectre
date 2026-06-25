package com.spectre.app.core.data.repository

import com.spectre.app.core.data.models.GithubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): Result<GithubRelease?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/a1b2d56/Spectre/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Spectre-App-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Unexpected response code: ${response.code}"))
                }
                val bodyString = response.body.string()
                val release = json.decodeFromString<GithubRelease>(bodyString)
                Result.success(release)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.removePrefix("v").substringBefore("-").trim()
        val cleanLatest = latest.removePrefix("v").substringBefore("-").trim()
        
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val curr = currentParts.getOrNull(i) ?: 0
            val lat = latestParts.getOrNull(i) ?: 0
            if (lat > curr) return true
            if (curr > lat) return false
        }
        return false
    }
}
