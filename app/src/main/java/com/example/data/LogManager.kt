package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR, DEBUG
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

object LogManager {
    private const val MAX_LOGS = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val buffer = ConcurrentLinkedDeque<LogEntry>()

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    fun log(level: LogLevel, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            message = message
        )
        buffer.addLast(entry)
        while (buffer.size > MAX_LOGS) {
            buffer.pollFirst()
        }
        _logsFlow.value = buffer.toList()
    }

    fun info(message: String) = log(LogLevel.INFO, message)
    fun success(message: String) = log(LogLevel.SUCCESS, message)
    fun warn(message: String) = log(LogLevel.WARN, message)
    fun error(message: String) = log(LogLevel.ERROR, message)
    fun debug(message: String) = log(LogLevel.DEBUG, message)

    fun clear() {
        buffer.clear()
        _logsFlow.value = emptyList()
    }

    fun getAllAsString(): String {
        return buffer.joinToString(separator = "\n") {
            "[${it.timestamp}] [${it.level}] ${it.message}"
        }
    }
}
