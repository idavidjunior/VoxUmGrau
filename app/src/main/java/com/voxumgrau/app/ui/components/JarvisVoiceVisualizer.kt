package com.voxumgrau.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxumgrau.app.ui.theme.*

// ============================================================
// JARVIS VOICE VISUALIZER — Visualizador central de voz
// ============================================================

@Composable
fun JarvisVoiceVisualizer(
    state: VoiceState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_pulse")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring"
    )

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow background
        Box(
            modifier = Modifier
                .size(180.dp)
                .alpha(glowAlpha * 0.3f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            JarvisCyanGlow,
                            Color.Transparent
                        )
                    )
                )
        )

        // Outer ring
        Box(
            modifier = Modifier
                .size((160 * ringScale).dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .alpha(0.2f)
        )

        // Inner circle with glass effect
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(JarvisSurfaceGlass),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "J",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraLight,
                color = when (state) {
                    VoiceState.DISCONNECTED -> JarvisTextMuted
                    VoiceState.PROCESSING -> JarvisAmber
                    else -> JarvisCyan
                },
                textAlign = TextAlign.Center
            )
        }

        // State indicator overlay
        when (state) {
            VoiceState.LISTENING -> {
                SoundWaves(
                    state = VoiceState.LISTENING,
                    modifier = Modifier.align(Alignment.Center),
                    color = JarvisCyan
                )
            }
            VoiceState.SPEAKING -> {
                PulseRing(
                    modifier = Modifier.align(Alignment.Center),
                    color = JarvisCyan
                )
                SoundWaves(
                    state = VoiceState.SPEAKING,
                    modifier = Modifier.align(Alignment.Center),
                    color = JarvisCyan
                )
            }
            VoiceState.PROCESSING -> {
                SpinningRing(
                    modifier = Modifier.align(Alignment.Center),
                    color = JarvisAmber
                )
            }
            VoiceState.DISCONNECTED -> {
                // Static dim
            }
            VoiceState.IDLE -> {
                SoftPulse()
            }
        }
    }
}

@Composable
private fun SoftPulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "soft_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "soft"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(JarvisCyanSubtle)
    )
}
