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
        
        // Dismiss notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)

        if (action == "com.github.biltudas1.sequence.ACCEPT_CALL") {
            Log.d("CallActionReceiver", "Call Accepted: $roomId")
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("roomId", roomId)
            }
            context.startActivity(launchIntent)
        } else if (action == "com.github.biltudas1.sequence.REJECT_CALL") {
            Log.d("CallActionReceiver", "Call Rejected: $roomId")
            
            val dataStoreManager = DataStoreManager(context)
            val authService = AuthService(OkHttpClient(), dataStoreManager)
            
            CoroutineScope(Dispatchers.IO).launch {
                val serverConfig = dataStoreManager.serverConfigFlow.firstOrNull()
                val accessToken = dataStoreManager.accessTokenFlow.firstOrNull()
                if (serverConfig != null && accessToken != null) {
                    authService.endVoiceCall(serverConfig, accessToken, roomId)
                }
            }
        }
    }
}
