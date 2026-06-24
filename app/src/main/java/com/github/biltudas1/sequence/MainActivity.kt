package com.github.biltudas1.sequence

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.biltudas1.sequence.data.ContactRepository
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.AppTheme
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.fcm.MyFirebaseMessagingService
import com.github.biltudas1.sequence.ui.*
import com.github.biltudas1.sequence.ui.contacts.ContactsScreen
import com.github.biltudas1.sequence.ui.theme.SequenceTheme
import com.github.biltudas1.sequence.ui.utils.CallStatusManager
import com.github.biltudas1.sequence.ui.utils.PermissionUtils
import com.github.biltudas1.sequence.util.AppConstants
import com.github.biltudas1.sequence.util.VersionUtils
import com.github.biltudas1.sequence.worker.UpdateWorker
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    private val incomingRoomId = mutableStateOf<String?>(null)
    private val incomingCallerName = mutableStateOf("")
    private val incomingCallerEmail = mutableStateOf("")
    private val incomingServerUrl = mutableStateOf<String?>(null)
    private val incomingIsExternal = mutableStateOf(false)
    private val targetPage = mutableStateOf<String?>(null)
    private var lastHandledRoomId: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val roomId = intent.getStringExtra("roomId")
        Log.i("MainActivity", "handleIntent: roomId=$roomId")
        if (roomId != null) {
            incomingRoomId.value = roomId
            incomingCallerName.value = intent.getStringExtra("callerName") ?: ""
            incomingCallerEmail.value = intent.getStringExtra("callerEmail") ?: ""
            incomingServerUrl.value = intent.getStringExtra("serverUrl")
            incomingIsExternal.value = intent.getStringExtra("isExternal")?.toBoolean() ?: false
        }
        targetPage.value = intent.getStringExtra("targetPage")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val dataStoreManager = remember { DataStoreManager.getInstance(context) }
            val appTheme by dataStoreManager.appThemeFlow.collectAsStateWithLifecycle(initialValue = AppTheme.SYSTEM)

            SequenceTheme(appTheme = appTheme) {
                val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
                val contactRepository = remember { ContactRepository(context, authService) }

                val updateInterval by dataStoreManager.updateIntervalFlow.collectAsStateWithLifecycle(initialValue = "Daily")
                LaunchedEffect(updateInterval) {
                    UpdateWorker.schedule(context, updateInterval)
                    if (updateInterval != "Never") {
                        UpdateWorker.checkNow(context)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val sessionExpiredText = stringResource(R.string.session_expired)
                    LaunchedEffect(Unit) {
                        dataStoreManager.sessionExpiredEvent.collect {
                            Toast.makeText(context, sessionExpiredText, Toast.LENGTH_LONG).show()
                            navController.navigate(AppConstants.Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val scope = rememberCoroutineScope()

                    DisposableEffect(Unit) {
                        val callEndListener = {
                            lastHandledRoomId = null
                        }
                        com.github.biltudas1.sequence.webrtc.CallManager.onCallEnded = callEndListener
                        onDispose {
                            com.github.biltudas1.sequence.webrtc.CallManager.onCallEnded = null
                        }
                    }

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
                    val ownEmail by dataStoreManager.userEmailFlow.collectAsStateWithLifecycle(initialValue = null)

                    var isServerIncompatible by remember { mutableStateOf(false) }
                    val serverIncompatibleText = stringResource(R.string.server_incompatible)

                    LaunchedEffect(serverConfig) {
                        val config = serverConfig
                        if (config != null && config.isValid()) {
                            val result = authService.getServerVersion(config)
                            val serverVersion = result.getOrNull()?.data?.version
                            if (serverVersion != null) {
                                val serverMajor = VersionUtils.extractMajorVersion(serverVersion)
                                if (serverMajor != null) {
                                    if (serverMajor > AppConstants.COMPATIBLE_SERVER_MAJOR_VERSION) {
                                        Toast.makeText(context, context.getString(R.string.client_outdated, serverVersion), Toast.LENGTH_LONG).show()
                                        isServerIncompatible = true
                                    } else if (serverMajor < AppConstants.COMPATIBLE_SERVER_MAJOR_VERSION) {
                                        Toast.makeText(context, context.getString(R.string.server_outdated, serverVersion), Toast.LENGTH_LONG).show()
                                        isServerIncompatible = true
                                    } else {
                                        isServerIncompatible = false
                                    }
                                } else {
                                    Log.e("MainActivity", "Failed to parse server version: $serverVersion")
                                    isServerIncompatible = true
                                }
                            } else {
                                Log.e("MainActivity", "Failed to fetch server version")
                                // If we can't even reach the version API, we might want to block 
                                // or allow. Given "stop doing further server operation", blocking is safer.
                                // isServerIncompatible = true
                            }
                        }
                    }

                    // --- Permission Launchers ---
                    
                    var pendingNavigationUrl by remember { mutableStateOf<Pair<String, String>?>(null) }
                    val micPermissionRequiredText = stringResource(R.string.mic_permission_required)
                    val audioPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted && pendingNavigationUrl != null) {
                            val (rId, url) = pendingNavigationUrl!!
                            navController.navigate("webrtc_call/$rId?serverUrl=$url")
                            pendingNavigationUrl = null
                        } else {
                            Toast.makeText(context, micPermissionRequiredText, Toast.LENGTH_LONG).show()
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
                    val destinationPage by targetPage
                    
                    LaunchedEffect(destinationPage, accessToken, currentRoute) {
                        if (destinationPage != null && accessToken != null && accessToken != "UNDEFINED") {
                            Log.i("MainActivity", "Navigation target detected: $destinationPage")
                            if (destinationPage == "about" && currentRoute != AppConstants.Routes.ABOUT) {
                                targetPage.value = null
                                navController.navigate(AppConstants.Routes.ABOUT)
                            }
                        }
                    }

                    LaunchedEffect(roomId, accessToken, serverConfig, currentRoute) {
                        val rId = roomId
                        if (rId != null && accessToken != null && accessToken != "UNDEFINED" && serverConfig != null) {
                            Log.i("MainActivity", "Incoming call effect check. Room: $rId, CurrentRoute: $currentRoute")
                            
                            val callStatusManager = CallStatusManager(context)
                            val isAlreadyInCall = callStatusManager.isUserOnAnotherCall()
                            val isActiveRoom = com.github.biltudas1.sequence.webrtc.CallManager.activeRoomId == rId

                            // If it's a different call and we are already busy, ignore
                            if (isAlreadyInCall && !isActiveRoom) {
                                val currentActive = com.github.biltudas1.sequence.webrtc.CallManager.activeRoomId
                                Log.i("MainActivity", "Ignoring new call while busy. Active: $currentActive, New: $rId")
                                incomingRoomId.value = null
                                return@LaunchedEffect
                            }

                            // If coming from the Accept notification button, stop busy loop
                            if (intent.getStringExtra("action") == MyFirebaseMessagingService.ACTION_ACCEPT) {
                                MyFirebaseMessagingService.markRoomAccepted(rId)
                                // Dismiss notification manually since we bypassed the receiver
                                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                nm.cancel(MyFirebaseMessagingService.CALL_NOTIFICATION_ID)
                            }

                            if (rId == lastHandledRoomId && incomingServerUrl.value == null) {
                                Log.i("MainActivity", "Skipping duplicate incoming call navigation for Room: $rId")
                                incomingRoomId.value = null
                                return@LaunchedEffect
                            }

                            if (currentRoute?.contains("webrtc_call") != true) {
                                val name = incomingCallerName.value
                                val email = incomingCallerEmail.value
                                
                                // If we already have a server URL from the intent (e.g. from CallService notification)
                                // use it, otherwise build it from serverConfig
                                val urlToUse = incomingServerUrl.value ?: run {
                                    val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                    val baseUrl = serverConfig!!.cleanEndpoint
                                    "$protocol://$baseUrl/room/$rId"
                                }
                                val isExternal = if (incomingServerUrl.value != null) incomingIsExternal.value else false
                                
                                Log.i("MainActivity", "Incoming call effect triggered for Room: $rId. URL: $urlToUse, External: $isExternal")
                                
                                lastHandledRoomId = rId
                                // Use a local copy to navigate and clear the state immediately
                                incomingRoomId.value = null
                                incomingServerUrl.value = null
                                navigateToCallWithPermission(rId, urlToUse, name, email, isExternal = isExternal)
                            } else {
                                Log.i("MainActivity", "Already in a call, clearing incomingRoomId")
                                incomingRoomId.value = null
                            }
                        }
                    }

                    if (accessToken == "UNDEFINED") {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    } else {
                        val startDest = remember(accessToken) {
                            if (accessToken == null) AppConstants.Routes.LOGIN
                            else if (!PermissionUtils.hasAllPermissions(context)) AppConstants.Routes.PERMISSIONS
                            else AppConstants.Routes.CONTACTS
                        }

                        NavHost(
                            navController = navController,
                            startDestination = startDest,
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
                            composable(AppConstants.Routes.LOGIN) {
                                LoginScreen(
                                    isServerIncompatible = isServerIncompatible,
                                    onLoginSuccess = {
                                        navController.navigate(AppConstants.Routes.PERMISSIONS) {
                                            popUpTo(AppConstants.Routes.LOGIN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable(AppConstants.Routes.PERMISSIONS) {
                                PermissionGatewayScreen(onAllPermissionsGranted = {
                                    navController.navigate(AppConstants.Routes.CONTACTS) {
                                        popUpTo(AppConstants.Routes.PERMISSIONS) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                })
                            }
                            composable(AppConstants.Routes.CONTACTS) {
                                val cannotPlaceCallBusyText = stringResource(R.string.cannot_place_call_busy)
                                val privacyModeRestrictionText = stringResource(R.string.privacy_mode_restriction)
                                MainScreen(
                                    isServerIncompatible = isServerIncompatible,
                                    onContactClick = { contact ->
                                        if (isServerIncompatible) {
                                            Toast.makeText(context, serverIncompatibleText, Toast.LENGTH_SHORT).show()
                                            return@MainScreen
                                        }
                                        if (contact.email == ownEmail) {
                                            Toast.makeText(context, "You cannot call yourself", Toast.LENGTH_SHORT).show()
                                            return@MainScreen
                                        }
                                        val callStatusManager = CallStatusManager(context)
                                        if (callStatusManager.isUserOnAnotherCall()) {
                                            Toast.makeText(context, cannotPlaceCallBusyText, Toast.LENGTH_SHORT).show()
                                            return@MainScreen
                                        }
                                        scope.launch {
                                            if (accessToken != null && serverConfig != null) {
                                                val result = authService.sendVoiceCall(serverConfig!!, accessToken!!, contact.email)
                                                if (result.isSuccess) {
                                                    result.getOrNull()?.data?.let { data ->
                                                        val rId = data.roomId
                                                        val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                                        val fullUrl = "$protocol://${serverConfig!!.cleanEndpoint}/room/$rId"
                                                        val fullName = "${contact.first_name ?: ""} ${contact.last_name ?: ""}".trim().ifEmpty { contact.email }
                                                        
                                                        // Save Outgoing Call Log
                                                        val repository = com.github.biltudas1.sequence.data.CallLogRepository(context)
                                                        repository.insertCallLog(
                                                            com.github.biltudas1.sequence.data.local.CallLogEntity(
                                                                email = contact.email,
                                                                name = fullName,
                                                                type = "OUTGOING",
                                                                timestamp = System.currentTimeMillis(),
                                                                roomId = rId
                                                            )
                                                        )
                                                        
                                                        navigateToCallWithPermission(rId, fullUrl, fullName, contact.email, isExternal = false)
                                                    }
                                                } else {
                                                    val exception = result.exceptionOrNull()
                                                    if (exception is com.github.biltudas1.sequence.data.remote.ForbiddenException) {
                                                        Toast.makeText(context, privacyModeRestrictionText, Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, exception?.message ?: "Call failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDialerCallClick = { email ->
                                        if (isServerIncompatible) {
                                            Toast.makeText(context, serverIncompatibleText, Toast.LENGTH_SHORT).show()
                                            return@MainScreen
                                        }
                                        if (email == ownEmail) {
                                            Toast.makeText(context, "You cannot call yourself", Toast.LENGTH_SHORT).show()
                                            return@MainScreen
                                        }
                                        val callStatusManager = CallStatusManager(context)
                                        if (callStatusManager.isUserOnAnotherCall()) {
                                            Toast.makeText(context, cannotPlaceCallBusyText, Toast.LENGTH_SHORT).show()
                                            return@MainScreen
                                        }
                                        scope.launch {
                                            if (accessToken != null && serverConfig != null) {
                                                val result = authService.sendVoiceCall(serverConfig!!, accessToken!!, email)
                                                if (result.isSuccess) {
                                                    result.getOrNull()?.data?.let { data ->
                                                        val rId = data.roomId
                                                        val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                                        val fullUrl = "$protocol://${serverConfig!!.cleanEndpoint}/room/$rId"
                                                        
                                                        // Save Outgoing Call Log
                                                        val repository = com.github.biltudas1.sequence.data.CallLogRepository(context)
                                                        repository.insertCallLog(
                                                            com.github.biltudas1.sequence.data.local.CallLogEntity(
                                                                email = email,
                                                                name = null,
                                                                type = "OUTGOING",
                                                                timestamp = System.currentTimeMillis(),
                                                                roomId = rId
                                                            )
                                                        )
                                                        
                                                        navigateToCallWithPermission(rId, fullUrl, email, email, isExternal = false)
                                                    }
                                                } else {
                                                    val exception = result.exceptionOrNull()
                                                    if (exception is com.github.biltudas1.sequence.data.remote.ForbiddenException) {
                                                        Toast.makeText(context, privacyModeRestrictionText, Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, exception?.message ?: "Call failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onSettingsClick = {
                                        navController.navigate(AppConstants.Routes.SETTINGS)
                                    }
                                )
                            }
                            composable(AppConstants.Routes.SETTINGS) {
                                SettingsScreen(
                                    isServerIncompatible = isServerIncompatible,
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onAboutClick = {
                                        navController.navigate(AppConstants.Routes.ABOUT)
                                    },
                                    onCallSettingsClick = {
                                        navController.navigate(AppConstants.Routes.CALL_SETTINGS)
                                    },
                                    onDataUsageClick = {
                                        navController.navigate(AppConstants.Routes.DATA_USAGE)
                                    },
                                    onLogoutClick = {
                                        scope.launch {
                                            dataStoreManager.clearTokens()
                                            contactRepository.clearLocalData()
                                        }
                                    }
                                )
                            }
                            composable(AppConstants.Routes.CALL_SETTINGS) {
                                CallSettingsScreen(
                                    onBackClick = { navController.popBackStack() },
                                    onWebRTCConfigClick = { navController.navigate(AppConstants.Routes.WEBRTC_CONFIG) },
                                    onAudioQualityClick = { navController.navigate(AppConstants.Routes.AUDIO_QUALITY) }
                                )
                            }
                            composable(AppConstants.Routes.AUDIO_QUALITY) {
                                AudioQualityScreen(onBackClick = {
                                    navController.popBackStack()
                                })
                            }
                            composable(AppConstants.Routes.DATA_USAGE) {
                                DataUsageScreen(onBackClick = {
                                    navController.popBackStack()
                                })
                            }
                            composable(AppConstants.Routes.WEBRTC_CONFIG) {
                                WebRTCConfigScreen(onBackClick = {
                                    navController.popBackStack()
                                })
                            }
                            composable(AppConstants.Routes.ABOUT) {
                                AboutScreen(onBackClick = {
                                    navController.popBackStack()
                                })
                            }
                            composable(AppConstants.Routes.ROOM_ENTRY) {
                                val cannotPlaceCallBusyText = stringResource(R.string.cannot_place_call_busy)
                                val roomCallText = stringResource(R.string.room_call)
                                RoomEntryScreen(
                                    onJoinRoom = { rId, url -> 
                                        if (isServerIncompatible) {
                                            Toast.makeText(context, serverIncompatibleText, Toast.LENGTH_SHORT).show()
                                            return@RoomEntryScreen
                                        }
                                        val callStatusManager = CallStatusManager(context)
                                        if (callStatusManager.isUserOnAnotherCall()) {
                                            Toast.makeText(context, cannotPlaceCallBusyText, Toast.LENGTH_SHORT).show()
                                        } else {
                                            navigateToCallWithPermission(rId, url, roomCallText, "")
                                        }
                                    }
                                )
                            }
                            composable(AppConstants.Routes.WEBRTC_CALL) { backStackEntry ->
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
