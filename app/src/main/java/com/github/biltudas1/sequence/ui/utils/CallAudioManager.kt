package com.github.biltudas1.sequence.ui.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.github.biltudas1.sequence.R

class CallAudioManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun startWaiting() {
        play(R.raw.ringwaiting, loop = true)
    }

    fun startRingback() {
        play(R.raw.ringback, loop = true)
    }

    private fun play(resId: Int, loop: Boolean) {
        stopAny()
        try {
            mediaPlayer = MediaPlayer.create(context, resId).apply {
                isLooping = loop
                start()
            }
        } catch (e: Exception) {
            Log.e("CallAudioManager", "Error playing audio resource: $resId", e)
        }
    }

    fun stopAny() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                it.release()
            }
        }
        mediaPlayer = null
    }
}
