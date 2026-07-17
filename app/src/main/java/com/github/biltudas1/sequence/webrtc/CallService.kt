package com.github.biltudas1.sequence.webrtc

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.github.biltudas1.sequence.MainActivity
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.media.AudioOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground service that maintains the call connection and shows the ongoing call notification.
 */
class CallService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID = "ongoing_call_v2"
        private const val NOTIFICATION_ID = 101
        
        const val ACTION_MUTE = "ACTION_MUTE"
        const val ACTION_SPEAKER = "ACTION_SPEAKER"
        const val ACTION_END_CALL = "ACTION_END_CALL"

        fun start(context: Context, roomId: String, name: String, email: String, isExternal: Boolean, serverUrl: String) {
            val intent = Intent(context, CallService::class.java).apply {
                putExtra("room_id", roomId)
                putExtra("name", name)
                putExtra("email", email)
                putExtra("is_external", isExternal)
                putExtra("server_url", serverUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }

        fun updateNotification(context: Context) {
            val intent = Intent(context, CallService::class.java).apply { action = "UPDATE" }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        when (action) {
            ACTION_MUTE -> {
                CallManager.toggleMute(this)
            }
            ACTION_SPEAKER -> {
                CallManager.toggleAudioOutput(this)
            }
            ACTION_END_CALL -> {
                handleEndCall()
            }
            "UPDATE" -> {
                updateNotification()
            }
            else -> initialForegroundStart(intent)
        }
        
        return START_NOT_STICKY
    }

    private fun initialForegroundStart(intent: Intent?) {
        val name = intent?.getStringExtra("name") ?: "Active Call"
        createNotificationChannel()
        val notification = createNotification(name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val name = CallManager.activeCallerName.ifEmpty { "Active Call" }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(name))
    }

    private fun handleEndCall() {
        val roomId = CallManager.activeRoomId
        val serverUrl = CallManager.activeServerUrl
        
        CallManager.terminateCall(this)
        
        if (roomId != null) {
            serviceScope.launch {
                try {
                    val dataStoreManager = DataStoreManager.getInstance(applicationContext)
                    val token = dataStoreManager.accessTokenFlow.first()
                    val config = dataStoreManager.serverConfigFlow.first()
                    
                    if ((config != null) && (token != null) && (config.endpoint == serverUrl)) {
                        val authService = com.github.biltudas1.sequence.data.remote.AuthService(
                            okhttp3.OkHttpClient(), 
                            dataStoreManager
                        )
                        authService.endVoiceCall(config, token, roomId)
                    }
                } catch (e: Exception) {
                    Timber.e("CallService: Error notifying server about end call")
                } finally {
                    stopSelf()
                }
            }
        } else {
            stopSelf()
        }
    }

    private fun createNotification(name: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, CallService::class.java).apply { action = ACTION_MUTE }
        val mutePendingIntent = PendingIntent.getService(
            this, 1, muteIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speakerIntent = Intent(this, CallService::class.java).apply { action = ACTION_SPEAKER }
        val speakerPendingIntent = PendingIntent.getService(
            this, 2, speakerIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, CallService::class.java).apply { action = ACTION_END_CALL }
        val endCallPendingIntent = PendingIntent.getService(
            this, 3, endCallIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isMuted = CallManager.isMuted.value
        val audioOutput = CallManager.audioOutput.value

        val person = Person.Builder()
            .setName(name)
            .setImportant(true)
            .build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText("Ongoing Call")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setColor(0xFF6200EE.toInt())
            .setColorized(true)
            .setContentIntent(contentPendingIntent)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person,
                    endCallPendingIntent
                )
            )
            .addAction(
                when (audioOutput) {
                    AudioOutput.SPEAKER -> R.drawable.ic_speaker
                    AudioOutput.HEADSET -> R.drawable.ic_headset
                    else -> R.drawable.ic_earpiece
                },
                when (audioOutput) {
                    AudioOutput.SPEAKER -> "Speaker"
                    AudioOutput.HEADSET -> "Headset"
                    else -> "Earpiece"
                },
                speakerPendingIntent
            )
            .addAction(
                if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on,
                if (isMuted) "Unmute" else "Mute", 
                mutePendingIntent
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ongoing Voice Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notifications for active voice calls"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
