package com.viyo.app.network

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "viyo_auth_prefs"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_BASE_URL = "custom_base_url"
    }

    fun saveToken(token: String, userId: String, username: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .apply()
    }

    fun getCustomBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun setCustomBaseUrl(url: String?) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }
}
