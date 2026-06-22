package com.spectre.app.core.network

import android.net.Uri
import android.text.util.Linkify
import androidx.core.util.PatternsCompat
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object LinkCleaner {
    private val client = OkHttpClient.Builder()
        .followRedirects(false) // Handle redirects manually
        .build()

    private val urlWithIgnoredSchemeRegex = Regex("(rtsp|ftp)://.*", RegexOption.IGNORE_CASE)
    private val urlWithSchemeRegex = Regex("https?://.*", RegexOption.IGNORE_CASE)

    suspend fun untrack(text: String): String = withContext(Dispatchers.IO) {
        val urlInfos = findUrls(text)
        if (urlInfos.isEmpty()) {
            return@withContext text
        }
        val untrackedTextBuilder = java.lang.StringBuilder(text)
        // Process in reverse order to keep indices valid when replacing
        for (urlInfo in urlInfos.reversed()) {
            var untrackedUrl = urlInfo.url
            
            // Step 1: Follow redirects if it's a short link
            if (isShortLink(untrackedUrl)) {
                untrackedUrl = resolveShortLink(untrackedUrl)
            }
            
            // Step 2: Apply the tracking parameter stripping rules
            untrackedUrl = cleanUrl(untrackedUrl)
            
            if (!urlInfo.hasScheme) {
                untrackedUrl = untrackedUrl.removePrefix("http://").removePrefix("https://")
            }
            
            untrackedTextBuilder.replace(urlInfo.start, urlInfo.end, untrackedUrl)
        }
        untrackedTextBuilder.toString()
    }

    private data class UrlInfo(val start: Int, val end: Int, val hasScheme: Boolean, val url: String)

    private fun findUrls(text: String): List<UrlInfo> {
        val matcher = PatternsCompat.AUTOLINK_WEB_URL.matcher(text)
        val urlInfos = mutableListOf<UrlInfo>()
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val match = matcher.group(0)!!
            if (Linkify.sUrlMatchFilter.acceptMatch(text, start, end) &&
                !urlWithIgnoredSchemeRegex.matches(match)
            ) {
                val hasScheme = urlWithSchemeRegex.matches(match)
                var url = if (hasScheme) match else "http://$match"
                try {
                    val uri = Uri.parse(url)
                    if (uri.isHierarchical && uri.isAbsolute) {
                        url = URI(url).normalize().toString()
                        urlInfos.add(UrlInfo(start = start, end = end, hasScheme = hasScheme, url = url))
                    }
                } catch (e: Exception) {
                    // Ignore malformed URIs
                }
            }
        }
        return urlInfos
    }

    private fun isShortLink(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host ?: return false
        val path = uri.path ?: ""
        return host.matches(Regex("""^(163cn\.tv|a\.co|amzn\.(asia|eu|to)|b23\.tv|bili2233\.cn|v\.douyin\.com|dwz\.cn|u\.jd\.com|v\.kuaishou\.com|pin\.it|share\.google|t\.cn|vm\.tiktok\.com|url\.cn|xhslink\.com)$""", RegexOption.IGNORE_CASE)) ||
            (host == "m.gifshow.com" && path.startsWith("/s/")) ||
            (host == "www.google.com" && path == "/share.google") ||
            (host == "www.instagram.com" && path.startsWith("/share/reel/")) ||
            (host == "api.pinterest.com" && path.startsWith("/url_shortener/")) ||
            (host == "www.reddit.com" && path.matches(Regex("""^/r/[^/]+/s/.*"""))) ||
            (host == "search.app") ||
            host.endsWith("tb.cn")
    }

    private fun resolveShortLink(url: String): String {
        var currentUrl = url
        var redirects = 0
        val maxRedirects = 5
        
        while (redirects < maxRedirects) {
            try {
                val uri = Uri.parse(currentUrl)
                val host = uri.host ?: break
                
                if (host.endsWith("tb.cn")) {
                    val request = Request.Builder().url(currentUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body.string()
                            val match = Regex("""var url = '([^']+)';""").find(body)
                            if (match != null) {
                                currentUrl = match.groupValues[1]
                                redirects++
                                continue
                            }
                        }
                    }
                    break
                }
                
                val request = Request.Builder().url(currentUrl).head().build()
                client.newCall(request).execute().use { response ->
                    if (response.code in 300..399) {
                        val location = response.header("Location")
                        if (!location.isNullOrEmpty()) {
                            currentUrl = if (location.startsWith("/")) {
                                val baseUri = Uri.parse(currentUrl)
                                "${baseUri.scheme}://${baseUri.authority}$location"
                            } else {
                                location
                            }
                            redirects++
                            continue
                        }
                    }
                }
            } catch (e: Exception) {
                break
            }
            break
        }
        return currentUrl
    }

    private fun cleanUrl(urlStr: String): String {
        try {
            var uri = Uri.parse(urlStr)
            val host = uri.host ?: return urlStr
            val path = uri.path ?: ""

            // 1. Common redirections
            if (host.endsWith("douban.com") && path == "/link2/" && uri.getQueryParameter("url") != null) {
                return cleanUrl(uri.getQueryParameter("url")!!)
            }
            if (host == "search.app" && path == "/" && uri.getQueryParameter("link") != null) {
                return cleanUrl(uri.getQueryParameter("link")!!)
            }
            if (host == "link.zhihu.com" && path == "/" && uri.getQueryParameter("target") != null) {
                return cleanUrl(uri.getQueryParameter("target")!!)
            }

            // 2. Amazon
            if (host.matches(Regex(""".+\.amazon\.(ae|ca|cn|co\.jp|co\.uk|com|com\.au|com\.be|com\.br|com\.mx|com\.tr|de|eg|es|fr|in|it|nl|pl|sa|se|sg)""", RegexOption.IGNORE_CASE))) {
                uri = uri.buildUpon().clearQuery().build()
                val newPath = path.replace(Regex("""/ref=[^/]+$""", RegexOption.IGNORE_CASE), "")
                if (path != newPath) {
                    uri = uri.buildUpon().path(newPath).build()
                }
                return uri.toString()
            }

            // 3. Bilibili
            if (host.endsWith("bilibili.com")) {
                uri = retainQueryParameters(uri, setOf("business_id", "business_type", "itemsId", "lottery_id", "p", "start_progress", "t"))
                if (uri.getQueryParameter("p") == "1") {
                    uri = removeQueryParameter(uri, "p")
                }
                return uri.toString()
            }

            // 4. Douban, Douyin, Instagram, Kuaishou, Netflix, Spotify, TikTok, X/Twitter
            if (host.endsWith("douban.com") || host.endsWith("douyin.com") || host.endsWith("iesdouyin.com") ||
                host.endsWith("instagram.com") || host.endsWith("chenzhongtech.com") || host.endsWith("gifshow.com") ||
                host.endsWith("kuaishou.com") || host.endsWith("netflix.com") || host.endsWith("spotify.com") ||
                host.endsWith("tiktok.com") || host.endsWith("twitter.com") || host.endsWith("x.com")) {
                return uri.buildUpon().clearQuery().build().toString()
            }

            // 5. Google Search
            if (host == "www.google.com" && path == "/search") {
                return retainQueryParameters(uri, setOf("q", "tbm")).toString()
            }

            // 6. Pinterest
            if (host == "www.pinterest.com") {
                uri = uri.buildUpon().clearQuery().build()
                if (path.endsWith("/sent") || path.endsWith("/sent/")) {
                    uri = uri.buildUpon().path(path.replace(Regex("""/sent/?$"""), "")).build()
                }
                return uri.toString()
            }

            // 7. Reddit
            if (host.endsWith("reddit.com")) {
                return retainQueryParameters(uri, setOf("context")).toString()
            }

            // 8. Stack Exchange
            if (host.matches(Regex(""".+(\.stackexchange|askubuntu|serverfault|stackoverflow|superuser)\.com""", RegexOption.IGNORE_CASE))) {
                val newPath = path.replace(Regex("""/([aq]/[0-9]+)/[0-9]+/?$"""), "/$1")
                if (path != newPath) {
                    uri = uri.buildUpon().path(newPath).build()
                }
                return uri.toString()
            }

            // 9. Taobao / Tmall
            if (host.endsWith("taobao.com") || host.endsWith("tmall.com")) {
                return retainQueryParameters(uri, setOf("id")).toString()
            }

            // 10. Threads
            if (host == "www.threads.com") {
                return removeQueryParameters(uri, setOf("slof", "xmt")).toString()
            }

            // 11. YouTube
            if (host == "youtu.be" || host.endsWith("youtube.com")) {
                return retainQueryParameters(uri, setOf("v", "t", "list", "index")).toString()
            }

            // Common default cleaning
            uri = removeQueryParametersMatching(uri, Regex("""^([isu]tm_.*|ref|fbclid|gclid|ttclid|wickedid|yclid|gbraid|wbraid)$""", RegexOption.IGNORE_CASE))
            return uri.toString()
        } catch (e: Exception) {
            return urlStr
        }
    }

    private fun retainQueryParameters(uri: Uri, keysToKeep: Set<String>): Uri {
        val builder = uri.buildUpon().clearQuery()
        for (key in uri.queryParameterNames) {
            if (keysToKeep.contains(key)) {
                for (value in uri.getQueryParameters(key)) {
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        return builder.build()
    }

    private fun removeQueryParameter(uri: Uri, keyToRemove: String): Uri {
        val builder = uri.buildUpon().clearQuery()
        for (key in uri.queryParameterNames) {
            if (key != keyToRemove) {
                for (value in uri.getQueryParameters(key)) {
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        return builder.build()
    }

    private fun removeQueryParameters(uri: Uri, keysToRemove: Set<String>): Uri {
        val builder = uri.buildUpon().clearQuery()
        for (key in uri.queryParameterNames) {
            if (!keysToRemove.contains(key)) {
                for (value in uri.getQueryParameters(key)) {
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        return builder.build()
    }

    private fun removeQueryParametersMatching(uri: Uri, regex: Regex): Uri {
        val builder = uri.buildUpon().clearQuery()
        for (key in uri.queryParameterNames) {
            if (!regex.matches(key)) {
                for (value in uri.getQueryParameters(key)) {
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        return builder.build()
    }
}
