package com.voxumgrau.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
// JARVIS TOP BAR — Barra superior glassmorphism
// ============================================================

@Composable
fun JarvisTopBar(
    title: String = "JARVIS",
    connected: Boolean,
    statusText: String,
    ecoAtivo: Boolean,
    onEcoToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        JarvisSurface,
                        JarvisSurface.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left — Title + connection
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = JarvisCyan,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                ConnectionStatusIndicator(
                    connected = connected,
                    text = statusText
                )
            }

            // Right — Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Eco toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (ecoAtivo) JarvisCyanSubtle else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (ecoAtivo) "🔊" else "🔇",
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Settings
                Text(
                    text = "⚙",
                    fontSize = 18.sp,
                    color = JarvisTextSecondary
                )
            }
        }
    }
}
