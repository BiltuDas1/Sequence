package com.github.biltudas1.sequence.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Delete
    suspend fun deleteCallLog(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAllCallLogs()

    @Query("SELECT * FROM call_logs WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCallLogByRoomId(roomId: String): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE creationTime = :creationTime LIMIT 1")
    suspend fun getCallLogByCreationTime(creationTime: Long): CallLogEntity?
    
    @Update
    suspend fun updateCallLog(callLog: CallLogEntity)
}
