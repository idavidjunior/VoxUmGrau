package com.voxumgrau.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxumgrau.app.VoxHealthInfo
import com.voxumgrau.app.ui.theme.*
import kotlinx.coroutines.delay

// ============================================================
// CONNECTION STATUS — Indicador de conexão com saude em tempo real
// ============================================================

@Composable
fun ConnectionStatusIndicator(
    connected: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    saude: VoxHealthInfo? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            agora = System.currentTimeMillis()
        }
    }

    val det = saude?.let { info ->
        val segAtras = if (info.ultimaAtividadeMs > 0)
            ((agora - info.ultimaAtividadeMs) / 1000).toInt().coerceAtLeast(0)
        else null
        buildString {
            if (info.rede.isNotEmpty()) {
                append(info.rede)
                append(" · ")
            }
            if (info.rotaAtiva.isNotEmpty()) {
                append(info.rotaAtiva)
                append(" · ")
            }
            if (connected) append("conectado")
            if (segAtras != null && connected) append(" · atividades ha ${segAtras}s")
            if (info.ultimaAtividadeMs > 0 && info.falhasHeartbeat > 0) append(" · heartbeat ${info.falhasHeartbeat}/3")
            if (info.tentativaReconnect > 0 && !connected) append(" · tentativa ${info.tentativaReconnect}")
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(JarvisSurfaceGlass)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .alpha(if (connected) pulseAlpha else 0.4f)
                    .background(if (connected) JarvisGreen else JarvisRed)
            )

            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (connected) JarvisGreen else JarvisRed,
                fontWeight = FontWeight.Medium
            )
        }

        if (det != null && det.isNotBlank()) {
            Text(
                text = det,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                color = JarvisTextMuted,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )
        }
    }
}

// ============================================================
// PROCESSING INDICATOR — Shimmer de processamento
// ============================================================

@Composable
fun ProcessingIndicator(
    text: String = "Jarvis está pensando...",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Animated dots
        repeat(3) { index ->
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 200)
                ),
                label = "dot_$index"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .alpha(dotAlpha)
                    .background(JarvisCyan)
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = JarvisCyanDim,
            fontWeight = FontWeight.Light
        )
    }
}

// ============================================================
// STATE BADGE — Badge de estado do microfone
// ============================================================

@Composable
fun StateBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
