package com.github.biltudas1.sequence.data.repository

import android.content.Context
import com.github.biltudas1.sequence.data.local.AppDatabase
import com.github.biltudas1.sequence.data.local.ContactEntity
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.data.remote.model.UserData
import com.github.biltudas1.sequence.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class ContactRepository(context: Context, private val authService: AuthService) {
    private val database = AppDatabase.getDatabase(context)
    private val contactDao = database.contactDao()

    val contactsFlow: Flow<List<UserData>> = contactDao.getAllContacts().map { entities ->
        entities.map { it.toUserData() }
    }

    suspend fun refreshContacts(serverConfig: ServerConfig, accessToken: String): Result<Unit> {
        Timber.d("refreshContacts: Fetching from remote")
        val result = authService.getContacts(serverConfig, accessToken)
        return if (result.isSuccess) {
            val remoteContacts = result.getOrNull()?.data ?: emptyList()
            Timber.i("refreshContacts: Success. Found ${remoteContacts.size} contacts")
            contactDao.deleteAll()
            contactDao.insertContacts(remoteContacts.map { it.toEntity() })
            Result.success(Unit)
        } else {
            val error = result.exceptionOrNull()
            Timber.e(error, "refreshContacts: Failed")
            Result.failure(error ?: Exception("Unknown error"))
        }
    }

    suspend fun addContact(serverConfig: ServerConfig, accessToken: String, email: String): Result<Unit> {
        Timber.d("addContact: ${AppLogger.redact(email)}")
        val result = authService.addContact(serverConfig, accessToken, email)
        return if (result.isSuccess) {
            val newUser = result.getOrNull()?.data
            if (newUser != null) {
                Timber.i("addContact: Success for ${AppLogger.redact(email)}")
                contactDao.insertContact(newUser.toEntity())
            }
            Result.success(Unit)
        } else {
            val error = result.exceptionOrNull()
            Timber.e(error, "addContact: Failed for ${AppLogger.redact(email)}")
            Result.failure(error ?: Exception("Unknown error"))
        }
    }

    suspend fun removeContact(serverConfig: ServerConfig, accessToken: String, email: String): Result<Unit> {
        Timber.d("removeContact: ${AppLogger.redact(email)}")
        val result = authService.removeContact(serverConfig, accessToken, email)
        return if (result.isSuccess) {
            Timber.i("removeContact: Success for ${AppLogger.redact(email)}")
            contactDao.deleteContactByEmail(email)
            Result.success(Unit)
        } else {
            val error = result.exceptionOrNull()
            Timber.e(error, "removeContact: Failed for ${AppLogger.redact(email)}")
            Result.failure(error ?: Exception("Unknown error"))
        }
    }

    suspend fun clearLocalData() {
        Timber.i("clearLocalData: Deleting all local contacts")
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
