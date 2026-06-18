package com.github.biltudas1.sequence

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.ui.LoginScreen
import com.github.biltudas1.sequence.ui.RoomEntryScreen
import com.github.biltudas1.sequence.ui.WebRTCScreen
import com.github.biltudas1.sequence.ui.contacts.ContactsScreen
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
                val accessTokenState = dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = "UNDEFINED")
                val accessToken = accessTokenState.value

                if (accessToken == "UNDEFINED") {
                    // Prevent flicker by showing a blank screen while loading session
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = if (accessToken == null) "login" else "contacts"
                    ) {
                        composable("login") {
                            LoginScreen(onLoginSuccess = {
                                navController.navigate("contacts") {
                                    popUpTo("login") { inclusive = true }
                                }
                            })
                        }
                        composable("contacts") {
                            ContactsScreen(onContactClick = { _ ->
                                navController.navigate("room_entry")
                            })
                        }
                        composable("room_entry") {
                            RoomEntryScreen(
                                onJoinRoom = { roomId, serverUrl ->
                                    navController.navigate("webrtc_call/$roomId?serverUrl=$serverUrl")
                                }
                            )
                        }
                        composable("webrtc_call/{roomId}?serverUrl={serverUrl}") { backStackEntry ->
                            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                            val serverUrl = backStackEntry.arguments?.getString("serverUrl") ?: ""
                            WebRTCScreen(
                                roomId = roomId,
                                serverUrl = serverUrl,
                                accessToken = accessToken as? String,
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
