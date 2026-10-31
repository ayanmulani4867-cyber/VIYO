package com.viyo.app.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseAuthUser(
    val id: String,
    val email: String? = null,
    @Json(name = "user_metadata") val userMetadata: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    val user: SupabaseAuthUser? = null
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String = "bearer",
    @Json(name = "user_id") val userId: String,
    val username: String
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String? = null,
    val username: String? = null,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @Json(name = "status_message") val statusMessage: String? = null,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "last_seen") val lastSeen: String? = null
)

@JsonClass(generateAdapter = true)
data class User(
    val id: String,
    val email: String,
    val username: String,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "is_admin") val isAdmin: Boolean = false,
    val profile: UserProfile? = null
)

@JsonClass(generateAdapter = true)
data class UserSearchItem(
    val id: String,
    val username: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "last_seen") val lastSeen: String? = null
)

@JsonClass(generateAdapter = true)
data class Contact(
    val id: String,
    @Json(name = "contact_user_id") val contactUserId: String,
    val username: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "last_seen") val lastSeen: String? = null
)

@JsonClass(generateAdapter = true)
data class ContactRow(
    val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "contact_user_id") val contactUserId: String,
    val nickname: String? = null,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class ContactRequestItem(
    val id: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "receiver_id") val receiverId: String,
    val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "sender_username") val senderUsername: String? = null,
    @Json(name = "sender_display_name") val senderDisplayName: String? = null,
    @Json(name = "sender_avatar_url") val senderAvatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class Message(
    val id: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "sender_id") val senderId: String? = null,
    @Json(name = "sender_username") val senderUsername: String? = null,
    @Json(name = "sender_display_name") val senderDisplayName: String? = null,
    val content: String? = null,
    @Json(name = "message_type") val messageType: String = "text",
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class Conversation(
    val id: String,
    val type: String,
    val title: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "last_message") val lastMessage: Message? = null
)

@JsonClass(generateAdapter = true)
data class ConversationMemberRow(
    val id: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "user_id") val userId: String,
    val role: String = "member"
)

@JsonClass(generateAdapter = true)
data class CallRecord(
    val id: String,
    @Json(name = "caller_id") val callerId: String? = null,
    @Json(name = "receiver_id") val receiverId: String? = null,
    @Json(name = "caller_username") val callerUsername: String? = null,
    @Json(name = "caller_display_name") val callerDisplayName: String? = null,
    @Json(name = "receiver_username") val receiverUsername: String? = null,
    @Json(name = "receiver_display_name") val receiverDisplayName: String? = null,
    @Json(name = "room_name") val roomName: String,
    @Json(name = "call_type") val callType: String,
    val status: String,
    @Json(name = "duration_seconds") val durationSeconds: Int = 0,
    @Json(name = "livekit_token") val livekitToken: String? = null,
    @Json(name = "livekit_url") val livekitUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class IncomingCallData(
    val callId: String,
    val roomName: String,
    val callType: String,
    val callerId: String,
    val callerUsername: String,
    val callerDisplayName: String,
    val callerAvatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class Meeting(
    val id: String,
    @Json(name = "meeting_code") val meetingCode: String,
    val title: String,
    @Json(name = "room_name") val roomName: String,
    @Json(name = "host_id") val hostId: String,
    @Json(name = "livekit_token") val livekitToken: String? = null,
    @Json(name = "livekit_url") val livekitUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class LiveKitTokenResponse(
    val token: String? = null,
    @Json(name = "livekit_url") val livekitUrl: String? = null,
    @Json(name = "room_name") val roomName: String? = null,
    val error: String? = null
)
