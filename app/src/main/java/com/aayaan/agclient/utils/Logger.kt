package com.aayaan.agclient.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, PACKET
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object Logger {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val _logsFlow = MutableSharedFlow<LogEntry>(replay = 100, extraBufferCapacity = 200)
    val logsFlow: SharedFlow<LogEntry> = _logsFlow.asSharedFlow()

    private fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        _logsFlow.tryEmit(entry)
        android.util.Log.println(
            when (level) {
                LogLevel.DEBUG -> android.util.Log.DEBUG
                LogLevel.INFO -> android.util.Log.INFO
                LogLevel.WARN -> android.util.Log.WARN
                LogLevel.ERROR -> android.util.Log.ERROR
                LogLevel.PACKET -> android.util.Log.VERBOSE
            },
            tag,
            message
        )
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${android.util.Log.getStackTraceString(throwable)}" else message
        log(LogLevel.ERROR, tag, msg)
    }
    fun packet(direction: String, packetName: String, details: String = "") {
        val msg = if (details.isNotEmpty()) "[$direction] $packetName - $details" else "[$direction] $packetName"
        log(LogLevel.PACKET, "Packet", msg)
    }
}
