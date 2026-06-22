package com.github.biltudas1.sequence

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.biltudas1.sequence.data.ContactRepository
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.*
import com.github.biltudas1.sequence.ui.contacts.ContactsScreen
import com.github.biltudas1.sequence.ui.theme.SequenceTheme
import com.github.biltudas1.sequence.ui.utils.CallStatusManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    private val incomingRoomId = mutableStateOf<String?>(null)
    private var isCallExternal = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val roomId = intent.getStringExtra("roomId")
        Log.d("MainActivity", "onNewIntent: roomId=$roomId")
        if (roomId != null) {
            isCallExternal = true
            incomingRoomId.value = roomId
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Allow this activity to show over the lock screen for ongoing calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        intent.getStringExtra("roomId")?.let {
            isCallExternal = true
            incomingRoomId.value = it
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val dataStoreManager = DataStoreManager(this)
        val authService = AuthService(OkHttpClient(), dataStoreManager)
        val contactRepository = ContactRepository(this, authService)
        
        setContent {
            SequenceTheme(darkTheme = true) {
                val context = LocalContext.current
                var isOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }
                val lifecycleOwner = LocalLifecycleOwner.current

                // Re-check battery optimization when app returns to foreground
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isOptimized = isBatteryOptimized(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                if (isOptimized) {
                    BatteryOptimizationScreen(
                        onCheckAgain = {
                            isOptimized = isBatteryOptimized(context)
                        }
                    )
                } else {
                    val navController = rememberNavController()
                    val accessTokenState = dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = "UNDEFINED")
                    val accessToken = accessTokenState.value
                    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = null)
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val scope = rememberCoroutineScope()

                    // Dynamic security: Only allow lockscreen visibility during a call
                    LaunchedEffect(currentRoute) {
                        val isCallScreen = currentRoute?.contains("webrtc_call") == true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(isCallScreen)
                        } else {
                            @Suppress("DEPRECATION")
                            if (isCallScreen) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                            }
                        }
                    }

                    // --- Permission Launchers ---
                    
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (!isGranted) Log.w("MainActivity", "Notification permission denied")
                    }

                    val requiredPermissions = mutableListOf<String>().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        add(Manifest.permission.READ_PHONE_STATE)
                    }

                    val initialPermissionsLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        // Handle result
                    }

                    LaunchedEffect(Unit) {
                        initialPermissionsLauncher.launch(requiredPermissions.toTypedArray())
                    }

                    var pendingNavigationUrl by remember { mutableStateOf<Pair<String, String>?>(null) }
                    val audioPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted && pendingNavigationUrl != null) {
                            val (rId, url) = pendingNavigationUrl!!
                            navController.navigate("webrtc_call/$rId?serverUrl=$url")
                            pendingNavigationUrl = null
                        } else {
                            Toast.makeText(context, "Microphone permission is required for calls", Toast.LENGTH_LONG).show()
                        }
                    }

                    fun navigateToCallWithPermission(rId: String, url: String) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            navController.navigate("webrtc_call/$rId?serverUrl=$url")
                        } else {
                            pendingNavigationUrl = rId to url
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    val roomId by incomingRoomId
                    LaunchedEffect(roomId, accessToken, serverConfig) {
                        if (roomId != null && accessToken != null && accessToken != "UNDEFINED" && serverConfig != null) {
                            val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                            val baseUrl = serverConfig!!.cleanEndpoint
                            val fullUrl = "$protocol://$baseUrl/room/$roomId"
                            
                            if (currentRoute?.contains("webrtc_call") != true) {
                                navigateToCallWithPermission(roomId!!, fullUrl)
                            }
                            incomingRoomId.value = null
                        }
                    }

                    if (accessToken == "UNDEFINED") {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = if (accessToken == null) "login" else "contacts",
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(400)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(400)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(400)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(400)
                                )
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.background)
                        ) {
                            composable("login") {
                                LoginScreen(onLoginSuccess = {
                                    navController.navigate("contacts") { popUpTo("login") { inclusive = true } }
                                })
                            }
                            composable("contacts") {
                                ContactsScreen(
                                    onContactClick = { contact ->
                                        val callStatusManager = CallStatusManager(context)
                                        if (callStatusManager.isUserOnAnotherCall()) {
                                            Toast.makeText(context, "Cannot place a call while on another call", Toast.LENGTH_SHORT).show()
                                            return@ContactsScreen
                                        }
                                        isCallExternal = false // Outgoing call
                                        scope.launch {
                                            if (accessToken != null && serverConfig != null) {
                                                val result = authService.sendVoiceCall(serverConfig!!, accessToken, contact.email)
                                                result.getOrNull()?.data?.roomId?.let { rId ->
                                                    val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                                    val fullUrl = "$protocol://${serverConfig!!.cleanEndpoint}/room/$rId"
                                                    navigateToCallWithPermission(rId, fullUrl)
                                                }
                                            }
                                        }
                                    },
                                    onSettingsClick = {
                                        navController.navigate("settings")
                                    }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onAboutClick = {
                                        navController.navigate("about")
                                    },
                                    onWebRTCConfigClick = {
                                        navController.navigate("webrtc_config")
                                    },
                                    onDataUsageClick = {
                                        navController.navigate("data_usage")
                                    },
                                    onAudioQualityClick = {
                                        navController.navigate("audio_quality")
                                    },
                                    onLogoutClick = {
                                        scope.launch {
                                            dataStoreManager.clearTokens()
                                            contactRepository.clearLocalData()
                                        }
                                    }
                                )
                            }
                            composable("audio_quality") {
                                AudioQualityScreen(onBackClick = {
                                    navController.popBackStack()
                                })
                            }
                            composable("data_usage") {
                                DataUsageScreen(onBackClick = {
                                    navController.popBackStack()
                                })
                            }
                            composable("webrtc_config") {
                                WebRTCConfigScreen(onBackClick = {
                                    navController.popBackStack()
                                })
                            }
                            composable("about") {
                                AboutScreen(onBackClick = {
                                    navController.popBackStack()
                                })
                            }
                            composable("room_entry") {
                                RoomEntryScreen(
                                    onJoinRoom = { rId, url -> navigateToCallWithPermission(rId, url) }
                                )
                            }
                            composable("webrtc_call/{roomId}?serverUrl={serverUrl}") { backStackEntry ->
                                WebRTCScreen(
                                    roomId = backStackEntry.arguments?.getString("roomId") ?: "",
                                    serverUrl = backStackEntry.arguments?.getString("serverUrl") ?: "",
                                    accessToken = accessToken,
                                    onCallStopped = {
                                        if (isCallExternal) {
                                            finishAndRemoveTask()
                                        } else {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
