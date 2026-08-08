package com.example.utils

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object GlobalConsoleLogger {
    private const val MAX_LOGS = 200
    private var nextId = 1L

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat by lazy { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    fun setupUncaughtExceptionHandler() {
        if (!BuildConfig.DEBUG) return
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("ERROR", "Uncaught Exception in thread ${thread.name}: ${throwable.localizedMessage}\n${throwable.stackTraceToString()}")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    @JvmStatic
    fun d(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(tag, message)
        addEntry(LogLevel.DEBUG, tag, message)
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.i(tag, message)
        addEntry(LogLevel.INFO, tag, message)
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.w(tag, message)
        addEntry(LogLevel.WARN, tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.e(tag, message)
        addEntry(LogLevel.ERROR, tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable?) {
        if (!BuildConfig.DEBUG) return
        val fullMsg = if (throwable != null) "$message\n${throwable.localizedMessage}\n${throwable.stackTraceToString()}" else message
        Log.e(tag, fullMsg, throwable)
        addEntry(LogLevel.ERROR, tag, fullMsg)
    }

    fun clear() {
        if (!BuildConfig.DEBUG) return
        _logs.value = emptyList()
    }

    @Synchronized
    private fun addEntry(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            id = nextId++,
            timestamp = dateFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _logs.value = current
    }
}
