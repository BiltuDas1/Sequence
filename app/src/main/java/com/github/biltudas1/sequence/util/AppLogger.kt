package com.github.biltudas1.sequence.util

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val MAX_LOGS = 1000
    private const val MAX_FILE_SIZE = 2 * 1024 * 1024 // 2 MB
    private val _logs = Collections.synchronizedList(LinkedList<String>())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    val logs: List<String> get() = synchronized(_logs) { _logs.toList() }

    private val _newLogFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val newLogFlow = _newLogFlow.asSharedFlow()

    private var logFile: File? = null

    fun getLogFile(): File? = logFile

    /**
     * Redacts an email address, keeping the domain visible for debugging.
     * Example: biltu@gmail.com -> b***u@gmail.com
     */
    fun redactEmail(email: String?): String {
        if (email == null) return "null"
        if (email.isBlank()) return ""
        val parts = email.split("@")
        if (parts.size != 2) return redactSecret(email) // Fallback if not a valid email
        
        val name = parts[0]
        val domain = parts[1]
        return if (name.length > 2) {
            "${name.take(1)}***${name.takeLast(1)}@$domain"
        } else {
            "***@$domain"
        }
    }

    /**
     * Redacts sensitive secrets like passwords or tokens.
     * Example: secret_token_12345 -> secr***2345
     */
    fun redactSecret(secret: String?): String {
        if (secret == null) return "null"
        if (secret.isBlank()) return ""
        return if (secret.length > 12) {
            "${secret.take(4)}***${secret.takeLast(4)}"
        } else {
            "***"
        }
    }

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        logFile = File(logDir, "app_logs.txt")

        synchronized(_logs) {
            try {
                if (logFile?.exists() == true) {
                    val lines = logFile?.readLines() ?: emptyList()
                    _logs.clear()
                    _logs.addAll(lines.takeLast(MAX_LOGS))
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun addLog(priority: Int, tag: String?, message: String, t: Throwable?) {
        val priorityStr = when (priority) {
            2 -> "V"
            3 -> "D"
            4 -> "I"
            5 -> "W"
            6 -> "E"
            7 -> "A"
            else -> "?"
        }
        
        val timestamp = dateFormat.format(Date())
        val logEntry = "$timestamp [$priorityStr] ${tag ?: "App"}: $message${t?.let { "\n${it.stackTraceToString()}" } ?: ""}"
        
        synchronized(_logs) {
            if (_logs.size >= MAX_LOGS) {
                _logs.removeAt(0)
            }
            _logs.add(logEntry)
        }
        _newLogFlow.tryEmit(logEntry)

        synchronized(this) {
            try {
                logFile?.let { file ->
                    if (file.exists() && file.length() > MAX_FILE_SIZE) {
                        file.writeText("[Log Rotated at ${dateFormat.format(Date())}]\n")
                    }
                    file.appendText("$logEntry\n")
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun clearLogs() {
        synchronized(_logs) {
            _logs.clear()
        }
        synchronized(this) {
            try {
                logFile?.let {
                    if (it.exists()) it.delete()
                    it.writeText("[Logs Cleared at ${dateFormat.format(Date())}]\n")
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        _newLogFlow.tryEmit("[Logs Cleared]")
    }

    class InMemoryTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            addLog(priority, tag, message, t)
        }
    }
}
