package com.spectre.app.core.network

import com.spectre.app.core.network.model.*
import retrofit2.Response
import retrofit2.http.*

interface IdentityApi {
    @POST("connect/token")
    @FormUrlEncoded
    suspend fun getToken(
        @Field("grant_type")          grantType: String = "password",
        @Field("username")            username: String,
        @Field("password")            password: String,
        @Field("scope")               scope: String = "api offline_access",
        @Field("client_id")           clientId: String = "mobile",
        @Field("device_type")         deviceType: Int = 0,
        @Field("device_name")         deviceName: String = "Spectre for Bitwarden",
        @Field("device_identifier")   deviceIdentifier: String,
        @Field("two_factor_token")    twoFactorToken: String? = null,
        @Field("two_factor_provider") twoFactorProvider: Int? = null,
        @Field("two_factor_remember") twoFactorRemember: Boolean? = null,
    ): Response<TokenResponse>

    @POST("connect/token")
    @FormUrlEncoded
    suspend fun refreshToken(
        @Field("grant_type")    grantType: String = "refresh_token",
        @Field("client_id")     clientId: String = "mobile",
        @Field("refresh_token") refreshToken: String,
    ): Response<TokenResponse>

    @POST("accounts/prelogin")
    suspend fun preLogin(@Body request: PreLoginRequest): Response<PreLoginResponse>
}

interface VaultApi {

    // ── Account ──────────────────────────────────────────────────────────────
    @GET("accounts/revision-date")
    suspend fun getRevisionDate(): Response<Long>

    @GET("sync")
    suspend fun sync(): Response<SyncResponse>

    // ── Ciphers ───────────────────────────────────────────────────────────────
    @GET("ciphers")
    suspend fun getCiphers(): Response<List<CipherResponse>>

    @GET("ciphers/{id}")
    suspend fun getCipher(@Path("id") id: String): Response<CipherResponse>

    @POST("ciphers")
    suspend fun createCipher(@Body request: CipherRequest): Response<CipherResponse>

    @PUT("ciphers/{id}")
    suspend fun updateCipher(
        @Path("id") id: String,
        @Body request: CipherRequest,
    ): Response<CipherResponse>

    @DELETE("ciphers/{id}")
    suspend fun deleteCipher(@Path("id") id: String): Response<Unit>

    @PUT("ciphers/{id}/delete")
    suspend fun softDeleteCipher(@Path("id") id: String): Response<Unit>

    @PUT("ciphers/{id}/restore")
    suspend fun restoreCipher(@Path("id") id: String): Response<CipherResponse>

    @PUT("ciphers/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: String): Response<Unit>

    @POST("ciphers/import")
    suspend fun importCiphers(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Unit>

    // ── Attachments ───────────────────────────────────────────────────────────
    @Multipart
    @POST("ciphers/{id}/attachment/v2")
    suspend fun createAttachment(
        @Path("id") cipherId: String,
        @Part("fileName") fileName: okhttp3.RequestBody,
        @Part("fileSize") fileSize: okhttp3.RequestBody,
        @Part("key") key: okhttp3.RequestBody,
        @Part file: okhttp3.MultipartBody.Part,
    ): Response<AttachmentResponse>

    @DELETE("ciphers/{cipherId}/attachment/{attachmentId}")
    suspend fun deleteAttachment(
        @Path("cipherId") cipherId: String,
        @Path("attachmentId") attachmentId: String,
    ): Response<Unit>

    // ── Folders ───────────────────────────────────────────────────────────────
    @GET("folders")
    suspend fun getFolders(): Response<List<FolderResponse>>

    @POST("folders")
    suspend fun createFolder(@Body request: FolderRequest): Response<FolderResponse>

    @PUT("folders/{id}")
    suspend fun updateFolder(
        @Path("id") id: String,
        @Body request: FolderRequest,
    ): Response<FolderResponse>

    @DELETE("folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String): Response<Unit>

    // ── Sends ─────────────────────────────────────────────────────────────────
    @GET("sends")
    suspend fun getSends(): Response<List<SendResponse>>

    @POST("sends")
    suspend fun createSend(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<SendResponse>

    @PUT("sends/{id}")
    suspend fun updateSend(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): Response<SendResponse>

    @DELETE("sends/{id}")
    suspend fun deleteSend(@Path("id") id: String): Response<Unit>
}
