package com.viyo.app

import com.viyo.app.model.*
import com.viyo.app.network.NetworkClient
import com.viyo.app.network.SupabaseApi
import org.junit.Assert.*
import org.junit.Test

class SupabaseAuthModelTest {

    @Test
    fun testRetrofitConverterCreation() {
        // This confirms Retrofit can create the SupabaseApi service and converters without crashing
        val api = NetworkClient.api
        assertNotNull(api)
    }

    @Test
    fun testSupabaseSignupResponseParsing() {
        val json = """
        {
          "access_token": "mock_jwt_token_12345",
          "token_type": "bearer",
          "expires_in": 3600,
          "refresh_token": "mock_refresh_token",
          "user": {
            "id": "34d04891-7613-466b-a73e-2ebf6c421da7",
            "aud": "authenticated",
            "role": "authenticated",
            "email": "test@viyo.io",
            "email_confirmed_at": "2026-10-31T15:54:20Z",
            "user_metadata": {
              "display_name": "Converter Check",
              "email": "test@viyo.io",
              "email_verified": true,
              "phone_verified": false,
              "sub": "34d04891-7613-466b-a73e-2ebf6c421da7",
              "username": "convertercheck"
            }
          }
        }
        """.trimIndent()

        val adapter = NetworkClient.moshi.adapter(SupabaseAuthResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("mock_jwt_token_12345", response?.accessToken)
        assertEquals("bearer", response?.tokenType)

        val user = response?.getEffectiveUser()
        assertNotNull(user)
        assertEquals("34d04891-7613-466b-a73e-2ebf6c421da7", user?.id)
        assertEquals("test@viyo.io", user?.email)
        assertEquals("convertercheck", user?.userMetadata?.username)
        assertEquals("Converter Check", user?.userMetadata?.displayName)
        assertEquals("convertercheck", response?.getEffectiveUsername("fallback@viyo.io"))
    }

    @Test
    fun testSupabaseUnconfirmedEmailSignupResponseParsing() {
        val json = """
        {
          "id": "34d04891-7613-466b-a73e-2ebf6c421da7",
          "aud": "authenticated",
          "role": "authenticated",
          "email": "unconfirmed@viyo.io",
          "user_metadata": {
            "display_name": "Unconfirmed User",
            "username": "unconfirmed"
          }
        }
        """.trimIndent()

        val adapter = NetworkClient.moshi.adapter(SupabaseAuthResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertNull(response?.accessToken)

        val user = response?.getEffectiveUser()
        assertNotNull(user)
        assertEquals("34d04891-7613-466b-a73e-2ebf6c421da7", user?.id)
        assertEquals("unconfirmed@viyo.io", user?.email)
        assertEquals("unconfirmed", response?.getEffectiveUsername("fallback@viyo.io"))
    }

    @Test
    fun testUserProfileParsing() {
        val json = """
        {
          "id": "34d04891-7613-466b-a73e-2ebf6c421da7",
          "username": "testuser",
          "display_name": "Test User",
          "avatar_url": null,
          "bio": "Hello VIYO!",
          "status_message": "Available",
          "is_online": true,
          "last_seen": "2026-10-31T15:54:20Z"
        }
        """.trimIndent()

        val adapter = NetworkClient.moshi.adapter(UserProfile::class.java)
        val profile = adapter.fromJson(json)

        assertNotNull(profile)
        assertEquals("testuser", profile?.username)
        assertEquals("Test User", profile?.displayName)
        assertTrue(profile?.isOnline == true)
    }

    @Test
    fun testLiveKitTokenResponseParsing() {
        val json = """
        {
          "token": "livekit_jwt_sample_token",
          "livekit_url": "wss://viyo-vz4rl9o4.livekit.cloud",
          "room_name": "room_12345"
        }
        """.trimIndent()

        val adapter = NetworkClient.moshi.adapter(LiveKitTokenResponse::class.java)
        val res = adapter.fromJson(json)

        assertNotNull(res)
        assertEquals("livekit_jwt_sample_token", res?.token)
        assertEquals("wss://viyo-vz4rl9o4.livekit.cloud", res?.livekitUrl)
        assertEquals("room_12345", res?.roomName)
    }
}

