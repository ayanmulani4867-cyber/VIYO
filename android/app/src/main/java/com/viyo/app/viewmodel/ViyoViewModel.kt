package com.viyo.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viyo.app.model.*
import com.viyo.app.network.*
import com.viyo.app.webrtc.LiveKitManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ViyoViewModel(application: Application) : AndroidViewModel(application), ViyoRealtimeListener {
    val liveKitManager = LiveKitManager(application)
    private val tokenManager = TokenManager(application)
    private val realtimeClient = ViyoRealtimeClient(this)

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
        // Restore session from TokenManager
        val savedToken = tokenManager.getToken()
        val savedUserId = tokenManager.getUserId()
        val savedUsername = tokenManager.getUsername()

        if (!savedToken.isNullOrBlank() && !savedUserId.isNullOrBlank()) {
            _token.value = savedToken
            NetworkClient.currentAuthToken = savedToken
            realtimeClient.connect(savedUserId)
            loadUserProfile(savedUserId, savedUsername ?: "")
            loadInitialData()
        }
    }

    private fun loadUserProfile(userId: String, fallbackUsername: String) {
        viewModelScope.launch {
            try {
                val profiles = NetworkClient.api.getProfile("eq.$userId")
                val p = profiles.firstOrNull()
                _currentUser.value = User(
                    id = userId,
                    email = "",
                    username = p?.username ?: fallbackUsername,
                    isActive = true,
                    isAdmin = false,
                    profile = p ?: UserProfile(displayName = fallbackUsername)
                )
            } catch (e: Exception) {
                Log.w("ViyoVM", "Error loading profile: ${e.message}")
            }
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = NetworkClient.api.signIn(
                    mapOf("email" to email.trim(), "password" to pass)
                )
                val accessToken = res.accessToken
                val user = res.user
                if (accessToken != null && user != null) {
                    _token.value = accessToken
                    NetworkClient.currentAuthToken = accessToken
                    val username = user.userMetadata?.get("username") ?: email.substringBefore("@")
                    tokenManager.saveToken(accessToken, user.id, username)
                    realtimeClient.connect(user.id)
                    loadUserProfile(user.id, username)
                    loadInitialData()
                    onSuccess()
                } else {
                    _errorMessage.value = "Login failed: No access token returned"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Login failed"
            }
        }
    }

    fun register(email: String, username: String, pass: String, name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = NetworkClient.api.signUp(
                    mapOf(
                        "email" to email.trim(),
                        "password" to pass,
                        "data" to mapOf(
                            "username" to username.trim().lowercase(),
                            "display_name" to name.trim()
                        )
                    )
                )
                val accessToken = res.accessToken
                val user = res.user
                if (accessToken != null && user != null) {
                    _token.value = accessToken
                    NetworkClient.currentAuthToken = accessToken
                    tokenManager.saveToken(accessToken, user.id, username)
                    realtimeClient.connect(user.id)
                    loadUserProfile(user.id, username)
                    loadInitialData()
                    onSuccess()
                } else {
                    // Try auto sign-in if signup succeeded without auto-confirm
                    login(email, pass, onSuccess)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Registration failed"
            }
        }
    }

    fun loadInitialData() {
        loadContacts()
        loadPendingRequests()
        loadConversations()
    }

    fun loadContacts() {
        val myId = tokenManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                val rows = NetworkClient.api.getContacts("eq.$myId")
                if (rows.isEmpty()) {
                    _contacts.value = emptyList()
                    return@launch
                }

                val contactUserIds = rows.map { it.contactUserId }
                // Fetch profiles for all contacts
                val orFilter = "id.in.(${contactUserIds.joinToString(",")})"
                val profiles = NetworkClient.api.searchProfiles(orFilter)
                val profileMap = profiles.associateBy { it.id }

                val contactList = rows.mapNotNull { row ->
                    val p = profileMap[row.contactUserId]
                    if (p != null) {
                        Contact(
                            id = row.id,
                            contactUserId = row.contactUserId,
                            username = p.username,
                            displayName = p.displayName,
                            avatarUrl = p.avatarUrl,
                            isOnline = p.isOnline,
                            lastSeen = p.lastSeen
                        )
                    } else null
                }
                _contacts.value = contactList
            } catch (e: Exception) {
                Log.w("ViyoVM", "Error loading contacts: ${e.message}")
            }
        }
    }

    fun loadPendingRequests() {
        val myId = tokenManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                val reqs = NetworkClient.api.getPendingRequests("eq.$myId")
                if (reqs.isEmpty()) {
                    _pendingRequests.value = emptyList()
                    return@launch
                }

                val senderIds = reqs.map { it.senderId }
                val orFilter = "id.in.(${senderIds.joinToString(",")})"
                val senderProfiles = NetworkClient.api.searchProfiles(orFilter)
                val profileMap = senderProfiles.associateBy { it.id }

                val enriched = reqs.map { req ->
                    val p = profileMap[req.senderId]
                    req.copy(
                        senderUsername = p?.username,
                        senderDisplayName = p?.displayName,
                        senderAvatarUrl = p?.avatarUrl
                    )
                }
                _pendingRequests.value = enriched
            } catch (e: Exception) {
                Log.w("ViyoVM", "Error loading pending requests: ${e.message}")
            }
        }
    }

    fun searchUsers(query: String) {
        val myId = tokenManager.getUserId() ?: return
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            try {
                val q = query.trim()
                val orFilter = "(username.ilike.*$q*,display_name.ilike.*$q*)"
                val results = NetworkClient.api.searchProfiles(
                    orFilter = orFilter,
                    notMeFilter = "neq.$myId"
                )
                _searchResults.value = results
            } catch (e: Exception) {
                _errorMessage.value = "Search error: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun sendContactRequest(targetUsername: String, onDone: () -> Unit) {
        val myId = tokenManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                // Find target user by username
                val target = NetworkClient.api.searchProfiles(orFilter = "username.eq.${targetUsername.trim().lowercase()}")
                val receiver = target.firstOrNull()
                if (receiver == null) {
                    _errorMessage.value = "User not found"
                    return@launch
                }

                NetworkClient.api.sendContactRequest(
                    mapOf(
                        "sender_id" to myId,
                        "receiver_id" to receiver.id,
                        "status" to "pending"
                    )
                )
                onDone()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send request: ${e.message}"
            }
        }
    }

    fun acceptContactRequest(requestId: String) {
        val myId = tokenManager.getUserId() ?: return
        val req = _pendingRequests.value.firstOrNull { it.id == requestId } ?: return
        viewModelScope.launch {
            try {
                NetworkClient.api.updateContactRequest("eq.$requestId", mapOf("status" to "accepted"))
                // Add mutual contacts
                NetworkClient.api.addContact(
                    listOf(
                        mapOf("user_id" to myId, "contact_user_id" to req.senderId),
                        mapOf("user_id" to req.senderId, "contact_user_id" to myId)
                    )
                )
                loadPendingRequests()
                loadContacts()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to accept request: ${e.message}"
            }
        }
    }

    fun rejectContactRequest(requestId: String) {
        viewModelScope.launch {
            try {
                NetworkClient.api.updateContactRequest("eq.$requestId", mapOf("status" to "rejected"))
                loadPendingRequests()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to reject request: ${e.message}"
            }
        }
    }

    private fun loadConversations() {
        val myId = tokenManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                val memberRows = NetworkClient.api.getConversationMembers("eq.$myId")
                if (memberRows.isEmpty()) {
                    _conversations.value = emptyList()
                    return@launch
                }
                val convIds = memberRows.map { it.conversationId }
                val convs = NetworkClient.api.getConversations()
                _conversations.value = convs.filter { it.id in convIds }
            } catch (e: Exception) {
                Log.w("ViyoVM", "Error loading conversations: ${e.message}")
            }
        }
    }

    fun openChat(contact: Contact, onOpenChatScreen: () -> Unit) {
        val myId = tokenManager.getUserId() ?: return
        _activeChatContact.value = contact
        viewModelScope.launch {
            try {
                // Find direct conversation with this contact
                val myMemberRows = NetworkClient.api.getConversationMembers("eq.$myId")
                val peerMemberRows = NetworkClient.api.getConversationMembers("eq.${contact.contactUserId}")

                val commonConvIds = myMemberRows.map { it.conversationId }.intersect(peerMemberRows.map { it.conversationId }.toSet())
                var targetConv: Conversation? = null

                if (commonConvIds.isNotEmpty()) {
                    val allConvs = NetworkClient.api.getConversations()
                    targetConv = allConvs.firstOrNull { it.id in commonConvIds && it.type == "direct" }
                }

                if (targetConv == null) {
                    // Create new direct conversation
                    val created = NetworkClient.api.createConversation(
                        mapOf("type" to "direct", "created_by" to myId)
                    )
                    targetConv = created.first()
                    // Add members
                    NetworkClient.api.addConversationMembers(
                        listOf(
                            mapOf("conversation_id" to targetConv.id, "user_id" to myId, "role" to "owner"),
                            mapOf("conversation_id" to targetConv.id, "user_id" to contact.contactUserId, "role" to "member")
                        )
                    )
                }

                _activeConversation.value = targetConv
                loadMessages(targetConv.id)
                onOpenChatScreen()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to open conversation: ${e.message}"
            }
        }
    }

    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            try {
                val msgs = NetworkClient.api.getMessages("eq.$conversationId")
                _messages.value = msgs
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load messages: ${e.message}"
            }
        }
    }

    fun sendMessage(content: String) {
        val myId = tokenManager.getUserId() ?: return
        val conv = _activeConversation.value ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            try {
                val created = NetworkClient.api.sendMessage(
                    mapOf(
                        "conversation_id" to conv.id,
                        "sender_id" to myId,
                        "content" to content.trim(),
                        "message_type" to "text"
                    )
                )
                val msg = created.firstOrNull()
                if (msg != null && _messages.value.none { it.id == msg.id }) {
                    _messages.value = _messages.value + msg
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send message: ${e.message}"
            }
        }
    }

    fun sendTyping(isTyping: Boolean) {
        // Handled via Realtime broadcast
    }

    fun startCall(receiverId: String, callType: String = "video") {
        val myId = tokenManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                val roomName = "viyo_call_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                val created = NetworkClient.api.createCallRecord(
                    mapOf(
                        "caller_id" to myId,
                        "receiver_id" to receiverId,
                        "room_name" to roomName,
                        "call_type" to callType,
                        "status" to "ringing"
                    )
                )
                val call = created.first()
                _activeCall.value = call

                // Request LiveKit Token from Supabase Edge Function
                val tokenRes = NetworkClient.api.generateLiveKitToken(
                    mapOf(
                        "room_name" to roomName,
                        "participant_identity" to myId,
                        "participant_name" to (_currentUser.value?.username ?: myId)
                    )
                )

                val token = tokenRes.token
                val url = tokenRes.livekitUrl ?: NetworkClient.LIVEKIT_URL
                if (token != null) {
                    liveKitManager.connect(url = url, token = token, video = callType == "video")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Call initiation notice: ${e.message}"
            }
        }
    }

    fun acceptIncomingCall(incoming: IncomingCallData, onConnected: () -> Unit) {
        val myId = tokenManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                NetworkClient.api.updateCallRecord("eq.${incoming.callId}", mapOf("status" to "accepted"))
                _incomingCall.value = null

                // Request LiveKit Token from Supabase Edge Function
                val tokenRes = NetworkClient.api.generateLiveKitToken(
                    mapOf(
                        "room_name" to incoming.roomName,
                        "participant_identity" to myId,
                        "participant_name" to (_currentUser.value?.username ?: myId)
                    )
                )

                val token = tokenRes.token
                val url = tokenRes.livekitUrl ?: NetworkClient.LIVEKIT_URL
                if (token != null) {
                    liveKitManager.connect(url = url, token = token, video = incoming.callType == "video")
                }
                onConnected()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to accept call: ${e.message}"
                _incomingCall.value = null
            }
        }
    }

    fun rejectIncomingCall(incoming: IncomingCallData) {
        viewModelScope.launch {
            try {
                NetworkClient.api.updateCallRecord("eq.${incoming.callId}", mapOf("status" to "rejected"))
            } catch (e: Exception) {
                // Ignore
            } finally {
                _incomingCall.value = null
            }
        }
    }

    fun endCall() {
        val call = _activeCall.value
        if (call != null) {
            viewModelScope.launch {
                try {
                    NetworkClient.api.updateCallRecord("eq.${call.id}", mapOf("status" to "ended"))
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    liveKitManager.disconnect()
                    _activeCall.value = null
                }
            }
        } else {
            liveKitManager.disconnect()
        }
    }

    fun createMeeting(title: String, onSuccess: () -> Unit) {
        val myId = tokenManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                val code = UUID.randomUUID().toString().take(8).uppercase()
                val room = "viyo_mtg_$code"
                val created = NetworkClient.api.createMeeting(
                    mapOf("meeting_code" to code, "host_id" to myId, "title" to title, "room_name" to room)
                )
                val mtg = created.first()
                _activeMeeting.value = mtg

                val tokenRes = NetworkClient.api.generateLiveKitToken(
                    mapOf(
                        "room_name" to room,
                        "participant_identity" to myId,
                        "participant_name" to (_currentUser.value?.username ?: myId)
                    )
                )
                val token = tokenRes.token
                val url = tokenRes.livekitUrl ?: NetworkClient.LIVEKIT_URL
                if (token != null) {
                    liveKitManager.connect(url = url, token = token, video = true)
                }
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create meeting: ${e.message}"
            }
        }
    }

    fun joinMeeting(code: String, onSuccess: () -> Unit) {
        val myId = tokenManager.getUserId() ?: return
        viewModelScope.launch {
            try {
                val results = NetworkClient.api.getMeetingByCode("eq.${code.trim().uppercase()}")
                val mtg = results.firstOrNull()
                if (mtg == null) {
                    _errorMessage.value = "Meeting not found"
                    return@launch
                }
                _activeMeeting.value = mtg

                val tokenRes = NetworkClient.api.generateLiveKitToken(
                    mapOf(
                        "room_name" to mtg.roomName,
                        "participant_identity" to myId,
                        "participant_name" to (_currentUser.value?.username ?: myId)
                    )
                )
                val token = tokenRes.token
                val url = tokenRes.livekitUrl ?: NetworkClient.LIVEKIT_URL
                if (token != null) {
                    liveKitManager.connect(url = url, token = token, video = true)
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
        realtimeClient.disconnect()
        tokenManager.clear()
        NetworkClient.currentAuthToken = null
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

    // --- ViyoRealtimeListener ---
    override fun onPresenceUpdate(userId: String, isOnline: Boolean, lastSeen: String?) {
        _contacts.value = _contacts.value.map { c ->
            if (c.contactUserId == userId) c.copy(isOnline = isOnline, lastSeen = lastSeen ?: c.lastSeen) else c
        }
        _searchResults.value = _searchResults.value.map { u ->
            if (u.id == userId) u.copy(isOnline = isOnline, lastSeen = lastSeen ?: u.lastSeen) else u
        }
    }

    override fun onNewMessage(message: Message) {
        val conv = _activeConversation.value
        if (conv != null && conv.id == message.conversationId) {
            if (_messages.value.none { it.id == message.id }) {
                _messages.value = _messages.value + message
            }
        }
    }

    override fun onContactRequestReceived(requestId: String, senderId: String) {
        loadPendingRequests()
    }

    override fun onContactRequestAccepted(userId: String) {
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
        realtimeClient.disconnect()
        liveKitManager.disconnect()
    }
}
