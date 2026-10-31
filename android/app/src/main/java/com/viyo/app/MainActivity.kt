package com.viyo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.viyo.app.theme.ViyoDarkBg
import com.viyo.app.theme.ViyoTheme
import com.viyo.app.ui.screens.*
import com.viyo.app.viewmodel.ViyoViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ViyoViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ViyoTheme {
                val permissionsState = rememberMultiplePermissionsState(
                    permissions = listOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.RECORD_AUDIO
                    )
                )

                LaunchedEffect(Unit) {
                    permissionsState.launchMultiplePermissionRequest()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ViyoDarkBg
                ) {
                    val navController = rememberNavController()
                    val token by viewModel.token.collectAsState()
                    val activeCall by viewModel.activeCall.collectAsState()
                    val activeMeeting by viewModel.activeMeeting.collectAsState()

                    // If in call or meeting, route to CallingScreen
                    LaunchedEffect(activeCall, activeMeeting) {
                        if (activeCall != null || activeMeeting != null) {
                            navController.navigate("calling")
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = if (token == null) "auth" else "home"
                    ) {
                        composable("auth") {
                            AuthScreen(
                                viewModel = viewModel,
                                onAuthSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToCall = { navController.navigate("calling") },
                                onNavigateToMeetings = { navController.navigate("meetings") }
                            )
                        }

                        composable("meetings") {
                            MeetingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onMeetingStarted = { navController.navigate("calling") }
                            )
                        }

                        composable("calling") {
                            CallingScreen(
                                viewModel = viewModel,
                                onCallEnded = {
                                    navController.popBackStack("home", inclusive = false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

