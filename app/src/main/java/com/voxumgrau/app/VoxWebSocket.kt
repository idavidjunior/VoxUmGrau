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

import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicInteger

/**
 * VoxWebSocket - Camada de Comunicacao Resiliente (Fases 2 e 3, decisao 112).
 *
 * Recursos:
 *  - Backoff exponencial com jitter 50% (1s -> 2s -> 4s -> 5s teto): evita
 *    thundering herd apos queda do servidor (padrao ouro Socket.IO).
 *  - Heartbeat application-level a cada 15s: 3 falhas = conexao morta
 *    -> fecha para disparar onFailure -> reconectar.
 *  - Fila de reenvio ACK-based: cada mensagem tem ID; a bridge confirma
 *    com {"ack": msg_id}; ao reconectar, reenvia pendentes.
 *  - Ping nativo OkHttp mantido (180s) como camada extra alem do heartbeat.
 */
class VoxWebSocket(
    private val onMessage: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(180, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private val hostCandidates: MutableList<Pair<String, Int>> = mutableListOf()
    private var fallbackIndex = 0
    private var desconectouIntencionalmente = false
    private var reconectouComSucesso = false
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Heartbeat application-level (Fase 2)
    private val HEARTBEAT_INTERVAL_MS = 15_000L
    private val HEARTBEAT_MAX_FALHAS = 3
    private var falhasHeartbeat = 0

    // Backoff exponencial com jitter (Fase 2 - padrao Socket.IO ouro)
    private val DELAY_INICIAL = 1000L
    private val DELAY_MAXIMO = 5000L
    private val JITTER_FACTOR = 0.5
    private var tentativaReconnect = 0

    // Fila de reenvio ACK-based (Fase 3)
    private val proximoId = AtomicInteger(1)
    private val filaPendentes: MutableList<Pair<Int, String>> = mutableListOf()
    private val filaLock = Any()

    /**
     * Conecta tentando varios enderecos em ordem de prioridade
     * (Local -> Tailscale IPv4 -> hostname). Se um falha, tenta o proximo.
     */
    fun connect(host: String, port: Int) {
        // Fase 3 - fallback de rede em ordem de prioridade:
        //   1. Host principal (Tailscale IPv4 ou outro configurado)
        //   2. Host local da LAN (192.168.x.x) — se o principal for Tailscale
        //   3. localhost (127.0.0.1) — depuracao no mesmo dispositivo
        // A bridge escuta em 0.0.0.0:8765, entao responde por qualquer interface.
        hostCandidates.clear()
        hostCandidates.add(Pair(host, port))
        val ehTailscale = host.startsWith("100.")
        if (ehTailscale) {
            // Tailscale caiu: tenta LAN antes de localhost
            hostCandidates.add(Pair("192.168.15.9", port))
        }
        if (host != "127.0.0.1" && host != "localhost") {
            hostCandidates.add(Pair("127.0.0.1", port))
        }
        Log.d("VoxWebSocket", "Candidatos de conexao: $hostCandidates")
        desconectouIntencionalmente = false
        reconectouComSucesso = false
        fallbackIndex = 0
        tentativaReconnect = 0
        conectar()
    }

    private fun conectar() {
        if (fallbackIndex >= hostCandidates.size) {
            fallbackIndex = 0
        }
        val (h, p) = hostCandidates[fallbackIndex]
        val url = "ws://$h:$p"
        Log.d("VoxWebSocket", "Conectando a $url (candidato ${fallbackIndex + 1}/${hostCandidates.size})")
        onStatus("Inicializando conexão...")
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("VoxWebSocket", "Connected to $h:$p")
                tentativaReconnect = 0
                reconectouComSucesso = true
                onStatus("Conectado")
                iniciarHeartbeat()
                reenviarPendentes()
            }
            override fun onMessage(ws: WebSocket, text: String) {
                onStatus("Online")
                processarMensagem(text)
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                onStatus("Desconectando")
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                pararHeartbeat()
                onStatus("Desconectado")
                tentarReconectar()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("VoxWebSocket", "Failure em $h:$p -> ${t.message}")
                pararHeartbeat()
                if (!reconectouComSucesso && hostCandidates.size > 1 && fallbackIndex < hostCandidates.size - 1) {
                    fallbackIndex++
                    onStatus("Trocando de rota (${fallbackIndex + 1}/${hostCandidates.size})...")
                    tentarProximoHost()
                } else {
                    onStatus("Erro: ${t.message}")
                    tentarReconectar()
                }
            }
        })
    }

    private fun tentarProximoHost() {
        if (desconectouIntencionalmente) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(300)
            if (!desconectouIntencionalmente) conectar()
        }
    }

    /**
     * Backoff exponencial com jitter (Fase 2):
     *   delay = min(DELAY_INICIAL * 2^n, DELAY_MAXIMO) * (1 +/- jitter)
     * Sequencia tipica: 1s -> 2s -> 4s -> 5s -> 5s (teto)
     * Jitter evita thundering herd quando o servidor volta.
     */
    private fun tentarReconectar() {
        if (desconectouIntencionalmente) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (!desconectouIntencionalmente) {
                tentativaReconnect++
                val base = (DELAY_INICIAL shl (tentativaReconnect - 1).coerceAtMost(5)).coerceAtMost(DELAY_MAXIMO)
                val jitter = (base * JITTER_FACTOR).toLong()
                val espera = (base - jitter + (Math.random() * (2 * jitter)).toLong()).coerceAtLeast(500L)
                Log.d("VoxWebSocket", "Reconnect tentativa $tentativaReconnect: ${espera}ms")
                val exibidoSeg = (espera + 500L) / 1000L
                onStatus("Reconectando em ${exibidoSeg}s (tentativa $tentativaReconnect)")
                delay(espera)
                if (desconectouIntencionalmente) return@launch
                conectar()
                // espera breve para confirmar conexao antes de reavaliar o loop
                delay(4000)
                if (reconectouComSucesso && !desconectouIntencionalmente) break
            }
        }
    }

    /**
     * Heartbeat application-level a cada 15s (Fase 2).
     * Envia {"tipo":"ping"} e espera {"tipo":"pong"}.
     * 3 falhas consecutivas sem pong => fecha conexao para forcar reconexao.
     */
    private fun iniciarHeartbeat() {
        pararHeartbeat()
        falhasHeartbeat = 0
        heartbeatJob = scope.launch {
            while (!desconectouIntencionalmente && ws != null) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (desconectouIntencionalmente) break
                try {
                    val ping = JSONObject().apply { put("tipo", "ping"); put("origem", "android-heartbeat") }.toString()
                    val ok = ws?.send(ping) ?: false
                    if (!ok) {
                        Log.w("VoxWebSocket", "Heartbeat: envio falhou (ws nula/false)")
                        falhasHeartbeat++
                        if (falhasHeartbeat >= HEARTBEAT_MAX_FALHAS) forcarReconexaoPorHeartbeat()
                    }
                    // Se em 2 ciclos (~30s) nao receber pong, falhasHeartbeat ja tera excedido 3
                } catch (e: Exception) {
                    falhasHeartbeat++
                    if (falhasHeartbeat >= HEARTBEAT_MAX_FALHAS) forcarReconexaoPorHeartbeat()
                }
            }
        }
    }

    private fun forcarReconexaoPorHeartbeat() {
        Log.w("VoxWebSocket", "Heartbeat falhou $HEARTBEAT_MAX_FALHAS vezes - forçando reconexão")
        pararHeartbeat()
        ws?.close(1011, "Heartbeat timeout")
        ws = null
        onStatus("Reconectando (heartbeat falhou)")
        tentarReconectar()
    }

    private fun pararHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Envia uma mensagem atribuindo um ID unico e enfileira para ACK (Fase 3).
     * Se cair antes do ACK, a mensagem sera reenviada ao reconectar.
     */
    fun send(text: String) {
        val id = proximoId.getAndIncrement()
        val payload = JSONObject().apply {
            put("tipo", "mensagem")
            put("id", id)
            put("texto", text)
        }.toString()
        synchronized(filaLock) { filaPendentes.add(Pair(id, payload)) }
        val enviado = ws?.send(payload) ?: false
        if (!enviado) {
            Log.w("VoxWebSocket", "send: ws nula, msg $id ficou na fila")
        }
    }

    /**
     * Processa mensagens recebidas.
     * Se for ACK, remove da fila. Caso contrario, repassa para a UI/tratador.
     */
    private fun processarMensagem(text: String) {
        try {
            val obj = JSONObject(text)
            if (obj.has("ack")) {
                val ackId = obj.getInt("ack")
                synchronized(filaLock) {
                    filaPendentes.removeAll { it.first == ackId }
                }
                Log.d("VoxWebSocket", "ACK recebido para msg $ackId - removido da fila")
                // Confirma ponte viva tambem para o heartbeat
                falhasHeartbeat = 0
                return
            }
            if (obj.optString("tipo") == "pong") {
                // Bridge respondeu o ping do heartbeat
                falhasHeartbeat = 0
                return
            }
        } catch (_: Exception) {
            // nao eh JSON estruturado -> repassa como antes
        }
        onMessage(text)
    }

    /**
     * Ao reconectar, reenvia todas as mensagens pendentes (nao confirmadas).
     * Fase 3 - fila de reenvio ACK-based: nenhuma fala se perde.
     */
    private fun reenviarPendentes() {
        val copia: List<Pair<Int, String>>
        synchronized(filaLock) {
            copia = filaPendentes.toList()
        }
        if (copia.isNotEmpty()) {
            Log.d("VoxWebSocket", "Reenviando ${copia.size} mensagem(ns) pendente(s)")
            for ((_, payload) in copia) {
                ws?.send(payload)
            }
        }
    }

    fun disconnect() {
        desconectouIntencionalmente = true
        reconectouComSucesso = false
        reconnectJob?.cancel()
        pararHeartbeat()
        ws?.close(1000, "App closing")
        ws = null
    }
}
