package com.voxumgrau.app

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class VoxViewModel(app: Application) : AndroidViewModel(app) {

    var status by mutableStateOf("Desconectado")
    var conectado by mutableStateOf(false)
    var ultimaFala by mutableStateOf("")
    var ouvindo by mutableStateOf(false)
    var microfonePermitido by mutableStateOf(false)
    var mensagens by mutableStateOf(listOf<Mensagem>())
    var textoInput by mutableStateOf("")
    var processando by mutableStateOf(false)
    var progressoEtapa by mutableStateOf("")
        private set
    var ecoAtivo by mutableStateOf(false)
        private set

    var hostIp by mutableStateOf("100.91.141.101")
        private set

    private var tts: VoxTts? = null
    private var stt: VoxStt? = null
    private var ws: VoxWebSocket? = null
    private var audioPlayer: VoxAudioPlayer? = null
    private var saudacaoLocalFalada = false
    var audioStreaming by mutableStateOf(false)
        private set

    private val saudacoesLocais = listOf(
        "Opa, chegou!",
        "Pronto, estou aqui!",
        "Fala aí!",
        "O sistema tá ligado!",
        "Cheguei, pode falar!",
    )

    fun initVoz() {
        // Load persisted host IP (default 127.0.0.1)
        val prefsHost = getApplication<Application>().getSharedPreferences("vox_prefs", android.content.Context.MODE_PRIVATE)
        hostIp = prefsHost.getString("host_ip", "100.91.141.101") ?: "100.91.141.101"
        tts = VoxTts { tentarOuvir() }
        tts?.init(getApplication())
        audioPlayer = VoxAudioPlayer(onDone = {
            if (!ouvindo) status = "Pronto para falar"
        })
        // Restaura o estado "Eco" persistido entre sessions
        val prefs = getApplication<Application>().getSharedPreferences("vox_prefs", android.content.Context.MODE_PRIVATE)
        ecoAtivo = prefs.getBoolean("eco_ativo", false)
        // Solicita desligar otimizacao de bateria na primeira execucao
        val ctx = getApplication<Application>()
        val pm = ctx.getSystemService(Application.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${ctx.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        }
    }

    fun toggleEco() {
        // existing toggleEco code unchanged
        // existing toggleEco code unchanged
        ecoAtivo = !ecoAtivo
        val prefs = getApplication<Application>().getSharedPreferences("vox_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("eco_ativo", ecoAtivo).apply()
        if (ecoAtivo) {
            tts?.speak("Eco ativado. Sistema de voz online.") {}
            if (conectado && microfonePermitido && !ouvindo) tentarOuvir()
        } else {
            tts?.speak("Eco desativado. Modo texto restaurado.") {}
            if (ouvindo) pararOuvir()
        }
    }

    // Persist and update host IP used for WebSocket connection
    fun updateHost(newHost: String) {
        hostIp = newHost
        val prefsHost = getApplication<Application>().getSharedPreferences("vox_prefs", android.content.Context.MODE_PRIVATE)
        prefsHost.edit().putString("host_ip", newHost).apply()
    }

    fun permissaoConcedida() {
        microfonePermitido = true
        tentarOuvir()
    }

    private fun tentarOuvir() {
        if (!ecoAtivo) return  // Modo Eco desligado: auto-ouvir desativado
        if (ouvindo || !microfonePermitido || !conectado) return
        comecarOuvir()
    }

    private val onSttResult: (String) -> Unit = { text ->
        viewModelScope.launch(Dispatchers.Main) {
            ouvindo = false
            mensagens = mensagens + Mensagem(text, true)
            ws?.send(text)
            processando = true
            progressoEtapa = "Enviando sua mensagem"
            // Modo Eco ativo: volta a ouvir automaticamente após processar
            if (ecoAtivo && conectado && microfonePermitido) {
                // pequeno delay na main thread (SpeechRecognizer exige main thread)
                viewModelScope.launch(Dispatchers.Main) {
                    delay(1500)
                    if (ecoAtivo && !ouvindo && !processando) {
                        comecarOuvir()
                    }
                }
            }
        }
    }

    private val onMessage: (String) -> Unit = { raw ->
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val json = JSONObject(raw)
                if (json.has("progresso")) {
                    val etapa = json.getString("progresso")
                    if (etapa.isNotEmpty()) {
                        progressoEtapa = etapa
                    }
                    return@launch
                }
                if (json.has("corrigido")) {
                    val corr = json.getString("corrigido")
                    val last = mensagens.lastOrNull()
                    if (last != null && last.deUsuario) {
                        mensagens = mensagens.dropLast(1) + Mensagem(corr, true, last.imagemB64, last.mime)
                    }
                }
                if (json.has("audio_streaming")) {
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) {
                        mensagens = mensagens + Mensagem(text, false)
                    }
                    processando = false
                    progressoEtapa = ""
                    tts?.stop()
                    saudacaoLocalFalada = true
                    if (ouvindo) pararOuvir()
                    audioStreaming = true
                    audioPlayer?.startStream()
                    if (ecoAtivo && conectado && microfonePermitido) {
                        viewModelScope.launch(Dispatchers.Main) {
                            delay(1200)
                            if (ecoAtivo && !ouvindo) comecarOuvir()
                        }
                    }
                } else if (json.has("audio_chunk")) {
                    val chunk = json.getString("audio_chunk")
                    audioPlayer?.playChunk(chunk)
                } else if (json.has("audio_done")) {
                    audioStreaming = false
                    audioPlayer?.finishStream()
                } else if (json.has("audio")) {
                    val audioB64 = json.getString("audio")
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) {
                        mensagens = mensagens + Mensagem(text, false)
                    }
                    processando = false
                    progressoEtapa = ""
                    tts?.stop()
                    saudacaoLocalFalada = true
                    if (ouvindo) pararOuvir()
                    audioPlayer?.play(audioB64)
                    if (ecoAtivo && conectado && microfonePermitido) {
                        viewModelScope.launch(Dispatchers.Main) {
                            delay(1200)
                            if (ecoAtivo && !ouvindo) comecarOuvir()
                        }
                    }
                } else if (json.has("text")) {
                    val text = json.getString("text")
                    mensagens = mensagens + Mensagem(text, false)
                    processando = false
                    progressoEtapa = ""
                    tts?.stop()
                    saudacaoLocalFalada = true
                }
            } catch (_: Exception) {
                mensagens = mensagens + Mensagem(raw, false)
                processando = false
                progressoEtapa = ""
            }
        }
    }

    private val onStatus: (String) -> Unit = { s ->
        viewModelScope.launch(Dispatchers.Main) {
            status = s
            conectado = s == "Conectado" || s == "Online"
            if (conectado && !saudacaoLocalFalada && !ouvindo) {
                tts?.speak(saudacoesLocais.random()) {}
            }
        }
    }

    private val onSttError: (String) -> Unit = { msg ->
        ouvindo = false
        status = msg
    }

    fun connect(host: String, port: Int = 8765) {
        ws?.disconnect()
        saudacaoLocalFalada = false
        ws = VoxWebSocket(onMessage, onStatus)
        ws?.connect(host, port)
    }

    fun send(text: String) {
        if (!conectado || ws == null) {
            status = "Desconectado — toque em Conectar primeiro"
            return
        }
        if (ouvindo) pararOuvir()
        ws?.send(text)
        mensagens = mensagens + Mensagem(text, true)
        textoInput = ""
        processando = true
        progressoEtapa = "Enviando sua mensagem"
    }

    fun enviarImagem(base64: String, mime: String, texto: String) {
        if (!conectado || ws == null) {
            status = "Desconectado — toque em Conectar primeiro"
            return
        }
        if (ouvindo) pararOuvir()
        val payload = JSONObject().apply {
            put("tipo", "imagem")
            put("texto", texto)
            put("imagem", base64)
            put("mime", mime)
        }
        ws?.send(payload.toString())
        mensagens = mensagens + Mensagem(texto, true, imagemB64 = base64, mime = mime)
        textoInput = ""
        processando = true
        progressoEtapa = "Enviando imagem"
    }

    fun comecarOuvir(interromper: Boolean = false) {
        if (interromper) audioPlayer?.stop()
        if (ouvindo) return
        stt?.destroy()
        ouvindo = true
        stt = VoxStt(onSttResult, onSttError)
        stt?.start(getApplication())
    }

    fun pararOuvir() {
        stt?.stop()
        ouvindo = false
    }

    override fun onCleared() {
        ws?.disconnect()
        stt?.destroy()
        tts?.shutdown()
        audioPlayer?.shutdown()
    }
}
