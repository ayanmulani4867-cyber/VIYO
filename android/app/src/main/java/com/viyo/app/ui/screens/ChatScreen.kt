package com.viyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
fun ChatScreen(
    viewModel: ViyoViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCall: () -> Unit
) {
    val activeContact by viewModel.activeChatContact.collectAsState()
    val activeConv by viewModel.activeConversation.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = ViyoDarkBg,
        topBar = {
            Surface(
                color = ViyoDarkSurface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        // Avatar with Presence Dot
                        Box {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = ViyoViolet.copy(alpha = 0.3f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (activeContact?.displayName ?: "U").take(1).uppercase(),
                                        color = ViyoVioletLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            // Presence Indicator Dot
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        color = if (activeContact?.isOnline == true) Color(0xFF10B981) else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                        }

                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = activeContact?.displayName ?: activeContact?.username ?: "Chat",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (activeContact?.isOnline == true) "Online" else "Offline",
                                color = if (activeContact?.isOnline == true) Color(0xFF10B981) else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Direct Call shortcuts
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                activeContact?.let {
                                    viewModel.startCall(it.contactUserId, "video")
                                    onNavigateToCall()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video Call",
                                tint = ViyoVioletLight
                            )
                        }

                        IconButton(
                            onClick = {
                                activeContact?.let {
                                    viewModel.startCall(it.contactUserId, "voice")
                                    onNavigateToCall()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Voice Call",
                                tint = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = ViyoDarkSurface,
                modifier = Modifier.imePadding()
            ) {
                Column {
                    // Typing indicator
                    if (isPeerTyping) {
                        Text(
                            text = "${activeContact?.displayName ?: "User"} is typing...",
                            color = ViyoVioletLight,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = {
                                messageText = it
                                viewModel.sendTyping(it.isNotBlank())
                            },
                            placeholder = { Text("Message...", color = Color.Gray) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ViyoDarkCard,
                                unfocusedContainerColor = ViyoDarkCard,
                                focusedBorderColor = ViyoViolet,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            maxLines = 4
                        )

                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    val text = messageText
                                    messageText = ""
                                    viewModel.sendMessage(text)
                                    viewModel.sendTyping(false)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(ViyoViolet, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No messages yet. Send a message to start conversation!",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(messages) { msg ->
                    val isMe = msg.senderId == currentUser?.id || msg.senderUsername == currentUser?.username

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 16.dp
                            ),
                            color = if (isMe) ViyoViolet else ViyoDarkCard,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = msg.content ?: "",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                val timeText = if (msg.createdAt.contains("T")) {
                                    msg.createdAt.substringAfter("T").take(5)
                                } else {
                                    msg.createdAt.takeLast(5)
                                }

                                Text(
                                    text = timeText,
                                    color = if (isMe) Color.White.copy(alpha = 0.7f) else Color.Gray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

