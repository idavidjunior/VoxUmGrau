package com.voxumgrau.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.voxumgrau.app.ui.theme.JarvisAmber
import com.voxumgrau.app.ui.theme.JarvisCyan
import com.voxumgrau.app.ui.theme.JarvisCyanGlow
import com.voxumgrau.app.ui.theme.JarvisTextMuted
import kotlin.math.PI
import kotlin.math.sin

// ============================================================
// SOUND WAVES — Barras de áudio animadas
// ============================================================

enum class VoiceState {
    IDLE, LISTENING, PROCESSING, SPEAKING, DISCONNECTED
}

@Composable
fun SoundWaves(
    state: VoiceState,
    modifier: Modifier = Modifier,
    color: Color = JarvisCyan,
    barCount: Int = 5
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")

    val animatedValues = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600 + index * 100,
                    easing = EaseInOutCubic
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wave_$index"
        )
    }

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(120.dp)) {
        val barWidth = size.width / (barCount * 2f)
        val maxHeight = size.height * 0.8f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val amplitude = when (state) {
                VoiceState.LISTENING -> animatedValues[i].value
                VoiceState.SPEAKING -> animatedValues[i].value * pulse
                VoiceState.PROCESSING -> 0.4f
                VoiceState.IDLE -> 0.15f + animatedValues[i].value * 0.1f
                VoiceState.DISCONNECTED -> 0.1f
            }

            val barHeight = maxHeight * amplitude
            val x = barWidth * (i * 2 + 0.5f)
            val barColor = when (state) {
                VoiceState.PROCESSING -> JarvisAmber
                VoiceState.DISCONNECTED -> Color.Gray
                else -> color.copy(alpha = 0.6f + amplitude * 0.4f)
            }

            drawRect(
                color = barColor,
                topLeft = Offset(x, centerY - barHeight / 2),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

// ============================================================
// SPINNING RING — Anel giratório para processamento
// ============================================================

@Composable
fun SpinningRing(
    modifier: Modifier = Modifier,
    color: Color = JarvisAmber,
    strokeWidth: Float = 3f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Canvas(modifier = modifier.size(60.dp)) {
        val radius = size.minDimension / 2 - strokeWidth
        drawArc(
            color = color.copy(alpha = 0.3f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(strokeWidth, strokeWidth),
            size = Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(strokeWidth, strokeWidth),
            size = Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}

// ============================================================
// PULSE RING — Pulso expansivo para speaking
// ============================================================

@Composable
fun PulseRing(
    modifier: Modifier = Modifier,
    color: Color = JarvisCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    Canvas(modifier = modifier.size(100.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 * scale1

        drawCircle(
            color = color.copy(alpha = alpha1),
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )

        drawCircle(
            color = color.copy(alpha = alpha1 * 0.5f),
            radius = radius * 0.7f,
            center = center
        )
    }
}
