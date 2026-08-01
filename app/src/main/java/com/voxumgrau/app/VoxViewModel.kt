package com.voxumgrau.app

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
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

    private var tts: VoxTts? = null
    private var stt: VoxStt? = null
    private var ws: VoxWebSocket? = null
    private var audioPlayer: VoxAudioPlayer? = null
    private var saudacaoLocalFalada = false

    private val saudacoesLocais = listOf(
        "Opa, chegou!",
        "Pronto, estou aqui!",
        "Fala aí!",
        "O sistema tá ligado!",
        "Cheguei, pode falar!",
    )

    fun initVoz() {
        tts = VoxTts { tentarOuvir() }
        tts?.init(getApplication())
        audioPlayer = VoxAudioPlayer(onDone = {
            if (!ouvindo) status = "Pronto para falar"
        })
    }

    fun permissaoConcedida() {
        microfonePermitido = true
        tentarOuvir()
    }

    private fun tentarOuvir() {
        if (ouvindo || !microfonePermitido || !conectado) return
        comecarOuvir()
    }

    private val onMessage: (String) -> Unit = { raw ->
        try {
            val json = JSONObject(raw)
            if (json.has("corrigido")) {
                val corr = json.getString("corrigido")
                val last = mensagens.lastOrNull()
                if (last != null && last.deUsuario) {
                    mensagens = mensagens.dropLast(1) + Mensagem(corr, true, last.imagemB64, last.mime)
                }
            }
            if (json.has("audio")) {
                val audioB64 = json.getString("audio")
                val text = json.optString("text", "")
                if (text.isNotEmpty()) {
                    mensagens = mensagens + Mensagem(text, false)
                }
                processando = false
                tts?.stop()
                saudacaoLocalFalada = true
                if (ouvindo) pararOuvir()
                audioPlayer?.play(audioB64)
            } else if (json.has("text")) {
                val text = json.getString("text")
                mensagens = mensagens + Mensagem(text, false)
                processando = false
                tts?.stop()
                saudacaoLocalFalada = true
            }
        } catch (_: Exception) {
            mensagens = mensagens + Mensagem(raw, false)
            processando = false
        }
    }

    private val onStatus: (String) -> Unit = { s ->
        status = s
        conectado = s == "Conectado" || s == "Online"
        if (conectado && !saudacaoLocalFalada && !ouvindo) {
            tts?.speak(saudacoesLocais.random()) {}
        }
    }

    private val onSttResult: (String) -> Unit = { text ->
        ouvindo = false
        mensagens = mensagens + Mensagem(text, true)
        ws?.send(text)
        processando = true
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
