package com.github.biltudas1.sequence.data

import android.content.Context
import com.github.biltudas1.sequence.data.local.AppDatabase
import com.github.biltudas1.sequence.data.local.CallLogEntity
import kotlinx.coroutines.flow.Flow

class CallLogRepository(context: Context) {
    private val callLogDao = AppDatabase.getDatabase(context).callLogDao()

    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()

    suspend fun insertCallLog(callLog: CallLogEntity) {
        callLogDao.insertCallLog(callLog)
    }

    suspend fun deleteCallLog(callLog: CallLogEntity) {
        callLogDao.deleteCallLog(callLog)
    }

    suspend fun clearAll() {
        callLogDao.deleteAllCallLogs()
    }

    suspend fun updateDuration(roomId: String, duration: Long) {
        val log = callLogDao.getCallLogByRoomId(roomId)
        if (log != null) {
            callLogDao.updateCallLog(log.copy(duration = duration))
        }
    }

    suspend fun markAsMissed(roomId: String) {
        val log = callLogDao.getCallLogByRoomId(roomId)
        if (log != null && log.type == "INCOMING") {
            callLogDao.updateCallLog(log.copy(type = "MISSED"))
        }
    }
}
