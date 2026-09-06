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
 * VoxHealthInfo - Snapshot de saude da conexao.
 * Consumido pela UI para mostrar rota ativa, ultima atividade, tentativas
 * de reconexao e falhas de heartbeat em tempo real.
 */
data class VoxHealthInfo(
    val rotaAtiva: String = "",
    val ultimaAtividadeMs: Long = 0L,
    val tentativaReconnect: Int = 0,
    val falhasHeartbeat: Int = 0,
    val conectado: Boolean = false,
    val rede: String = ""
)

/**
 * VoxWebSocket - Camada de Comunicacao Resiliente (Fases 2 e 3, decisao 112).
 *
 * Recursos:
 *  - Backoff exponencial com jitter 50% (1s -> 2s -> 4s -> 5s teto): evita
 *    thundering herd apos queda do servidor (padrao ouro Socket.IO).
 *  - Heartbeat application-level a cada 15s: se enviar ping e o pong nao
 *    voltar no ciclo seguinte, conta falha; 3 falhas = conexao morta
 *    -> fecha para disparar onFailure -> reconectar (detecta meia-morte).
 *  - Fila de reenvio ACK-based: cada mensagem tem ID; a bridge confirma
 *    com {"ack": msg_id}; ao reconectar, reenvia pendentes.
 *  - Ping nativo OkHttp mantido (180s) como camada extra alem do heartbeat.
 */
class VoxWebSocket(
    private val onMessage: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onHealth: (VoxHealthInfo) -> Unit = {}
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
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
    private var aguardandoPong = false

    // Modo tolerante: enquanto a app aguarda resposta da bridge (LLM+TTS demora
    // 30-90s e a bridge nao responde pings durante o processamento sequencial),
    // o heartbeat NAO conta falha de pong ausente, para nao reconectar no meio
    // da resposta e perder o streaming de texto/audio. Queda real continua
    // detectada pelo ping nativo OkHttp (180s) e por onFailure.
    @Volatile
    private var aguardandoResposta = false

    // Grace period inicial: a bridge leva ~45s (setup + saudação LLM+TTS) para
    // começar a ler o socket. Durante esse tempo os pings ficam no buffer e o
    // pong só volta depois. Sem grace period o app contaria 3 falhas (45s) e
    // forçaria reconexão em loop no meio do setup. Definimos uma janela de 90s
    // após a conexão: enquanto o primeiro pong não chegar, ausência de pong NÃO
    // conta falha. Pong real continua detectado (90s depois de conectar) e
    // queda real também (ping nativo OkHttp 180s + onFailure).
    @Volatile
    private var gracePeriodAteMs: Long = 0L

    fun setAguardandoResposta(aguardando: Boolean) {
        aguardandoResposta = aguardando
    }

    // Saude da conexao (consumida pela UI em tempo real)
    private var rotaAtiva: String = ""
    private var ultimaAtividadeMs: Long = 0L
    private var redeAtual: String = ""

    // Reconexao dirigida por sonda de rede: enquanto true, eventos de socket
    // (onClosed/onFailure) da conexao antiga sao ignorados, evitando corrida
    // entre o loop cego antigo e a vigia de rota.
    @Volatile
    private var processoDeRede = false
    private var vigiaJob: Job? = null
    private var rotaAtivaOriginal: String = ""
    private var portaOriginal: Int = 8765

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
        rotaAtiva = "$host:$port"
        ultimaAtividadeMs = 0L
        processoDeRede = false
        rotaAtivaOriginal = host
        portaOriginal = port
        emitirSaude()
        avaliarRotaViaSonda()
    }

    private fun conectar() {
        if (fallbackIndex >= hostCandidates.size) {
            fallbackIndex = 0
        }
        val (h, p) = hostCandidates[fallbackIndex]
        rotaAtiva = "$h:$p"
        emitirSaude()
        // Janela de tolerancia ao setup da bridge (90s): a ponte demora a
        // processar a saudacao inicial antes de ler os pings do app.
        falhasHeartbeat = 0
        gracePeriodAteMs = System.currentTimeMillis() + 90_000L
        val url = "ws://$h:$p"
        Log.d("VoxWebSocket", "Conectando a $url (candidato ${fallbackIndex + 1}/${hostCandidates.size})")
        onStatus("Inicializando conexão...")
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("VoxWebSocket", "Connected to $h:$p")
                tentativaReconnect = 0
                reconectouComSucesso = true
                ultimaAtividadeMs = System.currentTimeMillis()
                emitirSaude()
                onStatus("Conectado")
                iniciarHeartbeat()
                iniciarVigiaRota()
                reenviarPendentes()
            }
            override fun onMessage(ws: WebSocket, text: String) {
                ultimaAtividadeMs = System.currentTimeMillis()
                emitirSaude()
                onStatus("Online")
                processarMensagem(text)
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                onStatus("Desconectando")
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (processoDeRede) return
                pararHeartbeat()
                emitirSaude()
                onStatus("Desconectado")
                tentarReconectar()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("VoxWebSocket", "Failure em $h:$p -> ${t.message}")
                if (processoDeRede) return
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

    private fun emitirSaude() {
        onHealth(VoxHealthInfo(
            rotaAtiva = rotaAtiva,
            ultimaAtividadeMs = ultimaAtividadeMs,
            tentativaReconnect = tentativaReconnect,
            falhasHeartbeat = falhasHeartbeat,
            conectado = reconectouComSucesso && ws != null,
            rede = redeAtual
        ))
    }

    private fun tentarProximoHost() {
        if (desconectouIntencionalmente || processoDeRede) return
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
        if (desconectouIntencionalmente || processoDeRede) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (!desconectouIntencionalmente) {
                tentativaReconnect++
                emitirSaude()
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
     * Detecta conexao meio-morta: se o pong do ping anterior nao voltou
     * dentro do intervalo, conta falha mesmo com ws.send() retornando true.
     */
    private fun iniciarHeartbeat() {
        pararHeartbeat()
        falhasHeartbeat = 0
        aguardandoPong = false
        heartbeatJob = scope.launch {
            while (!desconectouIntencionalmente && ws != null) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (desconectouIntencionalmente) break
                try {
                    if (aguardandoPong) {
                        // Grace period de conexao: a bridge pode estar no setup
                        // (saudacao LLM+TTS leva ~45s) e ainda nao leu os pings.
                        // Nesta janela ausencia de pong e normal; nao conta falha.
                        val emGrace = System.currentTimeMillis() < gracePeriodAteMs
                        if (aguardandoResposta || emGrace) {
                            // Bridge ocupada processando: nao conta falha, so
                            // reporta a saude (evita reconexao/perda de resposta)
                            if (emGrace) {
                                Log.d("VoxWebSocket", "Heartbeat: sem pong, em grace period (setup bridge) - tolerado")
                            } else {
                                Log.d("VoxWebSocket", "Heartbeat: sem pong, mas aguardando resposta (tolerado)")
                            }
                            emitirSaude()
                        } else {
                            // Ping anterior sem pong dentro de 15s => conexao meio-morta
                            falhasHeartbeat++
                            emitirSaude()
                            Log.w("VoxWebSocket", "Heartbeat: pong nao voltou (falha $falhasHeartbeat/$HEARTBEAT_MAX_FALHAS)")
                            if (falhasHeartbeat >= HEARTBEAT_MAX_FALHAS) {
                                forcarReconexaoPorHeartbeat()
                                break
                            }
                        }
                    }
                    val ping = JSONObject().apply { put("tipo", "ping"); put("origem", "android-heartbeat") }.toString()
                    val ok = ws?.send(ping) ?: false
                    if (ok) {
                        aguardandoPong = true
                    } else {
                        falhasHeartbeat++
                        emitirSaude()
                        if (falhasHeartbeat >= HEARTBEAT_MAX_FALHAS) {
                            forcarReconexaoPorHeartbeat()
                            break
                        }
                    }
                } catch (e: Exception) {
                    falhasHeartbeat++
                    emitirSaude()
                    if (falhasHeartbeat >= HEARTBEAT_MAX_FALHAS) {
                        forcarReconexaoPorHeartbeat()
                        break
                    }
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
        emitirSaude()
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
     * Edit-and-resubmit (padrão ChatGPT/Claude): envia a edição de uma mensagem
     * já enviada. A bridge trunca o histórico a partir da mensagem editada e
     * regenera a resposta. Usa a mesma fila de ACK para não perder a edição em
     * caso de queda de conexão.
     */
    fun editarMensagem(textoAntigo: String, textoNovo: String) {
        val id = proximoId.getAndIncrement()
        val payload = JSONObject().apply {
            put("tipo", "editar")
            put("id", id)
            put("texto_antigo", textoAntigo)
            put("texto_novo", textoNovo)
        }.toString()
        synchronized(filaLock) { filaPendentes.add(Pair(id, payload)) }
        val enviado = ws?.send(payload) ?: false
        if (!enviado) {
            Log.w("VoxWebSocket", "editarMensagem: ws nula, edicao $id ficou na fila")
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
                aguardandoPong = false
                emitirSaude()
                return
            }
            if (obj.optString("tipo") == "pong") {
                // Bridge respondeu o ping do heartbeat
                falhasHeartbeat = 0
                aguardandoPong = false
                emitirSaude()
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

    /**
     * True se existe alguma mensagem não confirmada (ACK) na fila de reenvio.
     * Usado pela sincronização de histórico para não sobrescrever o que o
     * usuário acabou de enviar durante uma reconexão.
     */
    fun temPendentes(): Boolean {
        synchronized(filaLock) { return filaPendentes.isNotEmpty() }
    }

    fun disconnect() {
        desconectouIntencionalmente = true
        reconectouComSucesso = false
        processoDeRede = false
        reconnectJob?.cancel()
        pararHeartbeat()
        ws?.close(1000, "App closing")
        ws = null
        emitirSaude()
    }

    /**
     * True quando a conexao foi fechada pela app (nao por queda real).
     * Usado para nao disparar alerta sonoro de queda em desconexao manual.
     */
    fun foiFechadoPelaApp(): Boolean = desconectouIntencionalmente

    /**
     * Atualiza a rede ativa (rotulo) reportada pela UI. Setter idempotente
     * chamado pelo RedeMonitor via ViewModel; nao dispara reconexao.
     */
    fun setRede(rotulo: String) {
        if (redeAtual == rotulo) return
        redeAtual = rotulo
        emitirSaude()
    }

    /**
     * Vigia de rota: a cada TICK infere a rede a partir da rota ativa. Se a
     * rota for um IP privado (ROTA_PRIVADA = 100.x, 192.168.x, 10.x,
     * 172.16-31.x) mas ha uma VPN ativa, o roteamento pode nao chegar a esses
     * IPs; nesse caso sinaliza para um reconectar dirigido por sonda. Fora da
     * LAN (sem VPN), IPs privados nao sao alcancaveis por dados moveis — o
     * RedeMonitor ja tera disparado e a vigia apenas mantem o estado real.
     * Nao interfere quando ja conectado: a resposta dela vale quando nao ha
     * rota, mantendo o status honesto em vez de conexao morta silenciosa.
     */
    fun iniciarVigiaRota() {
        vigiaJob?.cancel()
        vigiaJob = scope.launch {
            while (!desconectouIntencionalmente) {
                delay(ROTA_TICK_MS)
                if (desconectouIntencionalmente) break
                val h = rotaAtiva.substringBeforeLast(":")
                val ehPrivada = ROTA_PRIVADA.any { h.startsWith(it) }
                val haVpn = tipoVpnAtual()
                if (ehPrivada && haVpn && reconectouComSucesso) {
                    avaliarRotaViaSonda()
                } else if (ehPrivada && !haVpn && (reconectouComSucesso || tentativaReconnect > 0)) {
                    rotaAtiva = "${rotaAtivaOriginal}:${portaOriginal}"
                    tentativaReconnect = 0
                    emitirSaude()
                }
            }
        }
    }

    fun pararVigiaRota() {
        vigiaJob?.cancel()
        vigiaJob = null
    }

    private val ROTA_PRIVADA = listOf("100.", "192.168.", "10.", "172.16.", "172.17.", "172.18.",
        "172.19.", "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.", "172.26.",
        "172.27.", "172.28.", "172.29.", "172.30.", "172.31.")

    // Tipos de rede que indicam tunel/VPN: uma rota 100.x (Tailscale) exige
    // que a VPN esteja ativa para ser alcancada por dados moveis.
    private fun tipoVpnAtual(): Boolean {
        return redeAtual.contains("VPN")
    }

    /**
     * Sonda TCP nos candidatos (1.5s por host, em paralelo) e conecta ao
     * primeiro alcançável. Se nenhum responde, reporta "Sem rota para o PC"
     * na UI (nunca reconexao cega). Disparado na troca de rede.
     */
    fun reiniciarConexao() {
        if (desconectouIntencionalmente) return
        reconnectJob?.cancel()
        processoDeRede = true
        reconnectJob = scope.launch {
            pararHeartbeat()
            ws?.close(1000, "Rede mudou")
            ws = null
            reconectouComSucesso = false
            tentativaReconnect = 0
            emitirSaude()
            avaliarRotaViaSonda()
            processoDeRede = false
        }
    }

    private fun avaliarRotaViaSonda() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val alcançavel = sondarHosts()
            if (desconectouIntencionalmente) return@launch
            if (alcançavel != null) {
                rotaAtiva = "${alcançavel.first}:${alcançavel.second}"
                tentativaReconnect = 0
                emitirSaude()
                conectar()
            } else {
                // Nenhuma rota agora: mantem estado honesto e espera o
                // RedeMonitor (ou o usuario via botao Testar) disparar de novo.
                rotaAtiva = "Sem rota para o PC"
                onStatus("Sem rota para o PC (verifique Wi-Fi/VPN)")
                emitirSaude()
            }
        }
    }

    /**
     * Testa a alcancabilidade de cada host candidato com um socket TCP
     * (connect timeout 1.5s). Retorna o primeiro (na ordem de prioridade) que
     * consegue abrir conexao TCP antes do OkHttp subir o WebSocket. Isso
     * derruba o custo de um ciclo cego: em vez de 2x10s de timeout HTTP, a
     * sonda decide em ~1.5s/host.
     */
    private fun sondarHosts(): Pair<String, Int>? {
        val ordem = hostCandidates.toList()
        for ((h, p) in ordem) {
            if (desconectouIntencionalmente) return null
            var so: java.net.Socket? = null
            try {
                so = java.net.Socket()
                so.connect(java.net.InetSocketAddress(h, p), SONDA_TIMEOUT_MS)
                Log.d("VoxWebSocket", "Sonda OK: $h:$p -> conectando")
                return Pair(h, p)
            } catch (_: Exception) {
                Log.d("VoxWebSocket", "Sonda falhou: $h:$p")
            } finally {
                try { so?.close() } catch (_: Exception) {}
            }
        }
        return null
    }

    private val SONDA_TIMEOUT_MS = 1500
    private val ROTA_TICK_MS = 5000L
}
