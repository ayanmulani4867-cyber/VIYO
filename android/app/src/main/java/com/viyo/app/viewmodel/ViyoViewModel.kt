package com.viyo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viyo.app.model.*
import com.viyo.app.network.NetworkClient
import com.viyo.app.webrtc.LiveKitManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViyoViewModel(application: Application) : AndroidViewModel(application) {
    val liveKitManager = LiveKitManager(application)

    private val _token = MutableStateFlow<String?>(null)
    val token = _token.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _activeCall = MutableStateFlow<CallRecord?>(null)
    val activeCall = _activeCall.asStateFlow()

    private val _activeMeeting = MutableStateFlow<Meeting?>(null)
    val activeMeeting = _activeMeeting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun login(usernameOrEmail: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = NetworkClient.api.login(
                    mapOf("username_or_email" to usernameOrEmail, "password" to pass)
                )
                _token.value = res.accessToken
                val user = NetworkClient.api.getMe("Bearer ${res.accessToken}")
                _currentUser.value = user
                loadInitialData()
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Login failed"
            }
        }
    }

    fun register(email: String, username: String, pass: String, name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = NetworkClient.api.register(
                    mapOf(
                        "email" to email,
                        "username" to username,
                        "password" to pass,
                        "display_name" to name
                    )
                )
                _token.value = res.accessToken
                val user = NetworkClient.api.getMe("Bearer ${res.accessToken}")
                _currentUser.value = user
                loadInitialData()
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Registration failed"
            }
        }
    }

    fun loadInitialData() {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                val c = NetworkClient.api.getContacts("Bearer $t")
                _contacts.value = c
                val convs = NetworkClient.api.getConversations("Bearer $t")
                _conversations.value = convs
            } catch (e: Exception) {
                // Ignore initial load error
            }
        }
    }

    fun startCall(receiverId: String, callType: String = "video") {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                val call = NetworkClient.api.initiateCall(
                    "Bearer $t",
                    mapOf("receiver_id" to receiverId, "call_type" to callType)
                )
                _activeCall.value = call
                if (call.livekitToken != null && call.livekitUrl != null) {
                    liveKitManager.connect(
                        url = call.livekitUrl,
                        token = call.livekitToken,
                        video = callType == "video"
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to start call: ${e.message}"
            }
        }
    }

    fun endCall() {
        val t = _token.value ?: return
        val call = _activeCall.value ?: return
        viewModelScope.launch {
            try {
                NetworkClient.api.callAction(
                    "Bearer $t",
                    call.id,
                    mapOf("action" to "end")
                )
            } catch (e: Exception) {
                // Ignore
            } finally {
                liveKitManager.disconnect()
                _activeCall.value = null
            }
        }
    }

    fun createMeeting(title: String, onSuccess: () -> Unit) {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                val mtg = NetworkClient.api.createMeeting(
                    "Bearer $t",
                    mapOf("title" to title, "waiting_room_enabled" to false)
                )
                _activeMeeting.value = mtg
                if (mtg.livekitToken != null && mtg.livekitUrl != null) {
                    liveKitManager.connect(url = mtg.livekitUrl, token = mtg.livekitToken, video = true)
                }
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create meeting: ${e.message}"
            }
        }
    }

    fun joinMeeting(code: String, onSuccess: () -> Unit) {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                val mtg = NetworkClient.api.joinMeeting("Bearer $t", code)
                _activeMeeting.value = mtg
                if (mtg.livekitToken != null && mtg.livekitUrl != null) {
                    liveKitManager.connect(url = mtg.livekitUrl, token = mtg.livekitToken, video = true)
                }
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to join meeting: ${e.message}"
            }
        }
    }

    fun leaveMeeting() {
        liveKitManager.disconnect()
        _activeMeeting.value = null
    }

    fun logout() {
        liveKitManager.disconnect()
        _token.value = null
        _currentUser.value = null
        _activeCall.value = null
        _activeMeeting.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

