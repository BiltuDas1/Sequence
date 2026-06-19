package com.github.biltudas1.sequence

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.LoginScreen
import com.github.biltudas1.sequence.ui.RoomEntryScreen
import com.github.biltudas1.sequence.ui.WebRTCScreen
import com.github.biltudas1.sequence.ui.contacts.ContactsScreen
import com.github.biltudas1.sequence.ui.theme.SequenceTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    private val incomingRoomId = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val roomId = intent.getStringExtra("roomId")
        Log.d("MainActivity", "onNewIntent: roomId=$roomId")
        if (roomId != null) {
            incomingRoomId.value = roomId
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initial check for intent
        intent.getStringExtra("roomId")?.let {
            incomingRoomId.value = it
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val dataStoreManager = DataStoreManager(this)
        val authService = AuthService(OkHttpClient(), dataStoreManager)
        
        setContent {
            SequenceTheme(darkTheme = true) {
                val navController = rememberNavController()
                val accessTokenState = dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = "UNDEFINED")
                val accessToken = accessTokenState.value
                val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = null)
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        Log.w("MainActivity", "Notification permission denied")
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                LaunchedEffect(accessToken, serverConfig) {
                    if (accessToken != null && accessToken != "UNDEFINED" && serverConfig != null && serverConfig!!.isValid()) {
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val token = task.result
                                Log.d("MainActivity", "FCM Token: $token")
                                scope.launch {
                                    authService.updateFcmToken(serverConfig!!, accessToken, token)
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(accessToken) {
                    if (accessToken == null && currentRoute != "login") {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                // Handle incoming call room navigation
                val roomId by incomingRoomId
                LaunchedEffect(roomId, accessToken, serverConfig) {
                    if (roomId != null && accessToken != null && accessToken != "UNDEFINED" && serverConfig != null) {
                        val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                        val baseUrl = serverConfig!!.cleanEndpoint
                        val fullUrl = "$protocol://$baseUrl/room/$roomId"
                        
                        Log.d("MainActivity", "Navigating to call: $fullUrl")
                        // Avoid redundant navigation if already in the call
                        if (currentRoute?.contains("webrtc_call") != true) {
                            navController.navigate("webrtc_call/$roomId?serverUrl=$fullUrl")
                        }
                        
                        // Reset the state after handling
                        incomingRoomId.value = null
                    }
                }

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
                            ContactsScreen(onContactClick = { contact ->
                                scope.launch {
                                    if (accessToken != null && serverConfig != null) {
                                        val result = authService.sendVoiceCall(serverConfig!!, accessToken, contact.email)
                                        if (result.isSuccess) {
                                            val callRoomId = result.getOrNull()?.data?.roomId
                                            if (callRoomId != null) {
                                                val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                                val baseUrl = serverConfig!!.cleanEndpoint
                                                val fullUrl = "$protocol://$baseUrl/room/$callRoomId"
                                                navController.navigate("webrtc_call/$callRoomId?serverUrl=$fullUrl")
                                            }
                                        }
                                    }
                                }
                            })
                        }
                        composable("room_entry") {
                            RoomEntryScreen(
                                onJoinRoom = { rId, serverUrl ->
                                    navController.navigate("webrtc_call/$rId?serverUrl=$serverUrl")
                                }
                            )
                        }
                        composable("webrtc_call/{roomId}?serverUrl={serverUrl}") { backStackEntry ->
                            val rId = backStackEntry.arguments?.getString("roomId") ?: ""
                            val sUrl = backStackEntry.arguments?.getString("serverUrl") ?: ""
                            WebRTCScreen(
                                roomId = rId,
                                serverUrl = sUrl,
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
}
