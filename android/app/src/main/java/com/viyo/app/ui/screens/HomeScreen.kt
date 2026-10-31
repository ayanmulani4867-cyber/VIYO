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
    onNavigateToMeetings: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadInitialData()
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
                        text = "Together, Wherever We Are",
                        fontSize = 11.sp,
                        color = ViyoVioletLight,
                        fontWeight = FontWeight.Medium
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = ViyoDarkSurface
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Hello, ${currentUser?.profile?.displayName ?: currentUser?.username ?: "Family Member"}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Instant family audio & video connection",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onNavigateToMeetings,
                                colors = ButtonDefaults.buttonColors(containerColor = ViyoViolet),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.VideoCall, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Meeting")
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Text(
                    text = "Family Members & Contacts",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (contacts.isEmpty()) {
                item {
                    Text(
                        text = "No contacts available. Start by discovering and inviting family members.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
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
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
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

                                Column {
                                    Text(
                                        text = contact.displayName,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "@${contact.username}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        viewModel.startCall(contact.contactUserId, "video")
                                        onNavigateToCall()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(ViyoViolet.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = ViyoVioletLight)
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.startCall(contact.contactUserId, "voice")
                                        onNavigateToCall()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = Color(0xFF10B981))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

