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

    fun redact(input: String?): String {
        if (input == null) return "null"
        if (input.isBlank()) return ""
        return if (input.length > 8) {
            "${input.take(3)}...${input.takeLast(3)}"
        } else {
            "***"
        }
    }

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        logFile = File(logDir, "app_logs.txt")

        // Load existing logs from file into memory
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
        
        // Add to in-memory list
        synchronized(_logs) {
            if (_logs.size >= MAX_LOGS) {
                _logs.removeAt(0)
            }
            _logs.add(logEntry)
        }
        _newLogFlow.tryEmit(logEntry)

        // Write to file
        synchronized(this) {
            try {
                logFile?.let { file ->
                    // Basic rotation: if file > 2MB, clear it or rotate.
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
