package com.github.biltudas1.sequence.data

import android.content.Context
import com.github.biltudas1.sequence.data.local.AppDatabase
import com.github.biltudas1.sequence.data.local.CallLogEntity
import com.github.biltudas1.sequence.util.AppLogger
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

class CallLogRepository(context: Context) {
    private val callLogDao = AppDatabase.getDatabase(context).callLogDao()

    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()

    suspend fun insertCallLog(callLog: CallLogEntity) {
        Timber.d("insertCallLog: ${callLog.type} - ${AppLogger.redact(callLog.email)}")
        callLogDao.insertCallLog(callLog)
    }

    suspend fun deleteCallLog(callLog: CallLogEntity) {
        Timber.d("deleteCallLog: id=${callLog.id}")
        callLogDao.deleteCallLog(callLog)
    }

    suspend fun clearAll() {
        Timber.i("clearAll: Deleting all call logs")
        callLogDao.deleteAllCallLogs()
    }

    suspend fun updateDuration(roomId: String, duration: Long) {
        Timber.d("updateDuration: Room=$roomId, Duration=${duration}ms")
        val log = callLogDao.getCallLogByRoomId(roomId)
        if (log != null) {
            callLogDao.updateCallLog(log.copy(duration = duration))
        } else {
            Timber.w("updateDuration: Log not found for room $roomId")
        }
    }

    suspend fun markAsMissed(roomId: String) {
        Timber.d("markAsMissed: Room=$roomId")
        val log = callLogDao.getCallLogByRoomId(roomId)
        if (log != null && log.type == "INCOMING") {
            Timber.i("markAsMissed: Updating log to MISSED")
            callLogDao.updateCallLog(log.copy(type = "MISSED"))
        } else if (log == null) {
            Timber.w("markAsMissed: Log not found for room $roomId")
        }
    }
}
