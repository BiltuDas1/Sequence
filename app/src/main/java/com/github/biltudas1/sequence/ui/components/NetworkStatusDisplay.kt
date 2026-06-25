package com.github.biltudas1.sequence.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.util.NetworkStatus

@Composable
fun NetworkStatusDisplay(status: NetworkStatus) {
    AnimatedVisibility(
        visible = status != NetworkStatus.Available,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        val (backgroundColor, textRes) = when (status) {
            NetworkStatus.Unavailable -> Color(0xFFD32F2F) to R.string.no_internet // Red
            NetworkStatus.Unstable -> Color(0xFFFBC02D) to R.string.unstable_internet // Amber
            else -> Color.Transparent to -1
        }

        if (textRes != -1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .statusBarsPadding()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(textRes),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
