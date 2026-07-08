package com.github.biltudas1.sequence

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import timber.log.Timber
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.github.biltudas1.sequence.auth.GoogleAuthManager
import com.github.biltudas1.sequence.data.model.AppTheme
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.fcm.MyFirebaseMessagingService
import com.github.biltudas1.sequence.ui.*
import com.github.biltudas1.sequence.ui.components.NetworkStatusDisplay
import com.github.biltudas1.sequence.ui.contacts.ContactsScreen
import com.github.biltudas1.sequence.util.ConnectivityObserver
import com.github.biltudas1.sequence.util.NetworkStatus
import com.github.biltudas1.sequence.ui.theme.SequenceTheme
import com.github.biltudas1.sequence.ui.utils.CallRingtonePlayer
import com.github.biltudas1.sequence.ui.utils.CallStatusManager
import com.github.biltudas1.sequence.ui.utils.PermissionUtils
import com.github.biltudas1.sequence.util.AppConstants
import com.github.biltudas1.sequence.util.AppLogger
import com.github.biltudas1.sequence.util.ToastUtils
import com.github.biltudas1.sequence.util.VersionUtils
import com.github.biltudas1.sequence.util.UpdateDownloadManager
import com.google.firebase.messaging.FirebaseMessaging
import com.github.biltudas1.sequence.worker.UpdateWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import java.io.File

class MainActivity : ComponentActivity() {

    private val incomingRoomId = mutableStateOf<String?>(null)
    private val incomingCallerName = mutableStateOf("")
    private val incomingCallerEmail = mutableStateOf("")
    private val incomingServerUrl = mutableStateOf<String?>(null)
    private val incomingIsExternal = mutableStateOf(false)
    private val incomingCreationTime = mutableStateOf<Long?>(null)
    private val targetPage = mutableStateOf<String?>(null)
    private var lastHandledRoomId: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Timber.d("onNewIntent: intent=$intent")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val roomId = intent.getStringExtra("roomId")
        val target = intent.getStringExtra("targetPage")
        val action = intent.getStringExtra("action")
        Timber.i("handleIntent: roomId=$roomId, target=$target, action=$action")
        if (roomId != null) {
            incomingRoomId.value = roomId
            incomingCallerName.value = intent.getStringExtra("callerName") ?: ""
            incomingCallerEmail.value = intent.getStringExtra("callerEmail") ?: ""
            incomingServerUrl.value = intent.getStringExtra("serverUrl")
            incomingIsExternal.value = intent.getStringExtra("isExternal")?.toBoolean() ?: false
            val creationTime = if (intent.hasExtra("creationTime")) intent.getLongExtra("creationTime", -1) else null
            incomingCreationTime.value = if (creationTime == -1L) null else creationTime
        }
        targetPage.value = target
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("onCreate MainActivity")
        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val dataStoreManager = remember { DataStoreManager.getInstance(context) }
            val appTheme by dataStoreManager.appThemeFlow.collectAsStateWithLifecycle(initialValue = AppTheme.SYSTEM)

            SequenceTheme(appTheme = appTheme) {
                val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
                val contactRepository = remember { ContactRepository(context, authService) }

                val updateDownloadManager = remember { UpdateDownloadManager(context) }
                val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
                val currentVersion = packageInfo.versionName ?: ""

                val updateInterval by dataStoreManager.updateIntervalFlow.collectAsStateWithLifecycle(initialValue = "Daily")
                val accessToken by dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = "UNDEFINED")
                val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = null)
                val ownEmail by dataStoreManager.userEmailFlow.collectAsStateWithLifecycle(initialValue = null)

                LaunchedEffect(accessToken) {
                    if (accessToken != null && accessToken != "UNDEFINED") {
                        val downloadInfo = dataStoreManager.downloadInfoFlow.first()
                        if (downloadInfo.status == "COMPLETED") {
                            val fileExists = downloadInfo.filePath?.let { File(it).exists() } ?: false
                            val isSameVersion = downloadInfo.versionTag?.removePrefix("v") == currentVersion.removePrefix("v")

                            if (isSameVersion || !fileExists) {
                                if (isSameVersion) {
                                    downloadInfo.filePath?.let { path -> File(path).delete() }
                                }
                                dataStoreManager.clearDownloadData()
                            }
                        }
                        val latestInfo = dataStoreManager.downloadInfoFlow.first()
                        if (latestInfo.status == "DOWNLOADING") {
                            updateDownloadManager.resumeDownload(latestInfo)
                        }
                    }
                }
                LaunchedEffect(updateInterval, accessToken) {
                    if (accessToken != null && accessToken != "UNDEFINED") {
                        UpdateWorker.schedule(context, updateInterval)
                        if (updateInterval != "Never") {
                            UpdateWorker.checkNow(context)
                        }
                    }
                }

                val connectivityObserver = remember { ConnectivityObserver(context) }
                val networkStatus by connectivityObserver.observe().collectAsStateWithLifecycle(initialValue = NetworkStatus.Available)

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        NetworkStatusDisplay(status = networkStatus)
                        
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        val scope = rememberCoroutineScope()
                        var isCalling by remember { mutableStateOf(false) }

                        val sessionExpiredText = stringResource(R.string.session_expired)
                        LaunchedEffect(Unit) {
                            dataStoreManager.sessionExpiredEvent.collect {
                                ToastUtils.show(context, sessionExpiredText, Toast.LENGTH_LONG)
                                navController.navigate(AppConstants.Routes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }

                        LaunchedEffect(currentRoute) {
                            val isCallScreen = currentRoute?.contains("webrtc_call") == true
                            if (!isCallScreen) {
                                lastHandledRoomId = null
                                isCalling = false
                            }

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

                        LaunchedEffect(accessToken, serverConfig) {
                            val token = accessToken
                            val config = serverConfig
                            if (token != null && token != "UNDEFINED" && config != null && config.isValid()) {
                                try {
                                    @Suppress("DEPRECATION")
                                    val fcmToken = FirebaseMessaging.getInstance().token.await()
                                    val currentSavedToken = dataStoreManager.fcmTokenFlow.firstOrNull()
                                    if (fcmToken != currentSavedToken) {
                                        val result = authService.updateFcmToken(config, token, fcmToken)
                                        if (result.isSuccess) {
                                            dataStoreManager.saveFcmToken(fcmToken)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to check/refresh FCM token")
                                }
                            }
                        }

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
                                            ToastUtils.show(context, context.getString(R.string.client_outdated, serverVersion), Toast.LENGTH_LONG)
                                            isServerIncompatible = true
                                        } else if (serverMajor < AppConstants.COMPATIBLE_SERVER_MAJOR_VERSION) {
                                            ToastUtils.show(context, context.getString(R.string.server_outdated, serverVersion), Toast.LENGTH_LONG)
                                            isServerIncompatible = true
                                        } else {
                                            isServerIncompatible = false
                                        }
                                    } else {
                                        isServerIncompatible = true
                                    }
                                }
                            }
                        }

                        var pendingNavigationRoute by remember { mutableStateOf<String?>(null) }
                        val micPermissionRequiredText = stringResource(R.string.mic_permission_required)
                        val audioPermissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (isGranted && pendingNavigationRoute != null) {
                                navController.navigate(pendingNavigationRoute!!)
                                pendingNavigationRoute = null
                            } else {
                                isCalling = false
                                ToastUtils.show(context, micPermissionRequiredText, Toast.LENGTH_LONG)
                            }
                        }

                        fun navigateToCallWithPermission(rId: String, url: String, name: String = "", email: String = "", isExternal: Boolean = false, creationTime: Long? = null, isOutgoing: Boolean = false) {
                            val encodedName = try { java.net.URLEncoder.encode(name, "UTF-8") } catch (e: Exception) { name }
                            val encodedEmail = try { java.net.URLEncoder.encode(email, "UTF-8") } catch (e: Exception) { email }
                            val encodedUrl = try { java.net.URLEncoder.encode(url, "UTF-8") } catch (e: Exception) { url }
                            val cTime = creationTime ?: -1L
                            val route = "webrtc_call/$rId?serverUrl=$encodedUrl&name=$encodedName&email=$encodedEmail&isExternal=$isExternal&creationTime=$cTime&isOutgoing=$isOutgoing"

                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                navController.navigate(route)
                            } else {
                                pendingNavigationRoute = route
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }

                        val roomId by incomingRoomId
                        val destinationPage by targetPage
                        
                        LaunchedEffect(destinationPage, accessToken, currentRoute) {
                            if (destinationPage != null && accessToken != null && accessToken != "UNDEFINED") {
                                val target = destinationPage
                                targetPage.value = null
                                if (target == "about" && currentRoute != AppConstants.Routes.ABOUT) {
                                    navController.navigate(AppConstants.Routes.ABOUT)
                                } else if (target == "recents") {
                                    scope.launch {
                                        dataStoreManager.saveLastSelectedTab(1)
                                    }
                                }
                            }
                        }

                        LaunchedEffect(roomId, accessToken, serverConfig, currentRoute) {
                            val rId = roomId
                            if (rId != null && accessToken != null && accessToken != "UNDEFINED" && serverConfig != null) {
                                val callStatusManager = CallStatusManager(context)
                                val isAlreadyInCall = callStatusManager.isUserOnAnotherCall()
                                val isActiveRoom = com.github.biltudas1.sequence.webrtc.CallManager.activeRoomId == rId

                                if (isAlreadyInCall && !isActiveRoom) {
                                    incomingRoomId.value = null
                                    return@LaunchedEffect
                                }

                                if (intent.getStringExtra("action") == MyFirebaseMessagingService.ACTION_ACCEPT) {
                                    MyFirebaseMessagingService.markRoomAccepted(rId)
                                    CallRingtonePlayer.stop(context)
                                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    nm.cancel(MyFirebaseMessagingService.CALL_NOTIFICATION_ID)
                                }

                                if (rId == lastHandledRoomId && incomingServerUrl.value == null) {
                                    incomingRoomId.value = null
                                    return@LaunchedEffect
                                }

                                if (currentRoute?.contains("webrtc_call") != true) {
                                    val name = incomingCallerName.value
                                    val email = incomingCallerEmail.value
                                    val urlToUse = incomingServerUrl.value ?: run {
                                        val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                        val baseUrl = serverConfig!!.cleanEndpoint
                                        "$protocol://$baseUrl/room/$rId"
                                    }
                                    val isExternal = if (incomingServerUrl.value != null) incomingIsExternal.value else false
                                    lastHandledRoomId = rId
                                    val cTime = incomingCreationTime.value
                                    incomingRoomId.value = null
                                    incomingServerUrl.value = null
                                    incomingCreationTime.value = null
                                    navigateToCallWithPermission(rId, urlToUse, name, email, isExternal = isExternal, creationTime = cTime, isOutgoing = false)
                                } else {
                                    incomingRoomId.value = null
                                }
                            }
                        }

                        if (accessToken == "UNDEFINED") {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        } else {
                            val startDest = remember(accessToken) {
                                if (accessToken == null) AppConstants.Routes.LOGIN
                                else if (PermissionUtils.hasAnyPermissionMissing(context)) AppConstants.Routes.PERMISSIONS
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
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .then(
                                        if (networkStatus != NetworkStatus.Available) {
                                            Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                composable(AppConstants.Routes.LOGIN) {
                                    LoginScreen(
                                        isServerIncompatible = isServerIncompatible,
                                        networkStatus = networkStatus,
                                        onLoginSuccess = {
                                            val destination = if (PermissionUtils.hasAnyPermissionMissing(context)) {
                                                AppConstants.Routes.PERMISSIONS
                                            } else {
                                                AppConstants.Routes.CONTACTS
                                            }
                                            navController.navigate(destination) {
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
                                        networkStatus = networkStatus,
                                        onContactClick = { contact ->
                                            if (isCalling) return@MainScreen
                                            if (isServerIncompatible) {
                                                ToastUtils.show(context, serverIncompatibleText, Toast.LENGTH_SHORT)
                                                return@MainScreen
                                            }
                                            if (contact.email == ownEmail) {
                                                ToastUtils.show(context, "You cannot call yourself", Toast.LENGTH_SHORT)
                                                return@MainScreen
                                            }
                                            val callStatusManager = CallStatusManager(context)
                                            if (callStatusManager.isUserOnAnotherCall()) {
                                                ToastUtils.show(context, cannotPlaceCallBusyText, Toast.LENGTH_SHORT)
                                                return@MainScreen
                                            }
                                            isCalling = true
                                            scope.launch {
                                                if (accessToken != null && serverConfig != null) {
                                                    val result = authService.sendVoiceCall(serverConfig!!, accessToken!!, contact.email)
                                                    if (result.isSuccess) {
                                                        result.getOrNull()?.data?.let { data ->
                                                            val rId = data.roomId
                                                            val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                                            val fullUrl = "$protocol://${serverConfig!!.cleanEndpoint}/room/$rId"
                                                            val fullName = "${contact.first_name ?: ""} ${contact.last_name ?: ""}".trim().ifEmpty { contact.email }
                                                            
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
                                                            
                                                            navigateToCallWithPermission(rId, fullUrl, fullName, contact.email, isExternal = false, isOutgoing = true)
                                                        }
                                                    } else {
                                                        isCalling = false
                                                        val exception = result.exceptionOrNull()
                                                        if (exception is com.github.biltudas1.sequence.data.remote.ForbiddenException) {
                                                            ToastUtils.show(context, privacyModeRestrictionText, Toast.LENGTH_LONG)
                                                        } else {
                                                            ToastUtils.show(context, exception?.message ?: "Call failed", Toast.LENGTH_SHORT)
                                                        }
                                                    }
                                                } else {
                                                    isCalling = false
                                                }
                                            }
                                        },
                                        onDialerCallClick = { email ->
                                            if (isCalling) return@MainScreen
                                            if (isServerIncompatible) {
                                                ToastUtils.show(context, serverIncompatibleText, Toast.LENGTH_SHORT)
                                                return@MainScreen
                                            }
                                            if (email == ownEmail) {
                                                ToastUtils.show(context, "You cannot call yourself", Toast.LENGTH_SHORT)
                                                return@MainScreen
                                            }
                                            val callStatusManager = CallStatusManager(context)
                                            if (callStatusManager.isUserOnAnotherCall()) {
                                                ToastUtils.show(context, cannotPlaceCallBusyText, Toast.LENGTH_SHORT)
                                                return@MainScreen
                                            }
                                            isCalling = true
                                            scope.launch {
                                                if (accessToken != null && serverConfig != null) {
                                                    val result = authService.sendVoiceCall(serverConfig!!, accessToken!!, email)
                                                    if (result.isSuccess) {
                                                        result.getOrNull()?.data?.let { data ->
                                                            val rId = data.roomId
                                                            val protocol = if (serverConfig!!.useWss) "wss" else "ws"
                                                            val fullUrl = "$protocol://${serverConfig!!.cleanEndpoint}/room/$rId"
                                                            
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
                                                            
                                                            navigateToCallWithPermission(rId, fullUrl, email, email, isExternal = false, isOutgoing = true)
                                                        }
                                                    } else {
                                                        isCalling = false
                                                        val exception = result.exceptionOrNull()
                                                        if (exception is com.github.biltudas1.sequence.data.remote.ForbiddenException) {
                                                            ToastUtils.show(context, privacyModeRestrictionText, Toast.LENGTH_LONG)
                                                        } else {
                                                            ToastUtils.show(context, exception?.message ?: "Call failed", Toast.LENGTH_SHORT)
                                                        }
                                                    }
                                                } else {
                                                    isCalling = false
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
                                        networkStatus = networkStatus,
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
                                                val refreshToken = dataStoreManager.refreshTokenFlow.firstOrNull()
                                                val currentToken = dataStoreManager.accessTokenFlow.firstOrNull()
                                                val currentConfig = dataStoreManager.serverConfigFlow.firstOrNull()
                                                
                                                if (currentToken != null && currentConfig != null && currentConfig.isValid()) {
                                                    authService.logoutUser(currentConfig, currentToken, refreshToken)
                                                }
                                                dataStoreManager.clearSession()
                                                contactRepository.clearLocalData()
                                                val googleAuthManager = GoogleAuthManager(context)
                                                googleAuthManager.signOut()
                                            }
                                        }
                                    )
                                }
                                composable(AppConstants.Routes.CALL_SETTINGS) {
                                    CallSettingsScreen(
                                        networkStatus = networkStatus,
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
                                    AboutScreen(
                                        onBackClick = {
                                            navController.popBackStack()
                                        },
                                        onViewLogsClick = {
                                            navController.navigate(AppConstants.Routes.LOGS)
                                        }
                                    )
                                }
                                composable(AppConstants.Routes.LOGS) {
                                    LogViewerScreen(onBackClick = {
                                        navController.popBackStack()
                                    })
                                }
                                composable(AppConstants.Routes.ROOM_ENTRY) {
                                    val cannotPlaceCallBusyText = stringResource(R.string.cannot_place_call_busy)
                                    val roomCallText = stringResource(R.string.room_call)
                                    RoomEntryScreen(
                                        onJoinRoom = { rId, url -> 
                                            if (isCalling) return@RoomEntryScreen
                                            if (isServerIncompatible) {
                                                ToastUtils.show(context, serverIncompatibleText, Toast.LENGTH_SHORT)
                                                return@RoomEntryScreen
                                            }
                                            val callStatusManager = CallStatusManager(context)
                                            if (callStatusManager.isUserOnAnotherCall()) {
                                                ToastUtils.show(context, cannotPlaceCallBusyText, Toast.LENGTH_SHORT)
                                            } else {
                                                isCalling = true
                                                navigateToCallWithPermission(rId, url, roomCallText, "", isOutgoing = true)
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
                                    val isOutgoing = backStackEntry.arguments?.getString("isOutgoing")?.toBoolean() ?: false
                                    val creationTime = backStackEntry.arguments?.getString("creationTime")?.toLongOrNull()
                                    val cTimeFinal = if (creationTime == -1L) null else creationTime

                                    val sUrl = try { java.net.URLDecoder.decode(rawUrl, "UTF-8") } catch (e: Exception) { rawUrl }
                                    val decodedName = try { java.net.URLDecoder.decode(rawName, "UTF-8") } catch (e: Exception) { rawName }
                                    val decodedEmail = try { java.net.URLDecoder.decode(rawEmail, "UTF-8") } catch (e: Exception) { rawEmail }

                                    WebRTCScreen(
                                        roomId = rId,
                                        serverUrl = sUrl,
                                        callerName = decodedName,
                                        callerEmail = decodedEmail,
                                        isExternal = isExternal,
                                        creationTime = cTimeFinal,
                                        accessToken = accessToken,
                                        isOutgoing = isOutgoing,
                                        onCallStopped = {
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
}
