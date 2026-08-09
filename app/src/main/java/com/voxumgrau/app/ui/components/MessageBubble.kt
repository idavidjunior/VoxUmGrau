package com.voxumgrau.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxumgrau.app.ui.theme.*

// ============================================================
// MESSAGE BUBBLE — Bolhas glassmorphism
// ============================================================

@Composable
fun JarvisMessageBubble(
    text: String,
    isUser: Boolean,
    timestamp: String = "",
    isAudio: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(
        topStart = if (isUser) 16.dp else 4.dp,
        topEnd = if (isUser) 4.dp else 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp
    )

    val backgroundColor = if (isUser) {
        Brush.horizontalGradient(
            colors = listOf(
                JarvisSurfaceVariant,
                JarvisSurfaceGlass
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                JarvisCyanSubtle,
                JarvisSurfaceGlass
            )
        )
    }

    val borderColor = if (isUser) JarvisBlueGlow else JarvisBorder

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 12.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // Bubble body
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(backgroundColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) JarvisTextPrimary else JarvisTextPrimary
                    )

                    if (isAudio) {
                        Spacer(modifier = Modifier.height(4.dp))
                        AudioWaveformIndicator(isUser = isUser)
                    }
                }
            }

            // Timestamp
            if (timestamp.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextMuted,
                    modifier = Modifier
                        .alpha(0.7f)
                        .align(if (isUser) Alignment.End else Alignment.Start)
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AudioWaveformIndicator(isUser: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")

    val heights = List(4) { index ->
        infiniteTransition.animateFloat(
            initialValue = 4f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + index * 80,
                    easing = EaseInOutCubic
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "audio_bar_$index"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isUser) JarvisBlueDim else JarvisCyanDim
                    )
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "♪",
            fontSize = 10.sp,
            color = if (isUser) JarvisBlueDim else JarvisCyanDim
        )
    }
}
