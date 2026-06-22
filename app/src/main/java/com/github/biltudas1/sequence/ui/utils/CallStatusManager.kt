package com.github.biltudas1.sequence.ui.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

class CallStatusManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun isUserOnAnotherCall(): Boolean {
        // 1. Check Cellular Call State
        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val telephonyCallState = if (hasPhoneStatePermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.callState
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.callState
            }
        } else {
            Log.w("CallStatusManager", "READ_PHONE_STATE permission not granted")
            TelephonyManager.CALL_STATE_IDLE
        }
        
        Log.d("CallStatusManager", "Telephony state: $telephonyCallState")
        if (telephonyCallState != TelephonyManager.CALL_STATE_IDLE) {
            return true
        }

        // 2. Check Audio Mode (VoIP like WhatsApp, Telegram, or Sequence itself)
        // MODE_IN_CALL (1) is for cellular, MODE_IN_COMMUNICATION (3) is for VoIP
        val mode = audioManager.mode
        Log.d("CallStatusManager", "Audio mode: $mode")
        
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            return true
        }

        return false
    }

    fun requestCallAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
}
