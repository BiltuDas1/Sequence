package com.github.biltudas1.sequence.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY email ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE email = :email")
    suspend fun deleteContactByEmail(email: String): Int

    @Query("DELETE FROM contacts")
    suspend fun deleteAll(): Int
}
