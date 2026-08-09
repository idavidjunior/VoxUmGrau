package com.voxumgrau.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxumgrau.app.ui.theme.*

// ============================================================
// FLOATING INPUT BAR — Barra de input flutuante
// ============================================================

@Composable
fun FloatingInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val micInteractionSource = remember { MutableInteractionSource() }
    val isMicPressed by micInteractionSource.collectIsPressedAsState()

    val micScale by animateFloatAsState(
        targetValue = if (isMicPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mic_scale"
    )

    val micGlowAlpha by animateFloatAsState(
        targetValue = if (isListening) 0.6f else 0f,
        animationSpec = tween(500),
        label = "mic_glow"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(JarvisSurfaceGlass)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera button
            IconButton(
                onClick = onCameraClick,
                modifier = Modifier.size(40.dp)
            ) {
                Text("📷", fontSize = 18.sp)
            }

            // Gallery button
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier.size(40.dp)
            ) {
                Text("🖼", fontSize = 18.sp)
            }

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = if (isListening) "Ouvindo..." else "Digite ou fale...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextMuted,
                        fontWeight = FontWeight.Light
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = JarvisTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(JarvisCyan),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Send button
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(40.dp)
            ) {
                Text(
                    "➤",
                    fontSize = 18.sp,
                    color = if (value.isNotBlank()) JarvisCyan else JarvisTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mic button (floating)
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(micScale)
                .clip(CircleShape)
                .background(
                    if (isListening) {
                        Brush.radialGradient(
                            colors = listOf(JarvisCyan, JarvisCyanDim)
                        )
                    } else {
                        SolidColor(JarvisSurfaceVariant)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                // Glow effect
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .alpha(micGlowAlpha)
                        .clip(CircleShape)
                        .background(JarvisCyanGlow)
                )
            }

            IconButton(
                onClick = onMicClick,
                interactionSource = micInteractionSource,
                modifier = Modifier.size(56.dp)
            ) {
                Text(
                    if (isListening) "⏹" else "🎤",
                    fontSize = 22.sp,
                    color = if (isListening) JarvisBlack else JarvisCyan
                )
            }
        }
    }
}
