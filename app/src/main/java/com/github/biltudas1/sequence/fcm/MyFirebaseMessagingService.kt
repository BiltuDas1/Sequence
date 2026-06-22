package com.github.biltudas1.sequence.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.IncomingCallActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val CHANNEL_ID = "voice_call_v3"
        const val MISSED_CALL_CHANNEL_ID = "missed_call"
        const val ACTION_ACCEPT = "com.github.biltudas1.sequence.ACCEPT_CALL"
        const val ACTION_REJECT = "com.github.biltudas1.sequence.REJECT_CALL"
        const val CALL_NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val callChannel = NotificationChannel(CHANNEL_ID, "Voice Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming voice calls"
                setSound(soundUri, attributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            val missedChannel = NotificationChannel(MISSED_CALL_CHANNEL_ID, "Missed Calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Missed call alerts"
            }
            notificationManager.createNotificationChannel(missedChannel)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received. Data: ${message.data}")
        
        val roomId = message.data["roomId"]
        val action = message.data["action"]
        val callerName = message.data["callerName"] ?: "Someone"
        
        if (roomId != null) {
            when (action) {
                "cancel" -> {
                    // Only show missed call if the other person cancelled
                    handleCallCancelled(callerName)
                }
                null, "start" -> {
                    // Only show incoming call if there's no action (new call)
                    wakeUpScreen()
                    showIncomingCallNotification(roomId, callerName)
                }
                else -> {
                    // Ignore other actions like 'reject' or 'end' to prevent ghost notifications
                    Log.d("FCM", "Ignoring action: $action")
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(CALL_NOTIFICATION_ID)
                }
            }
        }
    }

    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Sequence:CallWakeLock"
            )
            wakeLock.acquire(5000)
        } catch (e: Exception) {
            Log.e("FCM", "Error waking screen", e)
        }
    }

    private fun showIncomingCallNotification(roomId: String, callerName: String) {
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("roomId", roomId)
            putExtra("callerName", callerName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = ACTION_ACCEPT
            putExtra("roomId", roomId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(this, 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val rejectIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = ACTION_REJECT
            putExtra("roomId", roomId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(this, 2, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming Voice Call")
            .setContentText("Incoming call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .setSound(soundUri)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_logo, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_logo, "Reject", rejectPendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
    }

    private fun handleCallCancelled(callerName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(CALL_NOTIFICATION_ID)

        val cancelIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("cancel", true)
        }
        startActivity(cancelIntent)

        val missedCallNotification = NotificationCompat.Builder(this, MISSED_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Missed Call")
            .setContentText("You missed a call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), missedCallNotification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val dataStoreManager = DataStoreManager(applicationContext)
        val authService = AuthService(OkHttpClient(), dataStoreManager)
        scope.launch {
            val serverConfig = dataStoreManager.serverConfigFlow.firstOrNull()
            val accessToken = dataStoreManager.accessTokenFlow.firstOrNull()
            if (serverConfig != null && serverConfig.isValid() && accessToken != null) {
                authService.updateFcmToken(serverConfig, accessToken, token)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
