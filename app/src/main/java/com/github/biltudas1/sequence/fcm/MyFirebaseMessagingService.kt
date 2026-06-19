package com.github.biltudas1.sequence.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.biltudas1.sequence.MainActivity
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.AuthService
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
        const val CHANNEL_ID = "voice_call"
        const val ACTION_ACCEPT = "com.github.biltudas1.sequence.ACCEPT_CALL"
        const val ACTION_REJECT = "com.github.biltudas1.sequence.REJECT_CALL"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Voice Calls"
            val descriptionText = "Notifications for incoming voice calls"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token received: $token")
        
        val dataStoreManager = DataStoreManager(applicationContext)
        val authService = AuthService(OkHttpClient(), dataStoreManager)
        
        scope.launch {
            val serverConfig = dataStoreManager.serverConfigFlow.firstOrNull()
            val accessToken = dataStoreManager.accessTokenFlow.firstOrNull()
            
            if (serverConfig != null && serverConfig.isValid() && accessToken != null) {
                Log.d("FCM", "Updating token on server...")
                val result = authService.updateFcmToken(serverConfig, accessToken, token)
                Log.d("FCM", "Token update result: ${result.isSuccess}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received from: ${message.from}")
        
        val roomId = message.data["roomId"]
        val callerName = message.data["callerName"] ?: message.notification?.title ?: "Unknown"
        
        if (roomId != null) {
            showNotification(roomId, callerName)
        }
    }

    private fun showNotification(roomId: String, callerName: String) {
        Log.d("FCM", "Showing notification for room: $roomId from: $callerName")
        
        // Main intent (clicking the notification body)
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("roomId", roomId)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action
        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = ACTION_ACCEPT
            putExtra("roomId", roomId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            this, 1, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reject Action
        val rejectIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = ACTION_REJECT
            putExtra("roomId", roomId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            this, 2, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming Voice Call")
            .setContentText("Call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(mainPendingIntent, true)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_logo, "Accept", acceptPendingIntent) // Replace ic_logo with a proper icon if available
            .addAction(R.drawable.ic_logo, "Reject", rejectPendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
