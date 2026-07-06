package com.github.biltudas1.sequence.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val name: String?,
    val type: String, // "INCOMING", "OUTGOING", "MISSED"
    val timestamp: Long,
    val duration: Long? = null,
    val roomId: String? = null,
    val creationTime: Long? = null
)
