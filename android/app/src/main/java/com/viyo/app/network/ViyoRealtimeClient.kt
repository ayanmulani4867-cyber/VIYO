package com.viyo.app.network

import android.util.Log
import com.viyo.app.model.IncomingCallData
import com.viyo.app.model.Message
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

interface ViyoRealtimeListener {
    fun onPresenceUpdate(userId: String, isOnline: Boolean, lastSeen: String?)
    fun onNewMessage(message: Message)
    fun onContactRequestReceived(requestId: String, senderId: String)
    fun onContactRequestAccepted(userId: String)
    fun onIncomingCall(call: IncomingCallData)
    fun onCallAccepted(callId: String)
    fun onCallRejected(callId: String)
    fun onCallEnded(callId: String)
    fun onUserTyping(conversationId: String, userId: String, isTyping: Boolean)
}

class ViyoRealtimeClient(private val listener: ViyoRealtimeListener) {
    private val tag = "ViyoRealtime"
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val refCounter = AtomicInteger(1)
    private var currentUserId: String? = null
    private var isManuallyClosed = false

    fun connect(userId: String) {
        currentUserId = userId
        isManuallyClosed = false
        val wsUrl = NetworkClient.getRealtimeWebSocketUrl()
        Log.d(tag, "Connecting to Supabase Realtime: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = NetworkClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "Supabase Realtime WebSocket connected.")
                joinChannels(userId)
                startHeartbeatLoop()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                stopHeartbeatLoop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stopHeartbeatLoop()
                if (!isManuallyClosed) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "Supabase Realtime failure: ${t.message}")
                stopHeartbeatLoop()
                if (!isManuallyClosed) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun nextRef(): String = refCounter.getAndIncrement().toString()

    private fun joinChannels(userId: String) {
        // 1. Join Global Presence Channel
        val presenceJoin = JSONObject().apply {
            put("topic", "realtime:presence:global")
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("presence", JSONObject().apply {
                        put("key", userId)
                    })
                })
            })
            put("ref", nextRef())
        }
        webSocket?.send(presenceJoin.toString())

        // 2. Track My Online Presence in the Channel
        val trackMsg = JSONObject().apply {
            put("topic", "realtime:presence:global")
            put("event", "presence")
            put("payload", JSONObject().apply {
                put("type", "presence")
                put("event", "TRACK")
                put("payload", JSONObject().apply {
                    put("user_id", userId)
                    put("is_online", true)
                    put("online_at", System.currentTimeMillis())
                })
            })
            put("ref", nextRef())
        }
        webSocket?.send(trackMsg.toString())

        // 3. Join Postgres Changes for Messages
        val messagesJoin = JSONObject().apply {
            put("topic", "realtime:public:messages")
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "INSERT")
                            put("schema", "public")
                            put("table", "messages")
                        })
                    })
                })
            })
            put("ref", nextRef())
        }
        webSocket?.send(messagesJoin.toString())

        // 4. Join Postgres Changes for Calls
        val callsJoin = JSONObject().apply {
            put("topic", "realtime:public:calls")
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "calls")
                        })
                    })
                })
            })
            put("ref", nextRef())
        }
        webSocket?.send(callsJoin.toString())

        // 5. Join Postgres Changes for Contact Requests
        val requestsJoin = JSONObject().apply {
            put("topic", "realtime:public:contact_requests")
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "contact_requests")
                        })
                    })
                })
            })
            put("ref", nextRef())
        }
        webSocket?.send(requestsJoin.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val topic = json.optString("topic")
            val event = json.optString("event")
            val payload = json.optJSONObject("payload") ?: JSONObject()

            when (event) {
                "presence_state" -> {
                    // Current presence list across all online clients
                    val keys = payload.keys()
                    while (keys.hasNext()) {
                        val uid = keys.next()
                        listener.onPresenceUpdate(uid, true, null)
                    }
                }

                "presence_diff" -> {
                    // Joins
                    val joins = payload.optJSONObject("joins")
                    if (joins != null) {
                        val joinKeys = joins.keys()
                        while (joinKeys.hasNext()) {
                            val uid = joinKeys.next()
                            listener.onPresenceUpdate(uid, true, null)
                        }
                    }
                    // Leaves
                    val leaves = payload.optJSONObject("leaves")
                    if (leaves != null) {
                        val leaveKeys = leaves.keys()
                        while (leaveKeys.hasNext()) {
                            val uid = leaveKeys.next()
                            listener.onPresenceUpdate(uid, false, null)
                        }
                    }
                }

                "postgres_changes" -> {
                    val data = payload.optJSONObject("data") ?: payload
                    val record = data.optJSONObject("record") ?: JSONObject()
                    val table = data.optString("table", payload.optString("table"))

                    when (table) {
                        "messages" -> {
                            val id = record.optString("id")
                            val convId = record.optString("conversation_id")
                            val senderId = record.optString("sender_id")
                            val content = record.optString("content")
                            val msgType = record.optString("message_type", "text")
                            val createdAt = record.optString("created_at")

                            val msg = Message(
                                id = id,
                                conversationId = convId,
                                senderId = senderId,
                                senderUsername = null,
                                senderDisplayName = null,
                                content = content,
                                messageType = msgType,
                                createdAt = createdAt
                            )
                            listener.onNewMessage(msg)
                        }

                        "calls" -> {
                            val callId = record.optString("id")
                            val callerId = record.optString("caller_id")
                            val receiverId = record.optString("receiver_id")
                            val roomName = record.optString("room_name")
                            val callType = record.optString("call_type", "video")
                            val status = record.optString("status")

                            val myId = currentUserId ?: ""
                            if (receiverId == myId && status == "ringing") {
                                listener.onIncomingCall(
                                    IncomingCallData(
                                        callId = callId,
                                        roomName = roomName,
                                        callType = callType,
                                        callerId = callerId,
                                        callerUsername = "User",
                                        callerDisplayName = "Incoming Call",
                                        callerAvatarUrl = null
                                    )
                                )
                            } else if (callerId == myId && status == "accepted") {
                                listener.onCallAccepted(callId)
                            } else if (status == "rejected") {
                                listener.onCallRejected(callId)
                            } else if (status == "ended") {
                                listener.onCallEnded(callId)
                            }
                        }

                        "contact_requests" -> {
                            val reqId = record.optString("id")
                            val senderId = record.optString("sender_id")
                            val receiverId = record.optString("receiver_id")
                            val status = record.optString("status")

                            val myId = currentUserId ?: ""
                            if (receiverId == myId && status == "pending") {
                                listener.onContactRequestReceived(reqId, senderId)
                            } else if (senderId == myId && status == "accepted") {
                                listener.onContactRequestAccepted(receiverId)
                            }
                        }
                    }
                }

                "broadcast" -> {
                    val eventType = payload.optString("event")
                    if (eventType == "typing") {
                        val convId = payload.optString("conversation_id")
                        val uId = payload.optString("user_id")
                        val isTyping = payload.optBoolean("is_typing", false)
                        listener.onUserTyping(convId, uId, isTyping)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing Supabase Realtime message", e)
        }
    }

    private fun startHeartbeatLoop() {
        stopHeartbeatLoop()
        pingJob = scope.launch {
            while (isActive) {
                delay(25_000)
                try {
                    val heartbeat = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", nextRef())
                    }
                    webSocket?.send(heartbeat.toString())
                } catch (e: Exception) {
                    Log.w(tag, "Failed to send heartbeat", e)
                }
            }
        }
    }

    private fun stopHeartbeatLoop() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun scheduleReconnect() {
        val uid = currentUserId ?: return
        scope.launch {
            delay(3_000)
            if (!isManuallyClosed) {
                connect(uid)
            }
        }
    }

    fun disconnect() {
        isManuallyClosed = true
        stopHeartbeatLoop()
        webSocket?.close(1000, "User logged out")
        webSocket = null
        currentUserId = null
    }
}

