package com.github.biltudas1.sequence.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Manages audio routing and call-related sounds (ringback, busy, etc.)
 * Handles automatic headset detection and provides a unified interface for audio output switching.
 */
class CallAudioManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var onDeviceChanged: ((AudioOutput, Boolean) -> Unit)? = null

    var isHeadsetConnected = false
        private set

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            updateHeadsetStatus()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            updateHeadsetStatus()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        updateHeadsetStatus()
    }

    /**
     * Set a listener to be notified when the audio device status changes (e.g., headset plugged in).
     */
    fun setOnDeviceChangedListener(listener: (AudioOutput, Boolean) -> Unit) {
        this.onDeviceChanged = listener
        // Trigger initial state
        listener(if (isHeadsetConnected) AudioOutput.HEADSET else AudioOutput.EARPIECE, isHeadsetConnected)
    }

    private fun updateHeadsetStatus() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasHeadset = devices.any { isHeadset(it) }

        if (hasHeadset != isHeadsetConnected) {
            isHeadsetConnected = hasHeadset
            Timber.d("CallAudioManager: Headset status changed (isConnected=$isHeadsetConnected)")
            
            // Auto-switch logic: if headset is connected, default to it. 
            // If disconnected, default back to earpiece.
            val newOutput = if (isHeadsetConnected) AudioOutput.HEADSET else AudioOutput.EARPIECE
            onDeviceChanged?.invoke(newOutput, isHeadsetConnected)
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

    /**
     * Sets the active audio output device.
     */
    fun setAudioOutput(output: AudioOutput) {
        Timber.i("CallAudioManager: Setting audio output to $output")
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyAudioOutputS(output)
            } else {
                applyAudioOutputLegacy(output)
            }
        } catch (e: Exception) {
            Timber.e(e, "CallAudioManager: Error setting audio output to $output")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyAudioOutputS(output: AudioOutput) {
        audioManager.clearCommunicationDevice()
        val devices = audioManager.availableCommunicationDevices
        
        val deviceToSet = when (output) {
            AudioOutput.SPEAKER -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            AudioOutput.EARPIECE -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            AudioOutput.HEADSET -> {
                devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                    ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                    ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
                    ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
                    ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            }
        }
        
        if (deviceToSet != null) {
            val result = audioManager.setCommunicationDevice(deviceToSet)
            Timber.d("CallAudioManager: setCommunicationDevice(${deviceToSet.type}) result: $result")
        } else {
            Timber.w("CallAudioManager: No suitable device found for output: $output")
        }
    }

    @Suppress("DEPRECATION")
    private fun applyAudioOutputLegacy(output: AudioOutput) {
        when (output) {
            AudioOutput.SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            AudioOutput.EARPIECE -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            AudioOutput.HEADSET -> {
                audioManager.isSpeakerphoneOn = false
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val hasBluetooth = devices.any { 
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP 
                }
                if (hasBluetooth) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                } else {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            }
        }
    }

    @Synchronized
    fun startWaiting() = play(com.github.biltudas1.sequence.R.raw.ringwaiting)

    @Synchronized
    fun startRingback() = play(com.github.biltudas1.sequence.R.raw.ringback)

    @Synchronized
    fun startBusy() = play(com.github.biltudas1.sequence.R.raw.ringbusy)

    private fun play(resId: Int) {
        stopAny()
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                val afd = context.resources.openRawResourceFd(resId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                prepareAsync()
                setOnPreparedListener { it.start() }
            }
        } catch (e: Exception) {
            Timber.e(e, "CallAudioManager: Error playing sound resource $resId")
        }
    }

    /**
     * Stops any currently playing sound.
     */
    @Synchronized
    fun stopAny() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
        mediaPlayer = null
    }

    /**
     * Cleans up resources and resets audio mode. Should be called when the call ends.
     */
    fun cleanup() {
        Timber.d("CallAudioManager: Cleaning up resources")
        stopAny()
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        audioManager.mode = AudioManager.MODE_NORMAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
        }
    }
}
