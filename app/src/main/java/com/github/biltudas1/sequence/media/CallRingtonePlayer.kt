package com.github.biltudas1.sequence.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import timber.log.Timber

/**
 * Handles incoming call ringtone playback with dual-output support (Speaker + Headset).
 */
object CallRingtonePlayer {
    private var ringtone: Ringtone? = null

    @Synchronized
    fun start(context: Context) {
        if (ringtone?.isPlaying == true) return
        
        if (ringtone != null) {
            try {
                ringtone?.play()
            } catch (e: Exception) {
                Timber.e(e, "CallRingtonePlayer: Failed to resume play")
            }
            return
        }
        
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasHeadset = devices.any { isHeadset(it) }

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val newRingtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            
            // Setting USAGE_NOTIFICATION_RINGTONE is key for system-level dual routing
            newRingtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                newRingtone?.isLooping = true
            }
            
            ringtone = newRingtone

            if (hasHeadset) {
                Timber.d("CallRingtonePlayer: Headset detected. Enabling dual routing.")
                // Set mode to RINGTONE
                audioManager.mode = AudioManager.MODE_RINGTONE
                
                // Forcing speaker ON while in MODE_RINGTONE often triggers dual output
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                
                // For Bluetooth specifically, we explicitly open SCO
                val hasBluetooth = devices.any { isBluetooth(it) }
                if (hasBluetooth) {
                    Timber.d("CallRingtonePlayer: Starting SCO for Bluetooth ringtone")
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                }
            } else {
                Timber.d("CallRingtonePlayer: No headset, standard speaker output")
                audioManager.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }

            ringtone?.play()
            Timber.d("CallRingtonePlayer: Started")
        } catch (e: Exception) {
            Timber.e(e, "CallRingtonePlayer: Failed to start playback")
        }
    }

    private fun isHeadset(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                } else false
            }
        }
    }

    private fun isBluetooth(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                } else false
            }
        }
    }

    @Synchronized
    fun stop(context: Context) {
        try {
            val r = ringtone
            ringtone = null
            r?.stop()
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            
            audioManager.mode = AudioManager.MODE_NORMAL
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            
            Timber.d("CallRingtonePlayer: Stopped and reset audio state")
        } catch (e: Exception) {
            Timber.e(e, "CallRingtonePlayer: Error during stop/cleanup")
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
