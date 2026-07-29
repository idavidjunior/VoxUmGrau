package com.voxumgrau.app

import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class VoxAudioPlayer(private val onDone: () -> Unit = {}) {

    private var player: MediaPlayer? = null

    fun play(base64Audio: String) {
        stop()
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File.createTempFile("vox_", ".mp3")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    tempFile.delete()
                    onDone()
                }
                setOnErrorListener { mp, what, extra ->
                    tempFile.delete()
                    Log.e("VoxAudio", "MediaPlayer error: what=$what extra=$extra")
                    onDone()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("VoxAudio", "play: ${e.message}", e)
            onDone()
        }
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
        stop()
    }
}
