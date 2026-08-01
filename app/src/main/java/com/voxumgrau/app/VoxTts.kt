package com.voxumgrau.app

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoxTts(private val onDone: () -> Unit = {}) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                engine.language = Locale("pt", "BR")

                val voices = engine.voices
                var jarvisVoice = voices?.firstOrNull { v ->
                    v.locale.language == "pt" &&
                    (v.name.contains("male", ignoreCase = true) || v.name.contains("masculin", ignoreCase = true))
                }
                if (jarvisVoice == null) {
                    val ptVoices = voices?.filter { it.locale.language == "pt" }
                    jarvisVoice = ptVoices?.lastOrNull()
                }
                if (jarvisVoice != null) {
                    engine.voice = jarvisVoice
                }

                engine.setPitch(0.65f)
                engine.setSpeechRate(0.85f)
                ready = true
            }
        }
    }

    fun init(context: android.content.Context) {
        tts = TextToSpeech(context, this)
    }

    fun speak(text: String, done: (() -> Unit)? = null) {
        if (!ready) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) { (done ?: onDone)() }
            override fun onError(utteranceId: String?) {}
            override fun onStart(utteranceId: String?) {}
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vox")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
