package com.github.biltudas1.sequence.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.github.biltudas1.sequence.ui.theme.OutlineGhost
import com.github.biltudas1.sequence.ui.theme.SurfaceContainerHigh
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val googleAuthManager = remember { GoogleAuthManager(context) }
    val okHttpClient = remember { OkHttpClient() }
    val authService = remember { AuthService(okHttpClient, dataStoreManager) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Server Configuration",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
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
                color = Color.White
            )

            // Subtitle
            Text(
                text = "Connect instantly, securely.",
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
            )

            // Google Sign-In Button
            Button(
                onClick = {
                    if (serverConfig.isValid()) {
                        isLoading = true
                        scope.launch {
                            val credential = googleAuthManager.signIn()
                            if (credential != null) {
                                // 1. Try Login
                                var loginResult = authService.loginUser(serverConfig, credential.idToken)
                                
                                if (loginResult.isFailure && loginResult.exceptionOrNull()?.message?.contains("User doesn't exist", ignoreCase = true) == true) {
                                    // 2. User doesn't exist, try Register
                                    val regResult = authService.registerUser(serverConfig, credential.idToken)
                                    if (regResult.isSuccess) {
                                        // 3. Register success, now Login to get JWT
                                        loginResult = authService.loginUser(serverConfig, credential.idToken)
                                    } else {
                                        val error = regResult.exceptionOrNull()?.message ?: "Registration failed"
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        googleAuthManager.signOut()
                                        isLoading = false
                                        return@launch
                                    }
                                }

                                if (loginResult.isSuccess) {
                                    val loginData = loginResult.getOrNull()?.data
                                    if (loginData != null) {
                                        dataStoreManager.saveTokens(
                                            accessToken = loginData.jwt.access_token,
                                            refreshToken = loginData.jwt.refresh_token
                                        )
                                        Toast.makeText(context, "Welcome back, ${loginData.firstname ?: credential.displayName}", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    }
                                } else {
                                    val error = loginResult.exceptionOrNull()?.message ?: "Login failed"
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    googleAuthManager.signOut()
                                }
                            } else {
                                Toast.makeText(context, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
                            }
                            isLoading = false
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Please configure server settings first",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceContainerHigh,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, OutlineGhost)
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
                                color = Color.White,
                                strokeWidth = 3.dp
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
        }
    }

    if (showConfigDialog) {
        ServerConfigDialog(
            config = serverConfig,
            onDismiss = { showConfigDialog = false },
            onSave = {
                scope.launch {
                    dataStoreManager.saveServerConfig(it)
                }
                showConfigDialog = false
            }
        )
    }
}
