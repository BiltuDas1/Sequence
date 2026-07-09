package com.github.biltudas1.sequence.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.data.remote.model.EmailLoginRequest
import com.github.biltudas1.sequence.data.remote.model.EmailRegistrationRequest
import com.github.biltudas1.sequence.util.NetworkStatus
import com.github.biltudas1.sequence.util.ToastUtils
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailLoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    onBackClick: () -> Unit,
    isServerIncompatible: Boolean,
    networkStatus: NetworkStatus
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email Login") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 8.dp)
            )

            Text(
                text = "Sequence",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Login to your account",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isLoading) return@Button
                    if (email.isBlank() || password.isBlank()) {
                        ToastUtils.show(context, "Please fill all fields", android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (networkStatus == NetworkStatus.Unavailable) {
                        ToastUtils.show(context, context.getString(R.string.no_internet), android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (isServerIncompatible) {
                        ToastUtils.show(context, context.getString(R.string.server_incompatible), android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (!serverConfig.isValid()) {
                        ToastUtils.show(context, "Please configure server settings first", android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }

                    isLoading = true
                    scope.launch {
                        val result = authService.loginUserEmail(serverConfig, EmailLoginRequest(email, password))
                        if (result.isSuccess) {
                            val loginData = result.getOrNull()?.data
                            if (loginData != null) {
                                dataStoreManager.saveTokens(
                                    accessToken = loginData.jwt.access_token,
                                    refreshToken = loginData.jwt.refresh_token
                                )
                                dataStoreManager.saveUserEmail(loginData.email)
                                dataStoreManager.savePrivacyMode(loginData.privacy_mode)
                                onLoginSuccess()
                            }
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Login failed"
                            ToastUtils.show(context, error, android.widget.Toast.LENGTH_LONG)
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = email.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text("Login", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "New User? ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Register Now",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onRegisterClick() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailRegisterScreen(
    onRegistrationSuccess: () -> Unit,
    onBackClick: () -> Unit,
    isServerIncompatible: Boolean,
    networkStatus: NetworkStatus
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    val scope = rememberCoroutineScope()

    var firstname by remember { mutableStateOf("") }
    var lastname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = firstname,
                onValueChange = { firstname = it },
                label = { Text("First Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = lastname,
                onValueChange = { lastname = it },
                label = { Text("Last Name (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isLoading) return@Button
                    if (firstname.isBlank() || email.isBlank() || password.isBlank()) {
                        ToastUtils.show(context, "Please fill required fields", android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (password != confirmPassword) {
                        ToastUtils.show(context, "Passwords do not match", android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (networkStatus == NetworkStatus.Unavailable) {
                        ToastUtils.show(context, context.getString(R.string.no_internet), android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (isServerIncompatible) {
                        ToastUtils.show(context, context.getString(R.string.server_incompatible), android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }
                    if (!serverConfig.isValid()) {
                        ToastUtils.show(context, "Please configure server settings first", android.widget.Toast.LENGTH_SHORT)
                        return@Button
                    }

                    isLoading = true
                    scope.launch {
                        val request = EmailRegistrationRequest(
                            firstname = firstname,
                            lastname = lastname.ifBlank { null },
                            email = email,
                            password = password
                        )
                        val result = authService.registerUserEmail(serverConfig, request)
                        if (result.isSuccess) {
                            onRegistrationSuccess()
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Registration failed"
                            ToastUtils.show(context, error, android.widget.Toast.LENGTH_LONG)
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = firstname.isNotBlank() && email.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text("Register", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RegistrationSuccessScreen(
    onContinueClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = Color(0xFF4CAF50)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Registration successful",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onContinueClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Continue to Login", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
