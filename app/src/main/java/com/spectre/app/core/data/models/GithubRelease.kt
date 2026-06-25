package com.spectre.app.core.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name")      val tagName: String,       // "v1.8.4"
    @SerialName("name")          val name: String,          // "Spectre v1.8.4"
    @SerialName("body")          val body: String,          // markdown changelog
    @SerialName("published_at")  val publishedAt: String,
    @SerialName("html_url")      val htmlUrl: String,       // release page URL
    @SerialName("assets")        val assets: List<GithubAsset>
)

@Serializable
data class GithubAsset(
    @SerialName("name")                  val name: String,
    @SerialName("size")                  val size: Long,
    @SerialName("browser_download_url")  val downloadUrl: String,
    @SerialName("content_type")          val contentType: String
)
