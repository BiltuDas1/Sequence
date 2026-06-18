package com.github.biltudas1.sequence

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.ui.LoginScreen
import com.github.biltudas1.sequence.ui.RoomEntryScreen
import com.github.biltudas1.sequence.ui.WebRTCScreen
import com.github.biltudas1.sequence.ui.theme.SequenceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val dataStoreManager = DataStoreManager(this)
        
        setContent {
            SequenceTheme(darkTheme = true) {
                val navController = rememberNavController()
                val accessToken by dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = null)

                NavHost(
                    navController = navController,
                    startDestination = if (accessToken == null) "login" else "room_entry"
                ) {
                    composable("login") {
                        LoginScreen(onLoginSuccess = {
                            navController.navigate("room_entry") {
                                popUpTo("login") { inclusive = true }
                            }
                        })
                    }
                    composable("room_entry") {
                        RoomEntryScreen(
                            onJoinRoom = { roomId, serverUrl ->
                                navController.navigate("webrtc_call/$roomId?serverUrl=$serverUrl")
                            },
                            onLogout = {
                                navController.navigate("login") {
                                    popUpTo("room_entry") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("webrtc_call/{roomId}?serverUrl={serverUrl}") { backStackEntry ->
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                        val serverUrl = backStackEntry.arguments?.getString("serverUrl") ?: ""
                        WebRTCScreen(
                            roomId = roomId,
                            serverUrl = serverUrl,
                            accessToken = accessToken,
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
