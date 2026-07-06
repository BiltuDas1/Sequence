package com.github.biltudas1.sequence.ui.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import timber.log.Timber

object CallRingtonePlayer {
    private var ringtone: Ringtone? = null

    @Synchronized
    fun start(context: Context) {
        if (ringtone != null) {
            if (ringtone?.isPlaying == false) {
                try {
                    ringtone?.play()
                } catch (e: Exception) {
                    Timber.e(e, "CallRingtonePlayer: Failed to resume play")
                }
            }
            return
        }
        
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val newRingtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            
            newRingtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                newRingtone?.isLooping = true
            }
            
            ringtone = newRingtone
            ringtone?.play()
            
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true

            Timber.d("CallRingtonePlayer: Started")
        } catch (e: Exception) {
            Timber.e(e, "CallRingtonePlayer: Failed to start")
        }
    }

    @Synchronized
    fun stop(context: Context) {
        try {
            val r = ringtone
            ringtone = null // Clear reference first
            r?.stop()
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            
            Timber.d("CallRingtonePlayer: Stopped and speaker disabled")
        } catch (e: Exception) {
            Timber.e(e, "CallRingtonePlayer: Error stopping")
        }
    }

    
    fun ensureSpeaker(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Timber.e(e, "CallRingtonePlayer: Error ensuring speaker")
        }
    }
}
