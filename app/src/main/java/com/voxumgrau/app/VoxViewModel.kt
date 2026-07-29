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
            if (json.has("audio")) {
                val audioB64 = json.getString("audio")
                val text = json.optString("text", "")
                if (text.isNotEmpty()) {
                    mensagens = mensagens + Mensagem(text, false)
                }
                processando = false
                if (ouvindo) pararOuvir()
                audioPlayer?.play(audioB64)
            } else if (json.has("text")) {
                val text = json.getString("text")
                mensagens = mensagens + Mensagem(text, false)
                processando = false
            }
        } catch (_: Exception) {
            mensagens = mensagens + Mensagem(raw, false)
            processando = false
        }
    }

    private val onStatus: (String) -> Unit = { s ->
        status = s
        conectado = s == "Conectado" || s == "Online"
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
        ws = VoxWebSocket(onMessage, onStatus)
        ws?.connect(host, port)
    }

    fun send(text: String) {
        ws?.send(text)
        mensagens = mensagens + Mensagem(text, true)
        textoInput = ""
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
