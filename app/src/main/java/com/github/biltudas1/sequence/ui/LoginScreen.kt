package com.github.biltudas1.sequence.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.ui.theme.OutlineGhost
import com.github.biltudas1.sequence.ui.theme.SurfaceContainerHigh
import com.github.biltudas1.sequence.ui.theme.TextSecondary

@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Matches the android:padding="32dp" from your XML root layout
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
            onClick = { isLoading = !isLoading },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp), // Exact height from XML
            shape = RoundedCornerShape(16.dp), // Exact corner radius from XML
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceContainerHigh, // Matches @color/surface_container_high
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, OutlineGhost) // Matches @color/outline_ghost
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize() // Smoothly animate the sliding effect
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = "Google Logo",
                        tint = Color.Unspecified, // Prevents Android from painting the colorful G logo white
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
