package com.voxumgrau.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxumgrau.app.ui.theme.*

private val TerminalBg = Color(0xFF060607)
private val TerminalText = Color(0xFF4AF626)
private val TerminalMuted = Color(0xFF2E7D32)

/**
 * JANELA TERMINAL ">_" — painel colapsável que mostra em tempo real as linhas
 * de trabalho do agente: a bridge traduz os eventos session.next.* do opencode
 * serve em linhas pt-BR e as envia como {"tipo":"trabalho","linha":"..."}.
 * Ícone no topo da tela, expande/colapsa no toque, com auto-scroll para as
 * linhas mais recentes. Regra: só aparece quando há linhas ou conectado.
 */
@Composable
fun JarvisTerminalWindow(
    linhas: List<String>,
    aberto: Boolean,
    logsAtivo: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll: quando novidades chegam e o painel está aberto, desce
    // até a última linha (como um `tail -f`).
    LaunchedEffect(linhas.size, aberto) {
        if (aberto && linhas.isNotEmpty()) {
            listState.scrollToItem((linhas.size - 1).coerceAtLeast(0))
        }
    }

    if (linhas.isEmpty() && !logsAtivo) return

    Column(modifier = modifier.fillMaxWidth()) {
        // Header — botão colapsável
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(JarvisSurface.copy(alpha = 0.92f))
                .clickable { onToggle() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (logsAtivo) TerminalText else JarvisTextMuted)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ">_ terminal",
                color = if (logsAtivo) TerminalText else JarvisTextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (aberto) "▾" else "▸",
                color = JarvisTextSecondary,
                fontSize = 12.sp
            )
        }

        // Corpo — linhas em rolagem (tail -f)
        AnimatedVisibility(
            visible = aberto,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                itemsIndexed(linhas) { _, linha ->
                    Text(
                        text = linha,
                        color = TerminalText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 13.sp,
                        softWrap = false
                    )
                }
            }
        }
    }
}