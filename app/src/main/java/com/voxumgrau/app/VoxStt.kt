package com.voxumgrau.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * VoxStt — reconhecimento de fala com TURN-TAKING correto.
 *
 * Trata separadamente transcrição parcial e final:
 *   - onPartial: exibição ao vivo enquanto o usuário fala (NUNCA envia turno).
 *   - onResult:  resultado final, o ÚNICO ponto que fecha o turno e envia.
 *
 * O bug original (parcial chamando onResult) fechava o turno no meio da fala,
 * enviando à bridge trechos incompletos ou duplicados. Aqui o parcial apenas
 * atualiza a UI, e o turno é enviado uma única vez no final.
 */
class VoxStt(
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPartial: (String) -> Unit = {}
) {

    private var recognizer: SpeechRecognizer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    // Um reconhecimento só fecha o turno uma vez; parciais que chegam depois
    // do resultado final são ignorados (evita reenvio duplicado).
    private var turnoFechado = false

    fun start(context: Context) {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nada ouvido"
                        else -> "Erro de áudio: $error"
                    }
                    abandonarFoco()
                    onError(msg)
                }
                override fun onResults(results: Bundle?) {
                    abandonarFoco()
                    if (turnoFechado) return
                    turnoFechado = true
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!texts.isNullOrEmpty()) {
                        onResult(texts[0])
                    } else {
                        onError("Não entendi")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    // Parcial é só exibição ao vivo: NUNCA fecha turno nem envia.
                    // Se o final já chegou, ignora (evita reabrir o turno).
                    if (turnoFechado) return
                    val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!texts.isNullOrEmpty()) {
                        onPartial(texts[0])
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
        audioManager?.requestAudioFocus(focusRequest!!)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("pt", "BR"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o Jarvis")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0)
        }

        recognizer?.startListening(intent)
    }

    private fun abandonarFoco() {
        try {
            if (audioManager != null && focusRequest != null) {
                audioManager?.abandonAudioFocusRequest(focusRequest!!)
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        abandonarFoco()
        recognizer?.stopListening()
    }

    fun destroy() {
        abandonarFoco()
        recognizer?.destroy()
        recognizer = null
    }
}
