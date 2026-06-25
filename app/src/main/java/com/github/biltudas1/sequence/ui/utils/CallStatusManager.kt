package com.github.biltudas1.sequence.ui.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import timber.log.Timber

class CallStatusManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun isUserOnAnotherCall(): Boolean {
        // 1. Check Cellular Call State
        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        @Suppress("DEPRECATION")
        val telephonyCallState = if (hasPhoneStatePermission) {
            telephonyManager.callState
        } else {
            Timber.w("isUserOnAnotherCall: READ_PHONE_STATE permission not granted")
            TelephonyManager.CALL_STATE_IDLE
        }
        
        Timber.v("isUserOnAnotherCall: Telephony state: $telephonyCallState")
        if (telephonyCallState != TelephonyManager.CALL_STATE_IDLE) {
            Timber.i("isUserOnAnotherCall: User is on a cellular call")
            return true
        }

        // 2. Check Audio Mode (VoIP like WhatsApp, Telegram, or Sequence itself)
        // MODE_IN_CALL (1) is for cellular, MODE_IN_COMMUNICATION (3) is for VoIP
        val mode = audioManager.mode
        Timber.v("isUserOnAnotherCall: Audio mode: $mode")
        
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            Timber.i("isUserOnAnotherCall: User is on a VoIP/Audio call (Mode: $mode)")
            return true
        }

        return false
    }

    fun requestCallAudioFocus(): Boolean {
        Timber.d("requestCallAudioFocus: Requesting transient exclusive focus")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { Timber.d("AudioFocus Change: $it") }
                .build()
            val result = audioManager.requestAudioFocus(focusRequest)
            Timber.i("requestCallAudioFocus result: ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "GRANTED" else "FAILED"}")
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { Timber.d("AudioFocus Change (Legacy): $it") },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
            Timber.i("requestCallAudioFocus result (Legacy): ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "GRANTED" else "FAILED"}")
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
}
