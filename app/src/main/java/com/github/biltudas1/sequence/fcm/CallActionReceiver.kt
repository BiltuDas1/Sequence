package com.github.biltudas1.sequence.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.biltudas1.sequence.MainActivity
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val roomId = intent.getStringExtra("roomId") ?: return
        
        // Dismiss notification using the consistent ID
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(MyFirebaseMessagingService.CALL_NOTIFICATION_ID)

        if (action == MyFirebaseMessagingService.ACTION_ACCEPT) {
            Log.d("CallActionReceiver", "Call Accepted: $roomId")
            
            // Mark room as accepted to stop background busy signals
            MyFirebaseMessagingService.markRoomAccepted(roomId)
            
            // 1. Create intent to launch MainActivity
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("roomId", roomId)
                putExtra("callerName", intent.getStringExtra("callerName"))
                putExtra("callerEmail", intent.getStringExtra("callerEmail"))
            }
            
            // 2. Wrap it in a PendingIntent to let the system handle the transition better
            // especially if the user is currently in another app (like the Phone app)
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e("CallActionReceiver", "Failed to start activity", e)
            }
        } else if (action == MyFirebaseMessagingService.ACTION_REJECT) {
            Log.d("CallActionReceiver", "Call Rejected: $roomId")
            
            val dataStoreManager = DataStoreManager.getInstance(context)
            val authService = AuthService(OkHttpClient(), dataStoreManager)
            val repository = com.github.biltudas1.sequence.data.CallLogRepository(context)
            
            CoroutineScope(Dispatchers.IO).launch {
                repository.markAsMissed(roomId)
                val serverConfig = dataStoreManager.serverConfigFlow.firstOrNull()
                val accessToken = dataStoreManager.accessTokenFlow.firstOrNull()
                if (serverConfig != null && accessToken != null) {
                    authService.endVoiceCall(serverConfig, accessToken, roomId)
                }
            }
        }
    }
}
