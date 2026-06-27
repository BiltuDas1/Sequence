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
            
            // Do NOT set MODE_RINGTONE here as it can sometimes grab exclusive control
            // and fail on certain headset configurations.
            // Instead, we use the Ringtone object's internal stream management.

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val newRingtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            
            // USAGE_NOTIFICATION_RINGTONE is designed by Android to play 
            // on BOTH the speaker and the headphones automatically.
            newRingtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                newRingtone?.isLooping = true
            }
            
            ringtone = newRingtone
            ringtone?.play()
            
            // After starting play, we can try to nudge the speaker on.
            // This is the "standard" way to get dual-output for ringtones.
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true

            Timber.d("CallRingtonePlayer: Started")
        } catch (e: Exception) {
            Timber.e(e, "CallRingtonePlayer: Failed to start")
        }
    }

    fun stop(context: Context) {
        try {
            ringtone?.stop()
            ringtone = null
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            Timber.d("CallRingtonePlayer: Stopped")
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
