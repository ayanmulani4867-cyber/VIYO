package com.viyo.app.webrtc

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalVideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveKitManager(private val context: Context) {
    private var currentRoom: Room? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted = _isMicMuted.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled = _isCameraEnabled.asStateFlow()

    suspend fun connect(url: String, token: String, video: Boolean = true): Room {
        val room = LiveKit.create(appContext = context)
        currentRoom = room

        try {
            room.connect(url, token)
            _isConnected.value = true

            // Publish audio & video
            room.localParticipant.setMicrophoneEnabled(true)
            if (video) {
                room.localParticipant.setCameraEnabled(true)
            }
        } catch (e: Exception) {
            _isConnected.value = false
            throw e
        }

        return room
    }

    suspend fun toggleMic() {
        currentRoom?.let { room ->
            val newMuted = !_isMicMuted.value
            room.localParticipant.setMicrophoneEnabled(!newMuted)
            _isMicMuted.value = newMuted
        }
    }

    suspend fun toggleCamera() {
        currentRoom?.let { room ->
            val newCamera = !_isCameraEnabled.value
            room.localParticipant.setCameraEnabled(newCamera)
            _isCameraEnabled.value = newCamera
        }
    }

    fun disconnect() {
        currentRoom?.disconnect()
        currentRoom = null
        _isConnected.value = false
    }
}

