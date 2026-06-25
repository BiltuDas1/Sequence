package com.github.biltudas1.sequence.util

import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val MAX_LOGS = 1000
    private val _logs = Collections.synchronizedList(LinkedList<String>())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    val logs: List<String> get() = _logs.toList()

    fun addLog(priority: Int, tag: String?, message: String, t: Throwable?) {
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
    }

    class InMemoryTree : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, tag, message, t)
            addLog(priority, tag, message, t)
        }
    }
}
