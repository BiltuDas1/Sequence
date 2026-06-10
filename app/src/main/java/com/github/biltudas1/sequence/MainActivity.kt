package com.github.biltudas1.sequence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.github.biltudas1.sequence.ui.LoginScreen
import com.github.biltudas1.sequence.ui.theme.SequenceTheme
import com.github.biltudas1.sequence.ui.theme.SurfaceDim

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
                    containerColor = SurfaceDim
                ) { innerPadding ->
                    LoginScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
