package com.github.biltudas1.sequence.data.repository

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
        Timber.d("insertCallLog: ${callLog.type} - ${AppLogger.redact(callLog.email)}, creationTime=${callLog.creationTime}")
        
        val normalizedLog = normalizeTimestamp(callLog)

        if (normalizedLog.creationTime != null) {
            val existing = callLogDao.getCallLogByCreationTime(normalizedLog.creationTime)
            if (existing != null) {
                Timber.d("insertCallLog: Found existing call log, updating.")
                callLogDao.updateCallLog(normalizedLog.copy(id = existing.id))
                return
            }
        }
        callLogDao.insertCallLog(normalizedLog)
    }

    private fun normalizeTimestamp(callLog: CallLogEntity): CallLogEntity {
        return if (callLog.creationTime != null) {
            // Converts seconds to milliseconds if needed
            val msTimestamp = if (callLog.creationTime < 10_000_000_000L) {
                callLog.creationTime * 1000
            } else {
                callLog.creationTime
            }
            callLog.copy(timestamp = msTimestamp)
        } else {
            callLog
        }
    }

    suspend fun deleteCallLog(callLog: CallLogEntity) {
        Timber.d("deleteCallLog: id=${callLog.id}")
        callLogDao.deleteCallLog(callLog)
    }

    suspend fun clearAll() {
        Timber.i("clearAll: Deleting all call logs")
        callLogDao.deleteAllCallLogs()
    }

    suspend fun updateDuration(roomId: String, duration: Long, creationTime: Long? = null) {
        Timber.d("updateDuration: Room=$roomId, Duration=${duration}ms, creationTime=$creationTime")
        val log = findCallLog(roomId, creationTime)

        if (log != null) {
            callLogDao.updateCallLog(log.copy(duration = duration))
        } else {
            Timber.w("updateDuration: Log not found")
        }
    }

    suspend fun markAsMissed(roomId: String, creationTime: Long? = null, callerName: String? = null, callerEmail: String? = null) {
        Timber.d("markAsMissed: Room=$roomId, creationTime=$creationTime")
        val log = findCallLog(roomId, creationTime)

        if (log != null && log.type == "INCOMING") {
            Timber.i("markAsMissed: Updating log to MISSED")
            callLogDao.updateCallLog(log.copy(type = "MISSED"))
        } else if (log == null && callerEmail != null) {
            Timber.i("markAsMissed: Log not found, creating new MISSED log")
            val finalTimestamp = creationTime?.let {
                if (it < 10_000_000_000L) it * 1000 else it
            } ?: System.currentTimeMillis()
            
            callLogDao.insertCallLog(
                CallLogEntity(
                    email = callerEmail,
                    name = callerName,
                    type = "MISSED",
                    timestamp = finalTimestamp,
                    roomId = roomId,
                    creationTime = creationTime
                )
            )
        } else if (log == null) {
            Timber.w("markAsMissed: Log not found and no caller info to create one")
        }
    }

    private suspend fun findCallLog(roomId: String, creationTime: Long?): CallLogEntity? {
        return if (creationTime != null) {
            callLogDao.getCallLogByCreationTime(creationTime)
        } else {
            callLogDao.getCallLogByRoomId(roomId)
        }
    }
}
