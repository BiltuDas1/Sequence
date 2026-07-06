package com.github.biltudas1.sequence.ui.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.github.biltudas1.sequence.R
import timber.log.Timber

class CallAudioManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    @Synchronized
    fun startWaiting() {
        Timber.d("startWaiting: Playing ringwaiting")
        play(R.raw.ringwaiting, loop = true)
    }

    @Synchronized
    fun startRingback() {
        Timber.d("startRingback: Playing ringback")
        play(R.raw.ringback, loop = true)
    }

    @Synchronized
    fun startBusy() {
        Timber.d("startBusy: Playing ringbusy")
        play(R.raw.ringbusy, loop = true)
    }

    private fun play(resId: Int, loop: Boolean) {
        stopAnyInternal()
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val mp = MediaPlayer()
            mediaPlayer = mp // Assign immediately so stopAny can find it
            
            mp.apply {
                setAudioAttributes(audioAttributes)
                val afd = context.resources.openRawResourceFd(resId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = loop
                prepareAsync()
                setOnPreparedListener { 
                    try {
                        it.start() 
                    } catch (e: Exception) {
                        Timber.e(e, "Error starting MediaPlayer after prepare")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting up audio resource: $resId")
            stopAnyInternal()
        }
    }

    @Synchronized
    fun stopAny() {
        stopAnyInternal()
    }

    private fun stopAnyInternal() {
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

