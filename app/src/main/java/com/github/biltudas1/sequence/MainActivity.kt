package com.github.biltudas1.sequence

import android.Manifest
import android.app.NotificationManager
import android.content.Context
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
import androidx.compose.material3.Surface
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
import com.github.biltudas1.sequence.fcm.MyFirebaseMessagingService
import com.github.biltudas1.sequence.ui.*
import com.github.biltudas1.sequence.ui.contacts.ContactsScreen
import com.github.biltudas1.sequence.ui.theme.SequenceTheme
import com.github.biltudas1.sequence.ui.utils.CallStatusManager
import com.github.biltudas1.sequence.worker.UpdateWorker
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    private val incomingRoomId = mutableStateOf<String?>(null)
    private val incomingCallerName = mutableStateOf("")
    private val incomingCallerEmail = mutableStateOf("")
    private var lastHandledRoomId: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val roomId = intent.getStringExtra("roomId")
        Log.i("MainActivity", "onNewIntent: roomId=$roomId")
        if (roomId != null) {
            incomingRoomId.value = roomId
            incomingCallerName.value = intent.getStringExtra("callerName") ?: ""
            incomingCallerEmail.value = intent.getStringExtra("callerEmail") ?: ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        intent.getStringExtra("roomId")?.let {
            Log.i("MainActivity", "onCreate intent: roomId=$it")
            incomingRoomId.value = it
            incomingCallerName.value = intent.getStringExtra("callerName") ?: ""
            incomingCallerEmail.value = intent.getStringExtra("callerEmail") ?: ""
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

                val updateInterval by dataStoreManager.updateIntervalFlow.collectAsStateWithLifecycle(initialValue = "Daily")
                LaunchedEffect(updateInterval) {
                    UpdateWorker.schedule(context, updateInterval)
                }

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

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val scope = rememberCoroutineScope()

                    // Dynamically show over lock screen only for calls
                    LaunchedEffect(currentRoute) {
                        val isCallScreen = currentRoute?.contains("webrtc_call") == true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(isCallScreen)
                            setTurnScreenOn(isCallScreen)
                        } else {
                            @Suppress("DEPRECATION")
                            if (isCallScreen) {
                                window.addFlags(
                                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                                            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                                )
                            } else {
                                window.clearFlags(
                                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                                            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                                )
                            }
                        }
                    }

                    val accessToken by dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = "UNDEFINED")
                    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = null)

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

                    fun navigateToCallWithPermission(rId: String, url: String, name: String = "", email: String = "", isExternal: Boolean = false) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                            val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
                            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                            Log.i("MainActivity", "Navigating to webrtc_call. Room: $rId, External: $isExternal")
                            navController.navigate("webrtc_call/$rId?serverUrl=$encodedUrl&name=$encodedName&email=$encodedEmail&isExternal=$isExternal")
                        } else {
                            pendingNavigationUrl = rId to url
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    val roomId by incomingRoomId
                    
                    LaunchedEffect(roomId, accessToken, serverConfig, currentRoute) {
                        val rId = roomId
                        if (rId != null && accessToken != null && accessToken != "UNDEFINED" && serverConfig != null) {
                            Log.i("MainActivity", "Incoming call effect check. Room: $rId, CurrentRoute: $currentRoute")
                            
                            // If coming from the Accept notification button, stop busy loop
                            if (intent.getStringExtra("action") == MyFirebaseMessagingService.ACTION_ACCEPT) {
                                MyFirebaseMessagingService.markRoomAccepted(rId)
                                // Dismiss notification manually since we bypassed the receiver
                                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                nm.cancel(MyFirebaseMessagingService.CALL_NOTIFICATION_ID)
                            }

                            if (rId == lastHandledRoomId) {
                                Log.i("MainActivity", "Skipping duplicate incoming call navigation for Room: $rId")
                                incomingRoomId.value = null
                                return@LaunchedEffect
                            }

                            val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                            val baseUrl = serverConfig!!.cleanEndpoint
                            val fullUrl = "$protocol://$baseUrl/room/$rId"
                            
                            Log.i("MainActivity", "Incoming call effect triggered for Room: $rId")
                            if (currentRoute?.contains("webrtc_call") != true) {
                                lastHandledRoomId = rId
                                val name = incomingCallerName.value
                                val email = incomingCallerEmail.value
                                // Use a local copy to navigate and clear the state immediately
                                incomingRoomId.value = null
                                navigateToCallWithPermission(rId, fullUrl, name, email, isExternal = true)
                            } else {
                                Log.i("MainActivity", "Already in a call, clearing incomingRoomId")
                                incomingRoomId.value = null
                            }
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
                                    navController.navigate("contacts") {
                                        popUpTo("login") { inclusive = true }
                                        launchSingleTop = true
                                    }
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
                                        scope.launch {
                                            if (accessToken != null && serverConfig != null) {
                                                val result = authService.sendVoiceCall(serverConfig!!, accessToken!!, contact.email)
                                                result.getOrNull()?.data?.let { data ->
                                                    val rId = data.roomId
                                                    val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                                    val fullUrl = "$protocol://${serverConfig!!.cleanEndpoint}/room/$rId"
                                                    val fullName = "${contact.first_name ?: ""} ${contact.last_name ?: ""}".trim().ifEmpty { contact.email }
                                                    navigateToCallWithPermission(rId, fullUrl, fullName, contact.email, isExternal = false)
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
                                    onJoinRoom = { rId, url -> navigateToCallWithPermission(rId, url, "Room Call", "") }
                                )
                            }
                            composable("webrtc_call/{roomId}?serverUrl={serverUrl}&name={name}&email={email}&isExternal={isExternal}") { backStackEntry ->
                                val rId = backStackEntry.arguments?.getString("roomId") ?: ""
                                val rawUrl = backStackEntry.arguments?.getString("serverUrl") ?: ""
                                val rawName = backStackEntry.arguments?.getString("name") ?: ""
                                val rawEmail = backStackEntry.arguments?.getString("email") ?: ""
                                val isExternal = backStackEntry.arguments?.getString("isExternal")?.toBoolean() ?: false

                                val sUrl = try { java.net.URLDecoder.decode(rawUrl, "UTF-8") } catch (e: Exception) { rawUrl }
                                val decodedName = try { java.net.URLDecoder.decode(rawName, "UTF-8") } catch (e: Exception) { rawName }
                                val decodedEmail = try { java.net.URLDecoder.decode(rawEmail, "UTF-8") } catch (e: Exception) { rawEmail }

                                Log.i("MainActivity", "Entering WebRTCScreen. Room: $rId, External: $isExternal")

                                WebRTCScreen(
                                    roomId = rId,
                                    serverUrl = sUrl,
                                    callerName = decodedName,
                                    callerEmail = decodedEmail,
                                    isExternal = isExternal,
                                    accessToken = accessToken,
                                    onCallStopped = {
                                        Log.i("MainActivity", "onCallStopped triggered. isExternal: $isExternal")
                                        if (isExternal) {
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
