package com.voxumgrau.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voxumgrau.app.ui.components.*
import com.voxumgrau.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Data class para mensagens
data class Mensagem(
    val texto: String,
    val deUsuario: Boolean,
    val timestamp: String = "",
    val isAudio: Boolean = false,
    val imagem: ByteArray? = null,
    val legenda: String? = null
)

// ============================================================
// JARVIS CHAT SCREEN — Interface Completa Redesign v2
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JarvisChatScreen(viewModel: VoxViewModel = viewModel()) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    var showSettings by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf(viewModel.hostIp) }

    // Estado de edição de mensagem já enviada (padrão edit-and-resubmit)
    // Quando não-nulo, a mensagem naquele índice é exibida em modo de edição.
    var editandoIndice by remember { mutableStateOf<Int?>(null) }
    var editandoTexto by remember { mutableStateOf("") }

    fun cancelarEdicao() {
        editandoIndice = null
        editandoTexto = ""
    }

    fun salvarEdicao() {
        val idx = editandoIndice ?: return
        val original = viewModel.mensagens.getOrNull(idx)?.texto ?: ""
        if (viewModel.editarMensagem(idx, original, editandoTexto)) {
            cancelarEdicao()
        }
    }

    // Voice state para o visualizador
    val voiceState = when {
        !viewModel.conectado -> VoiceState.DISCONNECTED
        viewModel.ouvindo -> VoiceState.LISTENING
        viewModel.processando -> VoiceState.PROCESSING
        viewModel.audioStreaming -> VoiceState.SPEAKING
        else -> VoiceState.IDLE
    }

    // Copiar texto
    fun copiar(texto: String) {
        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("Jarvis", texto))
        Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
    }

    // Format timestamp
    fun now(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    // Permissions
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.permissaoConcedida()
    }

    // Init
    LaunchedEffect(Unit) {
        viewModel.initVoz()
        viewModel.connect(viewModel.hostIp)
    }

    LaunchedEffect(viewModel.conectado) {
        if (viewModel.conectado) {
            val temPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (temPerm) viewModel.microfonePermitido = true
            else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-scroll
    LaunchedEffect(viewModel.mensagens.size) {
        if (viewModel.mensagens.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.mensagens.size - 1)
        }
    }

    // ============================================================
    // UI — Layout Principal
    // ============================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBlack)
    ) {
        // Background gradient sutil
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JarvisBlack,
                            JarvisSurface.copy(alpha = 0.3f),
                            JarvisBlack
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            JarvisTopBar(
                title = "JARVIS",
                connected = viewModel.conectado,
                statusText = when {
                    viewModel.processando -> viewModel.progressoEtapa.ifEmpty { "Processando..." }
                    viewModel.ouvindo -> "Ouvindo..."
                    viewModel.conectado -> "Online"
                    else -> viewModel.status
                },
                ecoAtivo = viewModel.ecoAtivo,
                onEcoToggle = { viewModel.toggleEco() },
                onSettingsClick = { showSettings = !showSettings },
                saude = viewModel.saude
            )

            // Settings panel (collapsible)
            AnimatedVisibility(visible = showSettings) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(JarvisSurfaceGlass)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("IP do PC", color = JarvisTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextSecondary,
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisBorder,
                                cursorColor = JarvisCyan
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.testarConexao() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan)
                        ) {
                            Text("Testar")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                viewModel.updateHost(host)
                                viewModel.connect(host)
                                showSettings = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyan,
                                contentColor = JarvisBlack
                            )
                        ) {
                            Text("Conectar")
                        }
                    }
                }
            }

            // Área central — Chat + Visualizador
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (viewModel.mensagens.isEmpty() && !viewModel.processando) {
                    // Tela inicial — Visualizador centralizado
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        JarvisVoiceVisualizer(
                            state = voiceState,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Text(
                            text = if (viewModel.conectado)
                                "Olá, David. Estou pronto."
                            else
                                "Conectando...",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (viewModel.conectado)
                                "Toque no microfone para começar"
                            else
                                "Aguarde a conexão",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                    }
                } else {
                    // Lista de mensagens
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(viewModel.mensagens) { indice, msg ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { copiar(if (msg.imagem != null) (msg.legenda ?: msg.texto) else msg.texto) },
                                        onLongClick = {
                                            if (msg.deUsuario && editandoIndice == null && !viewModel.processando) {
                                                editandoIndice = indice
                                                editandoTexto = msg.texto
                                            } else {
                                                copiar(if (msg.imagem != null) (msg.legenda ?: msg.texto) else msg.texto)
                                            }
                                        }
                                    )
                            ) {
                                if (editandoIndice == indice) {
                                    // Balão em modo de edição
                                    EditMessageBubble(
                                        texto = editandoTexto,
                                        onTextoChange = { editandoTexto = it },
                                        onConfirm = { salvarEdicao() },
                                        onCancel = { cancelarEdicao() }
                                    )
                                } else if (msg.texto.isNotEmpty() || msg.imagem != null) {
                                    // Bolha de mensagem (texto e/ou imagem)
                                    JarvisMessageBubble(
                                        text = msg.texto,
                                        isUser = msg.deUsuario,
                                        timestamp = msg.timestamp,
                                        isAudio = msg.isAudio && !msg.deUsuario,
                                        imagem = msg.imagem,
                                        legenda = msg.legenda,
                                        acaoEdicao = if (msg.deUsuario && editandoIndice == null && !viewModel.processando) {
                                            {
                                                editandoIndice = indice
                                                editandoTexto = msg.texto
                                            }
                                        } else null
                                    )
                                }
                            }
                        }

                        // Processing indicator
                        if (viewModel.processando) {
                            item {
                                ProcessingIndicator(
                                    text = if (viewModel.progressoEtapa.isNotEmpty())
                                        "Jarvis ${viewModel.progressoEtapa.lowercase()}..."
                                    else
                                        "Jarvis está pensando..."
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(70.dp)) }
                    }

                    // Voice visualizer flutuante — sempre visível (não apenas quando há mensagens)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 80.dp)
                            .size(56.dp)
                    ) {
                        // Sempre mostra o visualizador, mas com intensidade diferente
                        SoundWaves(
                            state = voiceState,
                            modifier = Modifier.size(56.dp),
                            barCount = 3,
                            color = if (voiceState == VoiceState.IDLE) JarvisCyan.copy(alpha = 0.3f) else JarvisCyan
                        )
                    }
                }

                // Visualizador flutuante também na tela inicial
                if (viewModel.mensagens.isEmpty() && !viewModel.processando) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 80.dp)
                            .size(56.dp)
                    ) {
                        SoundWaves(
                            state = voiceState,
                            modifier = Modifier.size(56.dp),
                            barCount = 3,
                            color = if (voiceState == VoiceState.IDLE) JarvisCyan.copy(alpha = 0.3f) else JarvisCyan
                        )
                    }
                }
            }

            // Input bar flutuante
            JarvisInputBar(
                value = viewModel.textoInput,
                onValueChange = { viewModel.textoInput = it },
                onSend = {
                    val t = viewModel.textoInput.trim()
                    if (t.isNotEmpty()) viewModel.send(t)
                },
                onMicClick = {
                    if (viewModel.ouvindo) viewModel.pararOuvir()
                    else {
                        val temPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (temPerm) viewModel.comecarOuvir(interromper = true)
                        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                isListening = viewModel.ouvindo
            )
        }
    }
}

// ============================================================
// INPUT BAR — Com envio por Enter e layout ajustado
// ============================================================

@Composable
fun JarvisInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
            // Text field — com envio por Enter
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
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = JarvisTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(JarvisCyan),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Send button
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(38.dp)
            ) {
                Text(
                    "➤",
                    fontSize = 18.sp,
                    color = if (value.isNotBlank()) JarvisCyan else JarvisTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Mic button (flutuante, centralizado)
        Box(
            modifier = Modifier
                .size(52.dp)
                .scale(micScale)
                .clip(RoundedCornerShape(26.dp))
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
                        .size(70.dp)
                        .alpha(0.4f)
                        .clip(RoundedCornerShape(35.dp))
                        .background(JarvisCyanGlow)
                )
            }

            IconButton(
                onClick = onMicClick,
                interactionSource = micInteractionSource,
                modifier = Modifier.size(52.dp)
            ) {
                Text(
                    if (isListening) "⏹" else "🎤",
                    fontSize = 20.sp,
                    color = if (isListening) JarvisBlack else JarvisCyan
                )
            }
        }
    }
}
