package com.aayaan.agclient.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, PACKET, TUNNEL, RELAY, UPSTREAM, DIAG, FEEDBACK
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object Logger {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val _logsFlow = MutableSharedFlow<LogEntry>(replay = 150, extraBufferCapacity = 300)
    val logsFlow: SharedFlow<LogEntry> = _logsFlow.asSharedFlow()
    private val logHistory = java.util.Collections.synchronizedList(mutableListOf<LogEntry>())

    private fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        synchronized(logHistory) {
            logHistory.add(entry)
            if (logHistory.size > 1000) {
                logHistory.removeAt(0)
            }
        }
        _logsFlow.tryEmit(entry)
        android.util.Log.println(
            when (level) {
                LogLevel.DEBUG -> android.util.Log.DEBUG
                LogLevel.INFO -> android.util.Log.INFO
                LogLevel.WARN -> android.util.Log.WARN
                LogLevel.ERROR -> android.util.Log.ERROR
                LogLevel.PACKET, LogLevel.TUNNEL, LogLevel.RELAY, LogLevel.UPSTREAM -> android.util.Log.VERBOSE
                LogLevel.DIAG, LogLevel.FEEDBACK -> android.util.Log.INFO
            },
            tag,
            message
        )
    }

    fun getAllLogsText(): String {
        return synchronized(logHistory) {
            if (logHistory.isEmpty()) {
                "No logs recorded yet."
            } else {
                logHistory.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message}" }
            }
        }
    }

    fun clearLogs() {
        synchronized(logHistory) {
            logHistory.clear()
        }
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

    fun tunnel(message: String) = log(LogLevel.TUNNEL, "TUNNEL", message)
    fun relay(message: String) = log(LogLevel.RELAY, "RELAY", message)
    fun upstream(message: String) = log(LogLevel.UPSTREAM, "UPSTREAM", message)
    fun diag(message: String) = log(LogLevel.DIAG, "DIAG", message)
    fun feedback(message: String) = log(LogLevel.FEEDBACK, "USER_FEEDBACK", message)

    fun buildComprehensiveReport(diagnosticSummary: String = "", userFeedback: String = ""): String {
        return buildString {
            append("====================================================\n")
            append("      AG CLIENT v2.9.9 CONNECTION REPORT     \n")
            append("====================================================\n")
            append("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})\n")
            if (userFeedback.isNotBlank()) {
                append("User Feedback: $userFeedback\n")
            }
            append("----------------------------------------------------\n")
            if (diagnosticSummary.isNotBlank()) {
                append("DIAGNOSTICS SUMMARY:\n")
                append(diagnosticSummary)
                append("\n----------------------------------------------------\n")
            }
            append("RECENT SYSTEM & PACKET LOGS:\n")
            append(getAllLogsText())
            append("\n====================================================\n")
        }
    }
}
