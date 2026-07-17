package com.github.biltudas1.sequence.ui.auth

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.auth.GoogleAuthManager
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.components.ServerConfigDialog
import com.github.biltudas1.sequence.util.NetworkStatus
import com.github.biltudas1.sequence.util.ToastUtils
import com.github.biltudas1.sequence.worker.FcmTokenWorker
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import com.github.biltudas1.sequence.util.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onEmailLoginClick: () -> Unit,
    isServerIncompatible: Boolean,
    networkStatus: NetworkStatus,
    modifier: Modifier = Modifier,
    showConfigInitially: Boolean = false
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val googleAuthManager = remember { GoogleAuthManager(context) }
    val okHttpClient = remember { OkHttpClient() }
    val authService = remember { AuthService(okHttpClient, dataStoreManager) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(showConfigInitially) }
    val serverIncompatibleText = stringResource(R.string.server_incompatible)
    val noInternetText = stringResource(R.string.no_internet)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Server Configuration"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .size(100.dp)
            )

            //  Name
            Text(
                text = "Sequence",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Subtitle
            Text(
                text = "Connect instantly, securely.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
            )

            if (isServerIncompatible) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth()
                ) {
                    Text(
                        text = serverIncompatibleText,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Google Sign-In Button
            Button(
                onClick = {
                    if (isLoading) return@Button
                    if (networkStatus == NetworkStatus.Unavailable) {
                        ToastUtils.show(context, noInternetText, Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (isServerIncompatible) {
                        ToastUtils.show(context, serverIncompatibleText, Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (serverConfig.isValid()) {
                        isLoading = true
                        Timber.i("Starting Google Sign-In process")
                        scope.launch {
                            val credential = googleAuthManager.signIn()
                            if (credential != null) {
                                Timber.i("Google Sign-In successful. Email: ${AppLogger.redactEmail(credential.id)}")
                                
                                Timber.d("Attempting login to server: ${serverConfig.cleanEndpoint}")
                                var loginResult = authService.loginUser(serverConfig, credential.idToken)
                                
                                if (loginResult.isFailure && loginResult.exceptionOrNull()?.message?.contains("User doesn't exist", ignoreCase = true) == true) {
                                    Timber.i("User doesn't exist. Attempting registration.")
                                    
                                    val regResult = authService.registerUser(serverConfig, credential.idToken)
                                    if (regResult.isSuccess) {
                                        Timber.i("Registration successful. Retrying login.")
                                        loginResult = authService.loginUser(serverConfig, credential.idToken)
                                    } else {
                                        val error = regResult.exceptionOrNull()?.message ?: "Registration failed"
                                        Timber.e("Registration failed: $error")
                                        ToastUtils.show(context, error, Toast.LENGTH_LONG)
                                        googleAuthManager.signOut()
                                        isLoading = false
                                        return@launch
                                    }
                                }

                                if (loginResult.isSuccess) {
                                    val loginData = loginResult.getOrNull()?.data
                                    if (loginData != null) {
                                        Timber.i("Login successful. Saving session.")
                                        dataStoreManager.saveTokens(
                                            accessToken = loginData.jwt.access_token,
                                            refreshToken = loginData.jwt.refresh_token
                                        )
                                        dataStoreManager.saveUserEmail(loginData.email)
                                        dataStoreManager.savePrivacyMode(loginData.privacy_mode)

                                        // Ensure FCM token is synced immediately after login
                                        FcmTokenWorker.enqueue(context)

                                        ToastUtils.show(context, "Welcome back, ${loginData.firstname ?: credential.displayName}", Toast.LENGTH_SHORT)
                                        
                                        // On login success, we might need to go to permissions if some are missing,
                                        // or directly to contacts. The callback in MainActivity will handle this logic.
                                        onLoginSuccess()
                                    }
                                }
else {
                                    val error = loginResult.exceptionOrNull()?.message ?: "Login failed"
                                    Timber.e("Login failed: $error")
                                    ToastUtils.show(context, error, Toast.LENGTH_LONG)
                                    googleAuthManager.signOut()
                                }
                            } else {
                                Timber.w("Google Sign-In returned null credential")
                                ToastUtils.show(context, "Google Sign-In failed", Toast.LENGTH_SHORT)
                            }
                            isLoading = false
                        }
                    } else {
                        Timber.w("Login attempt with invalid server config")
                        ToastUtils.show(
                            context,
                            "Please configure server settings first",
                            Toast.LENGTH_SHORT
                        )
                    }
                },
                enabled = networkStatus != NetworkStatus.Unavailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !isLoading,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = "Sign in with Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Email Sign-In Button
            OutlinedButton(
                onClick = {
                    if (serverConfig.isValid()) {
                        onEmailLoginClick()
                    } else {
                        ToastUtils.show(
                            context,
                            "Please configure server settings first",
                            Toast.LENGTH_SHORT
                        )
                    }
                },
                enabled = networkStatus != NetworkStatus.Unavailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Icon",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Login using Email",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showConfigDialog) {
        ServerConfigDialog(
            config = serverConfig,
            authService = authService,
            onDismiss = { showConfigDialog = false },
            onSave = {
                scope.launch {
                    dataStoreManager.saveServerConfig(it)
                }
                showConfigDialog = false
            },
            autoTest = showConfigInitially
        )
    }
}
