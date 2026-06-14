package com.github.biltudas1.sequence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.biltudas1.sequence.ui.LoginScreen
import com.github.biltudas1.sequence.ui.RoomEntryScreen
import com.github.biltudas1.sequence.ui.WebRTCScreen
import com.github.biltudas1.sequence.ui.theme.SequenceTheme
import com.github.biltudas1.sequence.ui.theme.SurfaceDim

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SequenceTheme(darkTheme = true) {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = SurfaceDim
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "login",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("login") {
                            LoginScreen(onLoginSuccess = {
                                navController.navigate("room_entry")
                            })
                        }
                        composable("room_entry") {
                            RoomEntryScreen(onJoinRoom = { roomId, serverUrl ->
                                navController.navigate("webrtc_call/$roomId?serverUrl=$serverUrl")
                            })
                        }
                        composable("webrtc_call/{roomId}?serverUrl={serverUrl}") { backStackEntry ->
                            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                            val serverUrl = backStackEntry.arguments?.getString("serverUrl") ?: ""
                            WebRTCScreen(
                                roomId = roomId,
                                serverUrl = serverUrl,
                                onCallStopped = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
