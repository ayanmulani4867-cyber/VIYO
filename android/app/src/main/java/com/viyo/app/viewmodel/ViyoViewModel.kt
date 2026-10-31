package com.viyo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viyo.app.model.*
import com.viyo.app.network.*
import com.viyo.app.webrtc.LiveKitManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViyoViewModel(application: Application) : AndroidViewModel(application), ViyoWebSocketListener {
    val liveKitManager = LiveKitManager(application)
    private val tokenManager = TokenManager(application)
    private val webSocketClient = ViyoWebSocketClient(this)

    private val _token = MutableStateFlow<String?>(null)
    val token = _token.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<ContactRequestItem>>(emptyList())
    val pendingRequests = _pendingRequests.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserSearchItem>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    val activeConversation = _activeConversation.asStateFlow()

    private val _activeChatContact = MutableStateFlow<Contact?>(null)
    val activeChatContact = _activeChatContact.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping = _isPeerTyping.asStateFlow()

    private val _activeCall = MutableStateFlow<CallRecord?>(null)
    val activeCall = _activeCall.asStateFlow()

    private val _incomingCall = MutableStateFlow<IncomingCallData?>(null)
    val incomingCall = _incomingCall.asStateFlow()

    private val _activeMeeting = MutableStateFlow<Meeting?>(null)
    val activeMeeting = _activeMeeting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        // Initialize custom server url if saved
        val customUrl = tokenManager.getCustomBaseUrl()
        NetworkClient.initialize(customUrl)

        // Restore persisted session
        val savedToken = tokenManager.getToken()
        if (!savedToken.isNullOrBlank()) {
            _token.value = savedToken
            webSocketClient.connect(savedToken)
            loadCurrentUser(savedToken)
        }
    }

    private fun loadCurrentUser(t: String) {
        viewModelScope.launch {
            try {
                val user = NetworkClient.api.getMe("Bearer $t")
                _currentUser.value = user
                loadInitialData()
            } catch (e: Exception) {
                // If token expired or unauthorized, logout
                if (e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true) {
                    logout()
                }
            }
        }
    }

    fun login(usernameOrEmail: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = NetworkClient.api.login(
                    mapOf("username_or_email" to usernameOrEmail, "password" to pass)
                )
                _token.value = res.accessToken
                tokenManager.saveToken(res.accessToken, res.userId, res.username)
                webSocketClient.connect(res.accessToken)

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
                tokenManager.saveToken(res.accessToken, res.userId, res.username)
                webSocketClient.connect(res.accessToken)

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
                loadContacts()
                loadPendingRequests()
                val convs = NetworkClient.api.getConversations("Bearer $t")
                _conversations.value = convs
            } catch (e: Exception) {
                // Ignore initial load error
            }
        }
    }

    fun loadContacts() {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                val c = NetworkClient.api.getContacts("Bearer $t")
                _contacts.value = c
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun loadPendingRequests() {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                val reqs = NetworkClient.api.getPendingRequests("Bearer $t")
                _pendingRequests.value = reqs
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun searchUsers(query: String) {
        val t = _token.value ?: return
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = NetworkClient.api.searchUsers("Bearer $t", query.trim())
                _searchResults.value = results
            } catch (e: Exception) {
                _errorMessage.value = "Search error: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun sendContactRequest(targetUsername: String, onDone: () -> Unit) {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                NetworkClient.api.sendContactRequest(
                    "Bearer $t",
                    mapOf("receiver_username" to targetUsername)
                )
                onDone()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send request: ${e.message}"
            }
        }
    }

    fun acceptContactRequest(requestId: String) {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                NetworkClient.api.acceptContactRequest("Bearer $t", requestId)
                loadPendingRequests()
                loadContacts()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to accept request: ${e.message}"
            }
        }
    }

    fun rejectContactRequest(requestId: String) {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                NetworkClient.api.rejectContactRequest("Bearer $t", requestId)
                loadPendingRequests()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to reject request: ${e.message}"
            }
        }
    }

    // Messaging
    fun openChat(contact: Contact, onOpenChatScreen: () -> Unit) {
        val t = _token.value ?: return
        _activeChatContact.value = contact
        viewModelScope.launch {
            try {
                // Find or create direct conversation
                val conv = NetworkClient.api.createConversation(
                    "Bearer $t",
                    mapOf(
                        "type" to "direct",
                        "participant_user_ids" to listOf(contact.contactUserId)
                    )
                )
                _activeConversation.value = conv
                loadMessages(conv.id)
                onOpenChatScreen()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to open conversation: ${e.message}"
            }
        }
    }

    fun loadMessages(conversationId: String) {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                val msgs = NetworkClient.api.getMessages("Bearer $t", conversationId)
                _messages.value = msgs
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load messages: ${e.message}"
            }
        }
    }

    fun sendMessage(content: String) {
        val t = _token.value ?: return
        val conv = _activeConversation.value ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            try {
                val sent = NetworkClient.api.sendMessage(
                    "Bearer $t",
                    conv.id,
                    mapOf("content" to content, "message_type" to "text")
                )
                // Add to messages if not already delivered via WS
                if (_messages.value.none { it.id == sent.id }) {
                    _messages.value = _messages.value + sent
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send message: ${e.message}"
            }
        }
    }

    fun sendTyping(isTyping: Boolean) {
        val conv = _activeConversation.value ?: return
        val peerId = _activeChatContact.value?.contactUserId ?: return
        webSocketClient.sendTyping(conv.id, listOf(peerId), isTyping)
    }

    // Calling
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

    fun acceptIncomingCall(incoming: IncomingCallData, onConnected: () -> Unit) {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                val updatedCall = NetworkClient.api.callAction(
                    "Bearer $t",
                    incoming.callId,
                    mapOf("action" to "accept")
                )
                _incomingCall.value = null
                _activeCall.value = updatedCall
                if (updatedCall.livekitToken != null && updatedCall.livekitUrl != null) {
                    liveKitManager.connect(
                        url = updatedCall.livekitUrl,
                        token = updatedCall.livekitToken,
                        video = incoming.callType == "video"
                    )
                }
                onConnected()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to accept call: ${e.message}"
                _incomingCall.value = null
            }
        }
    }

    fun rejectIncomingCall(incoming: IncomingCallData) {
        val t = _token.value ?: return
        viewModelScope.launch {
            try {
                NetworkClient.api.callAction(
                    "Bearer $t",
                    incoming.callId,
                    mapOf("action" to "reject")
                )
            } catch (e: Exception) {
                // Ignore
            } finally {
                _incomingCall.value = null
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

    // Meetings
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
        webSocketClient.disconnect()
        tokenManager.clear()
        _token.value = null
        _currentUser.value = null
        _contacts.value = emptyList()
        _pendingRequests.value = emptyList()
        _searchResults.value = emptyList()
        _messages.value = emptyList()
        _activeConversation.value = null
        _activeChatContact.value = null
        _activeCall.value = null
        _incomingCall.value = null
        _activeMeeting.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // --- ViyoWebSocketListener Callbacks ---
    override fun onPresenceUpdate(userId: String, isOnline: Boolean, lastSeen: String?) {
        // Update presence in contacts list
        val currentContacts = _contacts.value
        _contacts.value = currentContacts.map { c ->
            if (c.contactUserId == userId) {
                c.copy(isOnline = isOnline, lastSeen = lastSeen ?: c.lastSeen)
            } else {
                c
            }
        }

        // Update presence in search results
        val currentSearch = _searchResults.value
        _searchResults.value = currentSearch.map { u ->
            if (u.id == userId) {
                u.copy(isOnline = isOnline, lastSeen = lastSeen ?: u.lastSeen)
            } else {
                u
            }
        }
    }

    override fun onNewMessage(message: Message) {
        val conv = _activeConversation.value
        if (conv != null && conv.id == message.conversationId) {
            if (_messages.value.none { it.id == message.id }) {
                _messages.value = _messages.value + message
            }
        }
        // Refresh conversations to update preview
        val t = _token.value
        if (t != null) {
            viewModelScope.launch {
                try {
                    val updatedConvs = NetworkClient.api.getConversations("Bearer $t")
                    _conversations.value = updatedConvs
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    override fun onContactRequestReceived(requestId: String, senderId: String, senderUsername: String) {
        loadPendingRequests()
    }

    override fun onContactRequestAccepted(userId: String, username: String) {
        loadContacts()
    }

    override fun onIncomingCall(call: IncomingCallData) {
        _incomingCall.value = call
    }

    override fun onCallAccepted(callId: String) {
        _activeCall.value?.let { current ->
            if (current.id == callId) {
                _activeCall.value = current.copy(status = "accepted")
            }
        }
    }

    override fun onCallRejected(callId: String) {
        if (_activeCall.value?.id == callId) {
            liveKitManager.disconnect()
            _activeCall.value = null
            _errorMessage.value = "Call was declined."
        }
    }

    override fun onCallEnded(callId: String) {
        if (_activeCall.value?.id == callId || _incomingCall.value?.callId == callId) {
            liveKitManager.disconnect()
            _activeCall.value = null
            _incomingCall.value = null
        }
    }

    override fun onUserTyping(conversationId: String, userId: String, isTyping: Boolean) {
        if (_activeConversation.value?.id == conversationId) {
            _isPeerTyping.value = isTyping
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketClient.disconnect()
        liveKitManager.disconnect()
    }
}
