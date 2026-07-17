package com.github.biltudas1.sequence.media

import android.content.Context
import android.os.PowerManager
import timber.log.Timber

/**
 * Manages the proximity sensor to turn the screen off when the phone is near the ear.
 * This is typically used during a call when the earpiece is active.
 */
class ProximityManager(context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null

    init {
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Sequence:ProximityWakeLock"
            )
        } else {
            Timber.w("ProximityManager: PROXIMITY_SCREEN_OFF_WAKE_LOCK is not supported on this device")
        }
    }

    /**
     * Enables the proximity sensor behavior.
     */
    fun enable() {
        proximityWakeLock?.let {
            if (!it.isHeld) {
                Timber.d("ProximityManager: Enabling proximity sensor")
                it.acquire()
            }
        }
    }

    /**
     * Disables the proximity sensor behavior.
     */
    fun disable() {
        proximityWakeLock?.let {
            if (it.isHeld) {
                Timber.d("ProximityManager: Disabling proximity sensor")
                it.release()
            }
        }
    }

    /**
     * Cleans up resources.
     */
    fun cleanup() {
        disable()
        proximityWakeLock = null
    }
}
