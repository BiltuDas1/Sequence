package com.github.biltudas1.sequence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.biltudas1.sequence.ui.theme.SequenceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Forces dark mode for the preview to match your original XML design
            SequenceTheme(darkTheme = true) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // Matches the @color/surface_dim from your XML
                    containerColor = Color(0xFF121212)
                ) { innerPadding ->
                    DialSomeLoginScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DialSomeLoginScreen(modifier: Modifier = Modifier) {
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
            color = Color(0xFFAAAAAA),
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        // Google Sign-In Button
        Button(
            onClick = { /* UI Only */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp), // Exact height from XML
            shape = RoundedCornerShape(16.dp), // Exact corner radius from XML
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E1E1E), // Matches @color/surface_container_high
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFF333333)) // Matches @color/outline_ghost
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center, // Centers the content like iconGravity="textStart"
                modifier = Modifier.fillMaxWidth()
            ) {
                // NOTE: Copy your ic_google.xml into the drawable folder of this new project
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google Logo",
                    tint = Color.Unspecified, // Prevents Android from painting the colorful G logo white
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Sign in with Google",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}