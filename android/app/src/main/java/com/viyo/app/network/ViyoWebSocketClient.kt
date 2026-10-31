package com.viyo.app.network

import android.util.Log
import com.viyo.app.model.IncomingCallData
import com.viyo.app.model.Message
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

interface ViyoWebSocketListener {
    fun onPresenceUpdate(userId: String, isOnline: Boolean, lastSeen: String?)
    fun onNewMessage(message: Message)
    fun onContactRequestReceived(requestId: String, senderId: String, senderUsername: String)
    fun onContactRequestAccepted(userId: String, username: String)
    fun onIncomingCall(call: IncomingCallData)
    fun onCallAccepted(callId: String)
    fun onCallRejected(callId: String)
    fun onCallEnded(callId: String)
    fun onUserTyping(conversationId: String, userId: String, isTyping: Boolean)
}

class ViyoWebSocketClient(private val listener: ViyoWebSocketListener) {
    private val tag = "ViyoWebSocket"
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentToken: String? = null
    private var isManuallyClosed = false

    fun connect(token: String) {
        currentToken = token
        isManuallyClosed = false
        val wsUrl = NetworkClient.getWebSocketUrl(token)
        Log.d(tag, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = NetworkClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connected successfully.")
                startPingLoop()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(tag, "WebSocket received: $text")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code / $reason")
                stopPingLoop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code / $reason")
                stopPingLoop()
                if (!isManuallyClosed) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "WebSocket failure: ${t.message}")
                stopPingLoop()
                if (!isManuallyClosed) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")

            when (event) {
                "user_presence" -> {
                    val userId = json.optString("user_id")
                    val isOnline = json.optBoolean("is_online", false)
                    val lastSeen = if (json.has("last_seen") && !json.isNull("last_seen")) json.getString("last_seen") else null
                    listener.onPresenceUpdate(userId, isOnline, lastSeen)
                }

                "new_message" -> {
                    val msgObj = json.optJSONObject("message")
                    if (msgObj != null) {
                        val msg = Message(
                            id = msgObj.optString("id"),
                            conversationId = msgObj.optString("conversation_id"),
                            senderId = msgObj.optString("sender_id"),
                            senderUsername = msgObj.optString("sender_username"),
                            senderDisplayName = msgObj.optString("sender_display_name", msgObj.optString("sender_username")),
                            content = msgObj.optString("content"),
                            messageType = msgObj.optString("message_type", "text"),
                            createdAt = msgObj.optString("created_at")
                        )
                        listener.onNewMessage(msg)
                    }
                }

                "contact_request_received" -> {
                    val reqId = json.optString("request_id")
                    val senderId = json.optString("sender_id")
                    val senderUsername = json.optString("sender_username")
                    listener.onContactRequestReceived(reqId, senderId, senderUsername)
                }

                "contact_request_accepted" -> {
                    val userId = json.optString("user_id")
                    val username = json.optString("username")
                    listener.onContactRequestAccepted(userId, username)
                }

                "incoming_call" -> {
                    val callId = json.optString("call_id")
                    val roomName = json.optString("room_name")
                    val callType = json.optString("call_type", "video")
                    val callerObj = json.optJSONObject("caller")
                    val callerId = callerObj?.optString("id") ?: ""
                    val callerUsername = callerObj?.optString("username") ?: ""
                    val callerDisplayName = callerObj?.optString("display_name") ?: callerUsername
                    val callerAvatarUrl = callerObj?.optString("avatar_url")

                    listener.onIncomingCall(
                        IncomingCallData(
                            callId = callId,
                            roomName = roomName,
                            callType = callType,
                            callerId = callerId,
                            callerUsername = callerUsername,
                            callerDisplayName = callerDisplayName,
                            callerAvatarUrl = callerAvatarUrl
                        )
                    )
                }

                "call_accepted" -> {
                    listener.onCallAccepted(json.optString("call_id"))
                }

                "call_rejected" -> {
                    listener.onCallRejected(json.optString("call_id"))
                }

                "call_ended" -> {
                    listener.onCallEnded(json.optString("call_id"))
                }

                "user_typing" -> {
                    val convId = json.optString("conversation_id")
                    val uId = json.optString("user_id")
                    val isTyping = json.optBoolean("is_typing", false)
                    listener.onUserTyping(convId, uId, isTyping)
                }

                "pong" -> {
                    // Heartbeat acknowledged
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing WebSocket event", e)
        }
    }

    fun sendTyping(conversationId: String, participantIds: List<String>, isTyping: Boolean) {
        val payload = JSONObject().apply {
            put("action", "typing")
            put("conversation_id", conversationId)
            put("participant_ids", org.json.JSONArray(participantIds))
            put("is_typing", isTyping)
        }
        webSocket?.send(payload.toString())
    }

    private fun startPingLoop() {
        stopPingLoop()
        pingJob = scope.launch {
            while (isActive) {
                delay(15_000)
                try {
                    val ping = JSONObject().apply { put("action", "ping") }
                    webSocket?.send(ping.toString())
                } catch (e: Exception) {
                    Log.w(tag, "Failed to send ping heartbeat", e)
                }
            }
        }
    }

    private fun stopPingLoop() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun scheduleReconnect() {
        val token = currentToken ?: return
        scope.launch {
            delay(3_000)
            if (!isManuallyClosed) {
                Log.d(tag, "Attempting WebSocket reconnect...")
                connect(token)
            }
        }
    }

    fun disconnect() {
        isManuallyClosed = true
        stopPingLoop()
        webSocket?.close(1000, "User logged out or app paused")
        webSocket = null
        currentToken = null
    }
}
