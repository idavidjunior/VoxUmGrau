package com.voxumgrau.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class Mensagem(val texto: String, val deUsuario: Boolean, val imagemB64: String? = null, val mime: String = "image/jpeg")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VoxChatScreen(viewModel: VoxViewModel = viewModel()) {

    var showSettings by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf(viewModel.hostIp) }
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

    val scope = rememberCoroutineScope()
    var fotoUri by remember { mutableStateOf<Uri?>(null) }

    fun novaFotoUri(): Uri {
        val dir = File(ctx.cacheDir, "fotos").apply { mkdirs() }
        val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }

    fun prepararEEnviar(uri: Uri) {
        scope.launch {
            val b64 = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input) ?: return@withContext null
                        val maior = maxOf(bmp.width, bmp.height)
                        val escala = if (maior > 1280) 1280f / maior else 1f
                        val w = (bmp.width * escala).toInt()
                        val h = (bmp.height * escala).toInt()
                        val scaled = if (escala < 1f) Bitmap.createScaledBitmap(bmp, w, h, true) else bmp
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        if (scaled !== bmp) scaled.recycle()
                        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (b64 != null) {
                viewModel.enviarImagem(b64, "image/jpeg", viewModel.textoInput)
            } else {
                Toast.makeText(ctx, "Não foi possível carregar a imagem", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) fotoUri?.let { prepararEEnviar(it) }
    }

    val galeriaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) prepararEEnviar(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.initVoz()
        viewModel.connect(viewModel.hostIp)
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
                            viewModel.processando -> viewModel.progressoEtapa.ifEmpty { "Processando..." }
                            viewModel.ouvindo -> "Ouvindo..."
                            else -> viewModel.status
                        },
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = { viewModel.toggleEco() }) {
                        Text(
                            if (viewModel.ecoAtivo) "🔊" else "🔇",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 20.sp
                        )
                    }
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
                    Button(onClick = { viewModel.updateHost(host); viewModel.connect(host); showSettings = false }) {
                        Text("Conectar")
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                items(viewModel.mensagens) { msg ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = { copiar(msg.texto) },
                                onLongClick = { copiar(msg.texto) }
                            ),
                        horizontalAlignment = if (msg.deUsuario) Alignment.End else Alignment.Start
                    ) {
                        if (msg.imagemB64 != null) {
                            val bmp = remember(msg.imagemB64) {
                                try {
                                    val bytes = Base64.decode(msg.imagemB64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            bmp?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Foto enviada",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                        if (msg.texto.isNotEmpty()) {
                            Text(
                                text = msg.texto,
                                textAlign = if (msg.deUsuario) TextAlign.End else TextAlign.Start,
                                color = if (msg.deUsuario) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (viewModel.processando) {
                    item {
                        Text(
                            text = if (viewModel.progressoEtapa.isNotEmpty()) "Jarvis ${viewModel.progressoEtapa.lowercase()}..." else "Jarvis está processando...",
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
                        fotoUri = novaFotoUri()
                        cameraLauncher.launch(fotoUri!!)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("📷", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        galeriaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("🖼", fontSize = 20.sp)
                }
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
