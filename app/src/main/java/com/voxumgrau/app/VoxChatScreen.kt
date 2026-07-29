package com.voxumgrau.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

data class Mensagem(val texto: String, val deUsuario: Boolean)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VoxChatScreen(viewModel: VoxViewModel = viewModel()) {

    var showSettings by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("100.120.67.64") }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()

    fun copiar(texto: String) {
        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("Jarvis", texto))
        Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.permissaoConcedida()
    }

    LaunchedEffect(Unit) {
        viewModel.initVoz()
        viewModel.connect(host)
    }

    LaunchedEffect(viewModel.conectado) {
        if (viewModel.conectado) {
            val temPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (temPerm) {
                viewModel.microfonePermitido = true
            } else {
                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(viewModel.mensagens.size) {
        if (viewModel.mensagens.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.mensagens.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vox UmGrau") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    Text(
                        text = when {
                            viewModel.processando -> "Processando..."
                            viewModel.ouvindo -> "Ouvindo..."
                            else -> viewModel.status
                        },
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Text("⚙", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (showSettings) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP do PC") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { viewModel.connect(host); showSettings = false }) {
                        Text("Conectar")
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                items(viewModel.mensagens) { msg ->
                    Text(
                        text = msg.texto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = { copiar(msg.texto) },
                                onLongClick = { copiar(msg.texto) }
                            ),
                        textAlign = if (msg.deUsuario) TextAlign.End else TextAlign.Start,
                        color = if (msg.deUsuario) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (viewModel.processando) {
                    item {
                        Text(
                            text = "Jarvis está processando...",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                OutlinedTextField(
                    value = viewModel.textoInput,
                    onValueChange = { viewModel.textoInput = it },
                    placeholder = { Text("Digite ou fale...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (viewModel.ouvindo) {
                            viewModel.pararOuvir()
                        } else {
                            val temPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (temPerm) viewModel.comecarOuvir(interromper = true)
                            else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (viewModel.ouvindo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (viewModel.ouvindo) "⏹" else "🎤", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val t = viewModel.textoInput.trim()
                    if (t.isNotEmpty()) {
                        viewModel.send(t)
                    }
                }) {
                    Text("Enviar")
                }
            }
        }
    }
}
