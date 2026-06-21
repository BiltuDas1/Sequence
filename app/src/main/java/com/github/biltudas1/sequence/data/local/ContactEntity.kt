package com.github.biltudas1.sequence.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val email: String,
    val first_name: String?,
    val last_name: String?,
    val created_at: String
)
