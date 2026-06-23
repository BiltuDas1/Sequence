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
import com.github.biltudas1.sequence.data.remote.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class CallService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "active_call_channel"
        const val NOTIFICATION_ID = 888
        
        const val ACTION_MUTE = "com.github.biltudas1.sequence.MUTE"
        const val ACTION_SPEAKER = "com.github.biltudas1.sequence.SPEAKER"
        const val ACTION_END_CALL = "com.github.biltudas1.sequence.END_CALL"
        
        fun start(context: Context, roomId: String, name: String, email: String, isExternal: Boolean, serverUrl: String) {
            val intent = Intent(context, CallService::class.java).apply {
                putExtra("roomId", roomId)
                putExtra("name", name)
                putExtra("email", email)
                putExtra("isExternal", isExternal)
                putExtra("serverUrl", serverUrl)
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
            val intent = Intent(context, CallService::class.java).apply {
                action = "UPDATE"
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        when (action) {
            ACTION_MUTE -> {
                CallManager.toggleMute()
                updateNotification()
            }
            ACTION_SPEAKER -> {
                CallManager.toggleSpeaker()
                updateNotification()
            }
            ACTION_END_CALL -> {
                handleEndCall()
            }
            "UPDATE" -> {
                updateNotification()
            }
            else -> {
                // Initial start
                updateNotification()
            }
        }
        
        return START_NOT_STICKY
    }

    private fun handleEndCall() {
        val roomId = CallManager.activeRoomId ?: return
        val context = this
        serviceScope.launch {
            val dataStoreManager = DataStoreManager(context)
            val authService = AuthService(OkHttpClient(), dataStoreManager)
            
            val actualConfig = try {
                dataStoreManager.serverConfigFlow.first()
            } catch (e: Exception) { null }
            
            val token = try {
                dataStoreManager.accessTokenFlow.first()
            } catch (e: Exception) { null }

            if (actualConfig != null && token != null) {
                authService.endVoiceCall(actualConfig, token, roomId)
            }
            
            launch(Dispatchers.Main) {
                CallManager.terminateCall(context)
            }
        }
    }

    private fun updateNotification() {
        val roomId = CallManager.activeRoomId ?: return
        val name = CallManager.activeCallerName
        val email = CallManager.activeCallerEmail
        val isExternal = CallManager.isExternalCall
        val serverUrl = CallManager.activeServerUrl

        val notification = createNotification(roomId, name, email, isExternal, serverUrl)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(roomId: String, name: String, email: String, isExternal: Boolean, serverUrl: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("roomId", roomId)
            putExtra("callerName", name)
            putExtra("callerEmail", email)
            putExtra("isExternal", isExternal.toString())
            putExtra("serverUrl", serverUrl)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, CallService::class.java).apply { action = ACTION_MUTE }
        val mutePendingIntent = PendingIntent.getService(this, 1, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val speakerIntent = Intent(this, CallService::class.java).apply { action = ACTION_SPEAKER }
        val speakerPendingIntent = PendingIntent.getService(this, 2, speakerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val endCallIntent = Intent(this, CallService::class.java).apply { action = ACTION_END_CALL }
        val endCallPendingIntent = PendingIntent.getService(this, 3, endCallIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val isMuted = CallManager.isMuted.value
        val isSpeakerOn = CallManager.isSpeakerOn.value

        val person = Person.Builder()
            .setName(name)
            .setImportant(true)
            .build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(name)
            .setContentText("Ongoing Call")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF2E7D32.toInt()) // Green color like in the image
            .setColorized(true)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person,
                    endCallPendingIntent
                )
            )
            .addAction(
                0,
                if (isSpeakerOn) "Earpiece" else "Speaker", 
                speakerPendingIntent
            )
            .addAction(
                0,
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
                "Active Call",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing call status"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
