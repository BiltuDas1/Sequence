package com.github.biltudas1.sequence.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.AudioQualityLevel
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualityScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val currentQuality by dataStoreManager.audioQualityFlow.collectAsStateWithLifecycle(initialValue = AudioQualityLevel.STANDARD)
    val scope = rememberCoroutineScope()

    val options = AudioQualityLevel.entries

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Audio Quality", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options) { level ->
                AudioQualityItem(
                    level = level,
                    isSelected = level == currentQuality,
                    onClick = {
                        scope.launch {
                            dataStoreManager.saveAudioQuality(level)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AudioQualityItem(
    level: AudioQualityLevel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null, // Handled by parent Surface
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = Color.White.copy(alpha = 0.6f)
                )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = level.label,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = level.description,
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
