package com.viyo.app.network

import com.viyo.app.model.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface SupabaseApi {
    // --- SUPABASE AUTH ---
    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: Map<String, @JvmSuppressWildcards Any>): SupabaseAuthResponse

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body body: Map<String, String>): SupabaseAuthResponse

    @GET("auth/v1/user")
    suspend fun getUser(): SupabaseAuthUser

    // --- PROFILES & SEARCH ---
    @GET("rest/v1/user_profiles")
    suspend fun getProfile(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): List<UserProfile>

    @GET("rest/v1/user_profiles")
    suspend fun searchProfiles(
        @Query("or") orFilter: String,
        @Query("id") notMeFilter: String? = null,
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 20
    ): List<UserSearchItem>

    @PATCH("rest/v1/user_profiles")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<UserProfile>

    // --- CONTACTS ---
    @GET("rest/v1/contacts")
    suspend fun getContacts(
        @Query("user_id") userFilter: String,
        @Query("select") select: String = "*"
    ): List<ContactRow>

    @POST("rest/v1/contacts")
    suspend fun addContact(
        @Body body: List<Map<String, String>>
    )

    @DELETE("rest/v1/contacts")
    suspend fun removeContact(
        @Query("user_id") userFilter: String,
        @Query("contact_user_id") contactUserFilter: String
    )

    // --- CONTACT REQUESTS ---
    @GET("rest/v1/contact_requests")
    suspend fun getPendingRequests(
        @Query("receiver_id") receiverFilter: String,
        @Query("status") statusFilter: String = "eq.pending",
        @Query("select") select: String = "*"
    ): List<ContactRequestItem>

    @POST("rest/v1/contact_requests")
    suspend fun sendContactRequest(
        @Body body: Map<String, String>
    )

    @PATCH("rest/v1/contact_requests")
    suspend fun updateContactRequest(
        @Query("id") idFilter: String,
        @Body body: Map<String, String>
    )

    // --- CONVERSATIONS ---
    @GET("rest/v1/conversations")
    suspend fun getConversations(
        @Query("select") select: String = "*",
        @Query("order") order: String = "updated_at.desc"
    ): List<Conversation>

    @POST("rest/v1/conversations")
    suspend fun createConversation(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<Conversation>

    @POST("rest/v1/conversation_members")
    suspend fun addConversationMembers(
        @Body body: List<Map<String, String>>
    )

    @GET("rest/v1/conversation_members")
    suspend fun getConversationMembers(
        @Query("user_id") userFilter: String,
        @Query("select") select: String = "*"
    ): List<ConversationMemberRow>

    // --- MESSAGING ---
    @GET("rest/v1/messages")
    suspend fun getMessages(
        @Query("conversation_id") convFilter: String,
        @Query("order") order: String = "created_at.asc",
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 50
    ): List<Message>

    @POST("rest/v1/messages")
    suspend fun sendMessage(
        @Body body: Map<String, String>
    ): List<Message>

    // --- CALLS ---
    @POST("rest/v1/calls")
    suspend fun createCallRecord(
        @Body body: Map<String, String>
    ): List<CallRecord>

    @PATCH("rest/v1/calls")
    suspend fun updateCallRecord(
        @Query("id") idFilter: String,
        @Body body: Map<String, String>
    ): List<CallRecord>

    // --- MEETINGS ---
    @POST("rest/v1/meetings")
    suspend fun createMeeting(
        @Body body: Map<String, String>
    ): List<Meeting>

    @GET("rest/v1/meetings")
    suspend fun getMeetingByCode(
        @Query("meeting_code") codeFilter: String,
        @Query("select") select: String = "*"
    ): List<Meeting>

    // --- EDGE FUNCTIONS: LIVEKIT TOKEN ---
    @POST("functions/v1/generate-livekit-token")
    suspend fun generateLiveKitToken(
        @Body body: Map<String, String>
    ): LiveKitTokenResponse
}

object NetworkClient {
    const val SUPABASE_URL = "https://bmzxwyzitfhczjbkjysp.supabase.co/"
    const val SUPABASE_KEY = "sb_publishable_JjKmLKGcwt_ooCrbJCYdTQ_cQbl-lQz"
    const val LIVEKIT_URL = "wss://viyo-vz4rl9o4.livekit.cloud"

    var currentAuthToken: String? = null

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("apikey", SUPABASE_KEY)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation")

        val token = currentAuthToken
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        } else {
            requestBuilder.header("Authorization", "Bearer $SUPABASE_KEY")
        }

        chain.proceed(requestBuilder.build())
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    val api: SupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(SUPABASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }

    fun getRealtimeWebSocketUrl(): String {
        return "wss://bmzxwyzitfhczjbkjysp.supabase.co/realtime/v1/websocket?apikey=$SUPABASE_KEY&vsn=1.0.0"
    }
}
