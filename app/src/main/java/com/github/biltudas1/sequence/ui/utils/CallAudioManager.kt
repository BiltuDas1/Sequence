package com.github.biltudas1.sequence.ui.utils

import android.content.Context
import android.media.MediaPlayer
import com.github.biltudas1.sequence.R
import timber.log.Timber

class CallAudioManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun startWaiting() {
        Timber.d("startWaiting: Playing ringwaiting")
        play(R.raw.ringwaiting, loop = true)
    }

    fun startRingback() {
        Timber.d("startRingback: Playing ringback")
        play(R.raw.ringback, loop = true)
    }

    fun startBusy() {
        Timber.d("startBusy: Playing ringbusy")
        play(R.raw.ringbusy, loop = true)
    }

    private fun play(resId: Int, loop: Boolean) {
        stopAny()
        try {
            mediaPlayer = MediaPlayer.create(context, resId).apply {
                isLooping = loop
                start()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio resource: $resId")
        }
    }

    fun stopAny() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    Timber.d("stopAny: Stopping current media player")
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
