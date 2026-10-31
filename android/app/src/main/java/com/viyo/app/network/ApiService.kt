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

    @GET("api/v1/contacts")
    suspend fun getContacts(@Header("Authorization") token: String): List<Contact>

    @POST("api/v1/contacts/request")
    suspend fun sendContactRequest(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

    @GET("api/v1/messaging/conversations")
    suspend fun getConversations(@Header("Authorization") token: String): List<Conversation>

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
    // 10.0.2.2 routes to host machine from Android Emulator; 127.0.0.1 for local testing
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val api: ViyoApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(ViyoApi::class.java)
}

