package com.viyo.app.network

import com.viyo.app.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ViyoApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: Map<String, String>): TokenResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: Map<String, String>): TokenResponse

    @GET("api/v1/auth/me")
    suspend fun getMe(@Header("Authorization") token: String): User

    // User Search
    @GET("api/v1/users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): List<UserSearchItem>

    // Contacts & Requests
    @GET("api/v1/contacts")
    suspend fun getContacts(@Header("Authorization") token: String): List<Contact>

    @POST("api/v1/contacts/request")
    suspend fun sendContactRequest(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

    @GET("api/v1/contacts/requests/pending")
    suspend fun getPendingRequests(@Header("Authorization") token: String): List<ContactRequestItem>

    @POST("api/v1/contacts/requests/{id}/accept")
    suspend fun acceptContactRequest(
        @Header("Authorization") token: String,
        @Path("id") requestId: String
    ): Map<String, Any>

    @POST("api/v1/contacts/requests/{id}/reject")
    suspend fun rejectContactRequest(
        @Header("Authorization") token: String,
        @Path("id") requestId: String
    ): Map<String, Any>

    // Messaging
    @GET("api/v1/messaging/conversations")
    suspend fun getConversations(@Header("Authorization") token: String): List<Conversation>

    @POST("api/v1/messaging/conversations")
    suspend fun createConversation(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    ): Conversation

    @GET("api/v1/messaging/conversations/{id}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String
    ): List<Message>

    @POST("api/v1/messaging/conversations/{id}/messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Body body: Map<String, String>
    ): Message

    // Calling
    @POST("api/v1/calls/initiate")
    suspend fun initiateCall(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): CallRecord

    @POST("api/v1/calls/{id}/action")
    suspend fun callAction(
        @Header("Authorization") token: String,
        @Path("id") callId: String,
        @Body body: Map<String, String>
    ): CallRecord

    @GET("api/v1/calls/history")
    suspend fun getCallHistory(@Header("Authorization") token: String): List<CallRecord>

    // Meetings
    @POST("api/v1/meetings")
    suspend fun createMeeting(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    ): Meeting

    @GET("api/v1/meetings/{code}")
    suspend fun joinMeeting(
        @Header("Authorization") token: String,
        @Path("code") meetingCode: String
    ): Meeting
}

object NetworkClient {
    // Default to production deployed Render backend
    const val DEFAULT_BASE_URL = "https://viyo-backend.onrender.com/"

    var baseUrl: String = DEFAULT_BASE_URL
        private set

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    var api: ViyoApi = createApi(baseUrl)
        private set

    fun initialize(customUrl: String? = null) {
        val targetUrl = if (!customUrl.isNullOrBlank()) {
            if (customUrl.endsWith("/")) customUrl else "$customUrl/"
        } else {
            DEFAULT_BASE_URL
        }
        baseUrl = targetUrl
        api = createApi(baseUrl)
    }

    private fun createApi(url: String): ViyoApi {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ViyoApi::class.java)
    }

    fun getWebSocketUrl(token: String): String {
        val baseWs = if (baseUrl.startsWith("https://")) {
            baseUrl.replace("https://", "wss://")
        } else {
            baseUrl.replace("http://", "ws://")
        }
        val cleanWs = if (baseWs.endsWith("/")) baseWs.dropLast(1) else baseWs
        return "$cleanWs/api/v1/ws?token=$token"
    }
}
