package com.voxumgrau.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * VoxLogSocket - Cliente do canal /logs da bridge (mesmo protocolo do Edge).
 *
 * Conecta em ws://host:8765/logs, envia {"tipo":"log_subscribe","arquivos":
 * ["bridge","narrador"]} e recebe:
 *  - {"type":"log_snapshot","logs":{nome:{"existente":bool,"linhas":[...]}}}
 *  - {"type":"log_lines","linhas":{nome:[...]}}
 *
 * Reconexão com backoff simples (3s) enquanto não desconectado
 * intencionalmente. Não tem fila ACK nem heartbeat: é monitor de leitura,
 * e a reconexão perde apenas a janela das linhas que passaram.
 */
class VoxLogSocket(
    private val onLogLines: (String, List<String>) -> Unit,
    private val onStatus: (Boolean) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private var host = ""
    private var port = 8765
    @Volatile
    private var ativo = false
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private val ARQUIVOS = listOf("bridge", "narrador")
        private const val BACKOFF_RECONEXAO_MS = 3000L
    }

    fun connect(host: String, port: Int = 8765) {
        this.host = host
        this.port = port
        ativo = true
        reconnectJob?.cancel()
        conectar()
    }

    private fun conectar() {
        if (!ativo) return
        val url = "ws://$host:$port/logs"
        Log.d("VoxLogSocket", "Conectando a $url")
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("VoxLogSocket", "Connected, assinando $ARQUIVOS")
                onStatus(true)
                val subscribe = JSONObject().apply {
                    put("tipo", "log_subscribe")
                    put("arquivos", ARQUIVOS)
                }.toString()
                ws.send(subscribe)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                processar(text)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d("VoxLogSocket", "Closed")
                onStatus(false)
                if (ativo) reconectar()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("VoxLogSocket", "Failure -> ${t.message}")
                onStatus(false)
                if (ativo) reconectar()
            }
        })
    }

    /**
     * Reconexão dirigida por troca de rede (chamada pelo ViewModel junto com
     * o socket principal). Fecha e reconecta na hora, sem esperar o backoff.
     */
    fun reiniciarConexao() {
        if (!ativo) return
        reconnectJob?.cancel()
        ws?.close(1000, "Rede mudou")
        ws = null
        conectar()
    }

    private fun reconectar() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(BACKOFF_RECONEXAO_MS)
            if (ativo) conectar()
        }
    }

    private fun processar(text: String) {
        try {
            val obj = JSONObject(text)
            when (obj.optString("type")) {
                "log_snapshot" -> {
                    val logs = obj.optJSONObject("logs") ?: return
                    val keys = logs.keys()
                    while (keys.hasNext()) {
                        val nome = keys.next()
                        val info = logs.optJSONObject(nome) ?: continue
                        val arr = info.optJSONArray("linhas") ?: continue
                        val linhas = (0 until arr.length())
                            .map { arr.optString(it) }
                            .filter { it.isNotBlank() }
                        if (linhas.isNotEmpty()) onLogLines(nome, linhas)
                    }
                }
                "log_lines" -> {
                    val linhaObj = obj.optJSONObject("linhas") ?: return
                    val keys = linhaObj.keys()
                    while (keys.hasNext()) {
                        val nome = keys.next()
                        val arr = linhaObj.optJSONArray(nome) ?: continue
                        val linhas = (0 until arr.length())
                            .map { arr.optString(it) }
                            .filter { it.isNotBlank() }
                        if (linhas.isNotEmpty()) onLogLines(nome, linhas)
                    }
                }
            }
        } catch (_: Exception) {
            Log.e("VoxLogSocket", "frame invalido: $text")
        }
    }

    fun disconnect() {
        ativo = false
        reconnectJob?.cancel()
        ws?.close(1000, "App fechando")
        ws = null
        onStatus(false)
    }
}