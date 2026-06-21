package com.github.biltudas1.sequence.data

import android.content.Context
import com.github.biltudas1.sequence.data.local.AppDatabase
import com.github.biltudas1.sequence.data.local.ContactEntity
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.data.remote.model.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

class ContactRepository(context: Context, private val authService: AuthService) {
    private val database = AppDatabase.getDatabase(context)
    private val contactDao = database.contactDao()

    val contactsFlow: Flow<List<UserData>> = contactDao.getAllContacts().map { entities ->
        entities.map { it.toUserData() }
    }

    suspend fun refreshContacts(serverConfig: ServerConfig, accessToken: String): Result<Unit> {
        val result = authService.getContacts(serverConfig, accessToken)
        return if (result.isSuccess) {
            val remoteContacts = result.getOrNull()?.data ?: emptyList()
            contactDao.deleteAll()
            contactDao.insertContacts(remoteContacts.map { it.toEntity() })
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun addContact(serverConfig: ServerConfig, accessToken: String, email: String): Result<Unit> {
        val result = authService.addContact(serverConfig, accessToken, email)
        return if (result.isSuccess) {
            val newUser = result.getOrNull()?.data
            if (newUser != null) {
                contactDao.insertContact(newUser.toEntity())
            }
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun removeContact(serverConfig: ServerConfig, accessToken: String, email: String): Result<Unit> {
        val result = authService.removeContact(serverConfig, accessToken, email)
        return if (result.isSuccess) {
            contactDao.deleteContactByEmail(email)
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun clearLocalData() {
        contactDao.deleteAll()
    }

    private fun ContactEntity.toUserData() = UserData(
        id = id,
        email = email,
        first_name = first_name,
        last_name = last_name,
        created_at = created_at
    )

    private fun UserData.toEntity() = ContactEntity(
        id = id,
        email = email,
        first_name = first_name,
        last_name = last_name,
        created_at = created_at
    )
}
