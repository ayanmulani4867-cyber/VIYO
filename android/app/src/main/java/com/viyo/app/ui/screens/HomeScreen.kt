package com.viyo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun HomeScreen(
    viewModel: ViyoViewModel,
    onNavigateToCall: () -> Unit,
    onNavigateToMeetings: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val incomingCall by viewModel.incomingCall.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showSearchSection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadInitialData()
    }

    // Incoming Call Banner / Dialog
    if (incomingCall != null) {
        val call = incomingCall!!
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss without action */ },
            containerColor = ViyoDarkSurface,
            title = {
                Text(
                    text = "Incoming ${if (call.callType == "video") "Video" else "Voice"} Call",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = ViyoViolet
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = call.callerDisplayName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = call.callerDisplayName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${call.callerUsername}",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acceptIncomingCall(call) {
                            onNavigateToCall()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Accept", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { viewModel.rejectIncomingCall(call) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Decline", color = Color.White)
                }
            }
        )
    }

    Scaffold(
        containerColor = ViyoDarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VIYO",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "See. Talk. Connect.",
                        fontSize = 11.sp,
                        color = ViyoVioletLight,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showSearchSection = !showSearchSection }) {
                        Icon(
                            imageVector = if (showSearchSection) Icons.Default.Close else Icons.Default.PersonAdd,
                            contentDescription = "Search Friends",
                            tint = ViyoVioletLight
                        )
                    }

                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = Color.Gray
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner if any
            if (errorMessage != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Red.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFFF8888),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                }
            }

            // User Profile Card & Quick Actions
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = ViyoDarkSurface
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = ViyoViolet
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (currentUser?.profile?.displayName ?: currentUser?.username ?: "U").take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser?.profile?.displayName ?: currentUser?.username ?: "User",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${currentUser?.username ?: ""} • Connected",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = onNavigateToMeetings,
                                colors = ButtonDefaults.buttonColors(containerColor = ViyoViolet),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Meeting", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Search Friends Section
            if (showSearchSection) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = ViyoDarkSurface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Search & Add Friends",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        viewModel.searchUsers(it)
                                    },
                                    placeholder = { Text("Search by username...", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ViyoDarkCard,
                                        unfocusedContainerColor = ViyoDarkCard,
                                        focusedBorderColor = ViyoViolet,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                IconButton(
                                    onClick = { viewModel.searchUsers(searchQuery) },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(ViyoViolet, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                                }
                            }

                            if (isSearching) {
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ViyoViolet
                                )
                            }

                            if (searchResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    searchResults.forEach { user ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = ViyoDarkCard
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box {
                                                        Surface(
                                                            modifier = Modifier.size(36.dp),
                                                            shape = CircleShape,
                                                            color = ViyoViolet.copy(alpha = 0.3f)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    text = user.displayName.take(1).uppercase(),
                                                                    color = ViyoVioletLight,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                        // Presence dot
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .align(Alignment.BottomEnd)
                                                                .background(
                                                                    color = if (user.isOnline) Color(0xFF10B981) else Color.Gray,
                                                                    shape = CircleShape
                                                                )
                                                        )
                                                    }

                                                    Column {
                                                        Text(
                                                            text = user.displayName,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(
                                                            text = "@${user.username} • ${if (user.isOnline) "Online" else "Offline"}",
                                                            color = if (user.isOnline) Color(0xFF10B981) else Color.Gray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.sendContactRequest(user.username) {
                                                            viewModel.clearError()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ViyoViolet),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Add", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pending Friend Requests Section
            if (pendingRequests.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF312E81).copy(alpha = 0.4f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = ViyoVioletLight, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Friend Requests (${pendingRequests.size})",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            pendingRequests.forEach { req ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ViyoDarkSurface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = req.senderDisplayName ?: req.senderUsername ?: "User",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "@${req.senderUsername ?: ""}",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = { viewModel.acceptContactRequest(req.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Accept", fontSize = 11.sp, color = Color.White)
                                            }

                                            Button(
                                                onClick = { viewModel.rejectContactRequest(req.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Reject", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Contacts Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contacts (${contacts.size})",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = { viewModel.loadContacts() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Gray)
                    }
                }
            }

            if (contacts.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = ViyoDarkSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = ViyoVioletLight, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No contacts yet",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Search for your friends by username and add them to start real-time chatting and calls.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(contacts) { contact ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = ViyoDarkSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // User Info with Online Indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box {
                                    Surface(
                                        modifier = Modifier.size(44.dp),
                                        shape = CircleShape,
                                        color = ViyoDarkCard
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = contact.displayName.take(1).uppercase(),
                                                color = ViyoVioletLight,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }

                                    // Presence Dot
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                color = if (contact.isOnline) Color(0xFF10B981) else Color.Gray,
                                                shape = CircleShape
                                            )
                                    )
                                }

                                Column {
                                    Text(
                                        text = contact.displayName,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "@${contact.username} • ",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = if (contact.isOnline) "Online" else "Offline",
                                            color = if (contact.isOnline) Color(0xFF10B981) else Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = if (contact.isOnline) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            // Actions: Chat, Video Call, Voice Call
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Chat button
                                IconButton(
                                    onClick = {
                                        viewModel.openChat(contact) {
                                            onNavigateToChat()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(ViyoViolet.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = "Chat", tint = ViyoVioletLight, modifier = Modifier.size(18.dp))
                                }

                                // Video call
                                IconButton(
                                    onClick = {
                                        viewModel.startCall(contact.contactUserId, "video")
                                        onNavigateToCall()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(ViyoViolet.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = ViyoVioletLight, modifier = Modifier.size(18.dp))
                                }

                                // Voice call
                                IconButton(
                                    onClick = {
                                        viewModel.startCall(contact.contactUserId, "voice")
                                        onNavigateToCall()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
