package com.voxumgrau.app

import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream

class VoxAudioPlayer(private val onDone: () -> Unit = {}) {

    private var player: MediaPlayer? = null
    private var streaming = false
    private var streamTempFile: File? = null

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
                    onDone()
                }
                setOnErrorListener { mp, what, extra ->
                    tempFile?.delete()
                    Log.e("VoxAudio", "MediaPlayer error: what=$what extra=$extra")
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
        streamTempFile = File.createTempFile("vox_stream_", ".mp3")
    }

    fun playChunk(base64Chunk: String) {
        if (!streaming) {
            Log.w("VoxAudio", "playChunk chamado sem startStream ativo")
            return
        }
        val tf = streamTempFile ?: return
        try {
            val audioBytes = Base64.decode(base64Chunk, Base64.DEFAULT)
            FileOutputStream(tf, true).use { it.write(audioBytes) }
        } catch (e: Exception) {
            Log.e("VoxAudio", "playChunk: ${e.message}", e)
        }
    }

    fun finishStream() {
        if (!streaming) return
        streaming = false
        val tf = streamTempFile
        streamTempFile = null
        if (tf != null && tf.exists() && tf.length() > 0) {
            stop()
            player = MediaPlayer().apply {
                setDataSource(tf.absolutePath)
                setOnCompletionListener {
                    tf.delete()
                    onDone()
                }
                setOnErrorListener { mp, what, extra ->
                    tf.delete()
                    Log.e("VoxAudio", "MediaPlayer error: what=$what extra=$extra")
                    onDone()
                    true
                }
                prepare()
                start()
            }
        } else {
            tf?.delete()
            onDone()
        }
    }

    fun cancelStream() {
        streaming = false
        streamTempFile?.delete()
        streamTempFile = null
    }

    fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            Log.e("VoxAudio", "stop: ${e.message}", e)
        }
        player = null
    }

    fun shutdown() {
        cancelStream()
        stop()
    }
}
