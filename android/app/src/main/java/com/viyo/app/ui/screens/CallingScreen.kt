package com.viyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viyo.app.theme.*
import com.viyo.app.viewmodel.ViyoViewModel
import kotlinx.coroutines.launch

@Composable
fun CallingScreen(
    viewModel: ViyoViewModel,
    onCallEnded: () -> Unit
) {
    val activeCall by viewModel.activeCall.collectAsState()
    val activeMeeting by viewModel.activeMeeting.collectAsState()
    val isMicMuted by viewModel.liveKitManager.isMicMuted.collectAsState()
    val isCameraEnabled by viewModel.liveKitManager.isCameraEnabled.collectAsState()
    val isConnected by viewModel.liveKitManager.isConnected.collectAsState()

    val scope = rememberCoroutineScope()

    val title = activeMeeting?.title ?: activeCall?.receiverDisplayName ?: activeCall?.receiverUsername ?: "In Call"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Participant placeholder / video area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(110.dp),
                shape = CircleShape,
                color = ViyoDarkCard
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = title.take(1).uppercase(),
                        color = ViyoVioletLight,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isConnected) "Connected (LiveKit WebRTC)" else "Connecting media stream...",
                color = if (isConnected) Color(0xFF10B981) else Color.Gray,
                fontSize = 13.sp
            )
        }

        // Floating Control Bar at Bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
            shape = RoundedCornerShape(32.dp),
            color = ViyoDarkSurface.copy(alpha = 0.9f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic toggle
                IconButton(
                    onClick = { scope.launch { viewModel.liveKitManager.toggleMic() } },
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (isMicMuted) Color.Red.copy(alpha = 0.2f) else ViyoDarkCard, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (isMicMuted) Color.Red else Color.White
                    )
                }

                // Camera toggle
                IconButton(
                    onClick = { scope.launch { viewModel.liveKitManager.toggleCamera() } },
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (!isCameraEnabled) Color.Red.copy(alpha = 0.2f) else ViyoDarkCard, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Camera",
                        tint = if (!isCameraEnabled) Color.Red else Color.White
                    )
                }

                // End call button
                IconButton(
                    onClick = {
                        if (activeMeeting != null) {
                            viewModel.leaveMeeting()
                        } else {
                            viewModel.endCall()
                        }
                        onCallEnded()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFFE11D48), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

