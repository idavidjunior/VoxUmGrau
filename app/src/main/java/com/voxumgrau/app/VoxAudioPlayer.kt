package com.voxumgrau.app

import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream

class VoxAudioPlayer(private val onDone: () -> Unit = {}) {

    private var player: MediaPlayer? = null
    private var streaming = false
    private var fimStream = false
    // Fila progressiva de chunks: cada "audio_chunk" da bridge é um MP3
    // completo e é tocado imediatamente (não mais concatenado e tocado no fim).
    private val filaChunks = ArrayDeque<File>()

    /**
     * Alerta sonoro curto (queda de conexao). Usa ToneGenerator do
     * sistema, independente do fluxo de audio MP3 da bridge.
     */
    fun playAlerta() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 250)
            Thread {
                try {
                    Thread.sleep(350)
                } catch (_: InterruptedException) {
                } finally {
                    tg.release()
                }
            }.start()
        } catch (e: Exception) {
            Log.e("VoxAudio", "playAlerta: ${e.message}", e)
        }
    }

    fun play(base64Audio: String) {
        stop()
        var tempFile: File? = null
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            tempFile = File.createTempFile("vox_", ".mp3")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            player = MediaPlayer().apply {
                setDataSource(tempFile!!.absolutePath)
                setOnCompletionListener {
                    tempFile?.delete()
                    limparPlayer()
                    onDone()
                }
                setOnErrorListener { mp, what, extra ->
                    tempFile?.delete()
                    Log.e("VoxAudio", "MediaPlayer error: what=$what extra=$extra")
                    limparPlayer()
                    onDone()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("VoxAudio", "play: ${e.message}", e)
            tempFile?.delete()
            onDone()
        }
    }

    fun startStream() {
        cancelStream()
        streaming = true
        fimStream = false
    }

    /**
     * Recebe um chunk MP3 completo (streaming por sentença da bridge) e o
     * enfileira. Se nenhum áudio está tocando, inicia a reprodução na hora.
     * A reprodução é progressiva: cada sentença começa assim que chega, sem
     * esperar o fim da resposta.
     */
    fun playChunk(base64Chunk: String) {
        if (!streaming) {
            Log.w("VoxAudio", "playChunk chamado sem startStream ativo")
            return
        }
        if (base64Chunk.isBlank()) return
        try {
            val audioBytes = Base64.decode(base64Chunk, Base64.DEFAULT)
            if (audioBytes.isEmpty()) return
            val tf = File.createTempFile("vox_chunk_", ".mp3")
            FileOutputStream(tf).use { it.write(audioBytes) }
            filaChunks.addLast(tf)
            tocarProximo()
        } catch (e: Exception) {
            Log.e("VoxAudio", "playChunk: ${e.message}", e)
        }
    }

    /**
     * Bridge terminou de enviar chunks (audio_done). Se a fila esvaziar,
     * dispara onDone. As chamadas de audio_done sem chunks (resposta muito
     * curta) encerram o ciclo normalmente.
     */
    fun finishStream() {
        if (!streaming) return
        streaming = false
        fimStream = true
        if (player == null) {
            onDone()
        }
    }

    private fun tocarProximo() {
        if (player != null) return // ainda tocando um chunk
        val tf = filaChunks.removeFirstOrNull() ?: run {
            // Fila vazia: se o stream já terminou, o ciclo está completo.
            if (fimStream && !streaming) onDone()
            return
        }
        player = MediaPlayer().apply {
            setDataSource(tf.absolutePath)
            setOnCompletionListener {
                tf.delete()
                limparPlayer()
                tocarProximo()
            }
            setOnErrorListener { mp, what, extra ->
                tf.delete()
                Log.e("VoxAudio", "MediaPlayer error: what=$what extra=$extra")
                limparPlayer()
                tocarProximo()
                true
            }
            prepare()
            start()
        }
    }

    private fun limparPlayer() {
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
    }

    fun cancelStream() {
        streaming = false
        fimStream = false
        stop()
        filaChunks.forEach { it.delete() }
        filaChunks.clear()
    }

    /**
     * Para a reprodução imediatamente e limpa a fila (barge-in: usuário
     * interrompeu a fala). Não dispara onDone — quem cancelou já sabe que o
     * ciclo acabou.
     */
    fun stop() {
        limparPlayer()
        filaChunks.forEach { it.delete() }
        filaChunks.clear()
    }

    fun shutdown() {
        cancelStream()
        stop()
    }
}
