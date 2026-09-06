package com.voxumgrau.app

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
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

    var saude by mutableStateOf(VoxHealthInfo())
        private set

    var redeAtual by mutableStateOf("")
        private set

    private var stt: VoxStt? = null
    private var ws: VoxWebSocket? = null
    private var audioPlayer: VoxAudioPlayer? = null
    private var redeMonitor: RedeMonitor? = null

    var audioStreaming by mutableStateOf(false)
        private set

    fun initVoz() {
        // Load persisted host IP (default 127.0.0.1)
        val prefsHost = getApplication<Application>().getSharedPreferences("vox_prefs", android.content.Context.MODE_PRIVATE)
        hostIp = prefsHost.getString("host_ip", "100.91.141.101") ?: "100.91.141.101"
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
        // Observa transicoes de rede (Wi-Fi <-> dados moveis, VPN, sem rede)
        // para reconectar dirigido por sonda no lugar do retry cego.
        val ctxMon = getApplication<Application>()
        redeMonitor = RedeMonitor(ctxMon) { tipo ->
            ws?.setRede(tipo.rotulo)
            reconectarPorRede(tipo)
        }
        redeMonitor?.registrar()
    }

    /**
     * Resposta a uma transicao de rede. Se ja conectado, nao faz nada (a
     * conexao atual segue valida). Se desconectado, reinicia a conexao com
     * sonda de alcancabilidade — deteccao automatica da troca Wi-Fi/dados:
     * o Android avisa via callback e o app tenta de novo na hora, em vez de
     * esperar dezenas de segundos no retry cego. Duende pequeno evita corrida
     * quando varios eventos chegam juntos.
     */
    private fun reconectarPorRede(tipo: TipoRede) {
        viewModelScope.launch(Dispatchers.Main) {
            delay(800) // debounce: coalesce onAvailable/onCapabilitiesChanged
            if (tipo == TipoRede.SEM_REDE) {
                if (conectado) {
                    ws?.disconnect()
                    status = "Sem rede — reconecta automaticamente ao voltar"
                }
                return@launch
            }
            redeAtual = tipo.rotulo
            if (!conectado) {
                ws?.reiniciarConexao()
            }
        }
    }

    /**
     * Teste manual de conexao (botao "Testar"): sonda a rota e reconecta.
     * Exposto para o usuario validar a alcançabilidade do PC sem depender do
     * evento automatico de rede.
     */
    fun testarConexao() {
        if (hostIp.isBlank()) return
        viewModelScope.launch(Dispatchers.Main) {
            status = "Testando conexão..."
            ws?.reiniciarConexao()
        }
    }


    fun toggleEco() {
        ecoAtivo = !ecoAtivo
        val prefs = getApplication<Application>().getSharedPreferences("vox_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("eco_ativo", ecoAtivo).apply()
        if (ecoAtivo) {
            if (conectado && microfonePermitido && !ouvindo) tentarOuvir()
        } else {
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

    private val onSttResult: (String) -> Unit = { raw ->
        viewModelScope.launch(Dispatchers.Main) {
            val text = VoxPontuacao.pontuar(raw)
            ouvindo = false
            mensagens = mensagens + Mensagem(text, true)
            ws?.setAguardandoResposta(true)
            ws?.send(text)
            processando = true
            progressoEtapa = "Enviando sua mensagem"
            // Modo Eco ativo: volta a ouvir automaticamente apÃ³s processar
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
                // Histórico ÚNICO (app = voz do ecossistema): a bridge envia a
                // conversa canônica do ecossistema na conexão. O app não mantém
                // histórico próprio — apenas renderiza o que o ecossistema tem.
                if (json.optString("tipo") == "historico") {
                    val arr = json.optJSONArray("mensagens") ?: return@launch
                    // Só substitui quando não há trabalho local em voo (fila de
                    // reenvio vazia e sem resposta pendente), para não apagar o
                    // que o usuário acabou de enviar durante uma reconexão.
                    if (processando || ws?.temPendentes() == true) return@launch
                    val lista = mutableListOf<Mensagem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val texto = obj.optString("texto")
                        if (texto.isNotEmpty()) {
                            lista.add(Mensagem(texto, obj.optBoolean("de_usuario")))
                        }
                    }
                    if (lista.isNotEmpty()) mensagens = lista
                    return@launch
                }
                if (json.optString("tipo") == "imagem") {
                    // Imagem gerada pela bridge (diagrama/mapa mental): decodifica
                    // o PNG base64 e renderiza no balão. A resposta textual segue
                    // depois no fluxo normal.
                    val b64 = json.optString("base64_png")
                    val legenda = json.optString("legenda", "").ifEmpty { "Imagem gerada" }
                    if (b64.isNotEmpty()) {
                        try {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            if (bytes.isNotEmpty()) {
                                mensagens = mensagens + Mensagem(
                                    texto = legenda,
                                    deUsuario = false,
                                    imagem = bytes,
                                    legenda = legenda
                                )
                            }
                        } catch (_: Exception) {
                            // PNG inválido: ignora o payload; o texto segue normal
                        }
                    }
                    return@launch
                }
                if (json.optBoolean("edicao_falhou")) {
                    // Edição não encontrada na bridge: restaura a mensagem
                    // original (a edição não vingou) e informa no status.
                    val antiga = json.optString("corrigido", "")
                    val ultima = mensagens.lastOrNull()
                    if (ultima != null && (ultima.deUsuario || ultima.texto == antiga)) {
                        mensagens = mensagens.dropLast(1) + Mensagem(antiga, true)
                    }
                    processando = false
                    progressoEtapa = ""
                    ws?.setAguardandoResposta(false)
                    status = "Não encontrei essa mensagem para editar"
                    return@launch
                }
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
                        mensagens = mensagens.dropLast(1) + Mensagem(corr, true, last.timestamp)
                    }
                }
                if (json.has("audio_streaming")) {
                    val text = json.optString("text", "")
                    // Após uma edição, o texto chega como resposta normal (sem
                    // flag). A mensagem editada já foi recolocada localmente.
                    if (text.isNotEmpty()) {
                        mensagens = mensagens + Mensagem(text, false)
                    }
                    processando = false
                    progressoEtapa = ""
                    ws?.setAguardandoResposta(false)

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
                    ws?.setAguardandoResposta(false)

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
                    ws?.setAguardandoResposta(false)

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
        }
    }

    private val onHealth: (VoxHealthInfo) -> Unit = { info ->
        viewModelScope.launch(Dispatchers.Main) {
            val caiu = saude.conectado && !info.conectado && ws?.foiFechadoPelaApp() != true
            saude = info
            if (caiu) audioPlayer?.playAlerta()
        }
    }

    private val onSttError: (String) -> Unit = { msg ->
        ouvindo = false
        status = msg
    }

    fun connect(host: String, port: Int = 8765) {
        ws?.disconnect()

        saude = VoxHealthInfo()
        ws = VoxWebSocket(onMessage, onStatus, onHealth)
        ws?.connect(host, port)
    }

    fun send(text: String) {
        if (!conectado || ws == null) {
            status = "Desconectado â€” toque em Conectar primeiro"
            return
        }
        if (ouvindo) pararOuvir()
        ws?.setAguardandoResposta(true)
        ws?.send(text)
        mensagens = mensagens + Mensagem(text, true)
        textoInput = ""
        processando = true
        progressoEtapa = "Enviando sua mensagem"
    }

    /**
     * Edit-and-resubmit: edita uma mensagem já enviada (padrão ChatGPT/Claude).
     * O texto original passa a ser o novo texto e tudo que vinha DEPOIS da
     * mensagem editada é descartado, tanto localmente quanto na bridge (que
     * trunca o histórico e regenera a resposta). Retorna true se iniciou.
     */
    fun editarMensagem(indice: Int, textoAntigo: String, textoNovo: String): Boolean {
        if (!conectado || ws == null) {
            status = "Desconectado — toque em Conectar primeiro"
            return false
        }
        val novo = textoNovo.trim()
        if (novo.isEmpty() || novo == textoAntigo) return false
        if (indice !in mensagens.indices || !mensagens[indice].deUsuario) return false
        if (processando) return false
        if (ouvindo) pararOuvir()
        // Descarta a mensagem editada e todas as seguintes, depois recoloca a
        // nova versão no lugar — a resposta virá regenerada pela bridge.
        val anteriores = mensagens.take(indice)
        mensagens = anteriores + Mensagem(novo, true)
        textoInput = ""
        ws?.setAguardandoResposta(true)
        ws?.editarMensagem(textoAntigo, novo)
        processando = true
        progressoEtapa = "Reenviando sua mensagem"
        return true
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
        redeMonitor?.desregistrar()
        redeMonitor = null
        ws?.disconnect()
        stt?.destroy()
        audioPlayer?.shutdown()
    }
}
