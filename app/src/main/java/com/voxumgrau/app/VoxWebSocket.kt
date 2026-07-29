package com.voxumgrau.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VoxWebSocket(
    private val onMessage: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private var host: String = ""
    private var port: Int = 8765
    private var desconectouIntencionalmente = false
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(host: String, port: Int) {
        this.host = host
        this.port = port
        desconectouIntencionalmente = false
        conectar()
    }

    private fun conectar() {
        val url = "ws://$host:$port"
        onStatus("Conectando")
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                onStatus("Conectado")
            }
            override fun onMessage(ws: WebSocket, text: String) {
                onStatus("Online")
                onMessage(text)
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                onStatus("Desconectando")
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onStatus("Desconectado")
                tentarReconectar()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                onStatus("Erro: ${t.message}")
                tentarReconectar()
            }
        })
    }

    private fun tentarReconectar() {
        if (desconectouIntencionalmente) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var tentativa = 0
            while (!desconectouIntencionalmente) {
                tentativa++
                val espera = (tentativa * 2).coerceAtMost(30)
                onStatus("Reconectando em ${espera}s (tentativa $tentativa)")
                delay(espera * 1000L)
                if (desconectouIntencionalmente) return@launch
                conectar()
                delay(3000)
                if (ws != null && !desconectouIntencionalmente) break
            }
        }
    }

    fun send(text: String) {
        ws?.send(text)
    }

    fun disconnect() {
        desconectouIntencionalmente = true
        reconnectJob?.cancel()
        ws?.close(1000, "App closing")
        ws = null
    }
}
