package com.voxumgrau.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * TipoRede - Classificacao simples da rede ativa do dispositivo.
 * Usada para o usuario e para a camada de conexao saberem onde estao
 * (Wi-Fi, dados moveis, VPN, ou nenhuma rede).
 */
enum class TipoRede(val rotulo: String) {
    SEM_REDE("Sem rede"),
    DADOS("Dados móveis"),
    WIFI("Wi-Fi"),
    VIA_VPN("Rede (VPN)"),
    OUTRA("Rede")
}

/**
 * RedeMonitor - Observa transicoes de rede do Android.
 *
 * Registra um ConnectivityManager.NetworkCallback na rede padrao e dispara
 * onMudancaRede somente quando a classificacao muda (nao a cada revalidacao),
 * evitando tempestades de reconexao. Sem o monitor, o app so descobria uma
 * troca Wi-Fi -> dados moveis quando o socket morria (lento).
 */
class RedeMonitor(
    private val context: Context,
    private val onMudancaRede: (TipoRede) -> Unit
) {
    @Volatile
    private var ultimoTipo: TipoRede = TipoRede.SEM_REDE

    private val cm: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun registrar() {
        ultimoTipo = tipoAtual()
        runCatching {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerDefaultNetworkCallback(callback)
        }.onFailure {
            Log.w("RedeMonitor", "nao foi possivel registrar callback de rede", it)
        }
    }

    fun desregistrar() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = avaliar("disponivel")
        override fun onLost(network: Network) = avaliar("perdida")
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
            avaliar("capacidades")
        }
    }

    private fun avaliar(causa: String) {
        val novo = tipoAtual()
        if (novo != ultimoTipo) {
            Log.d("RedeMonitor", "Rede mudou ($causa): ${ultimoTipo.rotulo} -> ${novo.rotulo}")
            ultimoTipo = novo
            onMudancaRede(novo)
        }
    }

    fun tipoAtual(): TipoRede {
        val network = cm.activeNetwork ?: return TipoRede.SEM_REDE
        val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull() ?: return TipoRede.SEM_REDE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return TipoRede.SEM_REDE
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return TipoRede.DADOS
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return TipoRede.WIFI
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return TipoRede.VIA_VPN
        return TipoRede.OUTRA
    }
}