package com.voxumgrau.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    imagem: ByteArray? = null,
    legenda: String? = null,
    onImagemClick: ((ByteArray) -> Unit)? = null,
    acaoEdicao: (() -> Unit)? = null,
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

    val bitmap = remember(imagem) {
        imagem?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

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
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = text.ifEmpty { legenda ?: "Imagem gerada" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 280.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (onImagemClick != null)
                                        Modifier.clickable { onImagemClick(imagem!!) }
                                    else Modifier
                                ),
                            contentScale = ContentScale.Fit
                        )
                        if (text.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) JarvisTextPrimary else JarvisTextPrimary
                        )
                    }

                    if (isAudio) {
                        Spacer(modifier = Modifier.height(4.dp))
                        AudioWaveformIndicator(isUser = isUser)
                    }
                }
            }

            // Ação de edição — apenas nos balões do usuário
            if (acaoEdicao != null && isUser) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✎",
                    fontSize = 13.sp,
                    color = JarvisTextMuted,
                    modifier = Modifier
                        .alpha(0.8f)
                        .align(Alignment.End)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { acaoEdicao() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
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

// ============================================================
// EDIT MESSAGE BUBBLE — Modo de edição de mensagem já enviada
// (padrão edit-and-resubmit das outras IAs: edita no lugar,
// confirma e o restante é regenerado pela bridge)
// ============================================================

@Composable
fun EditMessageBubble(
    texto: String,
    onTextoChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            BasicTextField(
                value = texto,
                onValueChange = onTextoChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = JarvisTextPrimary
                ),
                cursorBrush = SolidColor(JarvisCyan),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(JarvisSurfaceGlass.copy(alpha = 0.7f))
                    .border(width = 1.dp, color = JarvisCyan.copy(alpha = 0.6f), shape = shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                Text(
                    text = "Cancelar",
                    fontSize = 12.sp,
                    color = JarvisTextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCancel() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Text(
                    text = "Salvar edição",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = JarvisCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisCyan.copy(alpha = 0.12f))
                        .clickable { onConfirm() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
