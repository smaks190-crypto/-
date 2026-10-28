package com.example.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel(val label: String, val priority: Int) {
    DEBUG("DEBUG", 1),
    INFO("INFO", 2),
    WARNING("WARNING", 3),
    WARN("WARNING", 3),
    ERROR("ERROR", 4),
    FATAL("FATAL", 5);

    val canonical: LogLevel
        get() = if (this == WARN) WARNING else this
}

enum class NetworkCallStatus(val label: String) {
    SUCCESS("SUCCESS"),
    FALLBACK("FALLBACK"),
    ERROR("ERROR")
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val epochMillis: Long = 0L,
    val level: LogLevel,
    val source: String,
    val message: String,
    val details: String? = null,
    val stackTrace: String? = null,
    val operationId: String? = null,
    val isPreviousSessionCrash: Boolean = false
) {
    val tag: String get() = source
}

data class NetworkTraceEntry(
    val id: Long,
    val operationId: String,
    val timestamp: String,
    val epochMillis: Long = 0L,
    val operationType: String,
    val modelName: String,
    val durationMs: Long,
    val status: NetworkCallStatus,
    val httpCode: Int? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val attemptNumber: Int = 1,
    val totalModelsInChain: Int = 1,
    val details: String? = null
)

data class DbOperationEntry(
    val id: Long,
    val operationId: String,
    val timestamp: String,
    val epochMillis: Long = 0L,
    val operationName: String,
    val tableName: String,
    val durationMs: Long,
    val isSuccess: Boolean = true,
    val details: String? = null,
    val errorMessage: String? = null
)

data class DatabaseDiagnosticsSnapshot(
    val isInitialized: Boolean = false,
    val databaseName: String = "budget_database",
    val schemaVersion: Int = 5,
    val isOpen: Boolean = false,
    val activeBudgetId: String = "—",
    val tableCounts: Map<String, Int> = emptyMap(),
    val lastUpdatedTimestamp: String = "—",
    val lastError: String? = null
)

data class SystemDiagnosticsState(
    val lifecycleState: String = "RELEASE",
    val isVoiceRecording: Boolean = false,
    val isAnalyzingVoice: Boolean = false,
    val isAiAuditRunning: Boolean = false,
    val isGeneratingDavidReaction: Boolean = false,
    val activeBudgetId: String = "—",
    val activeModalName: String = "None"
)

/**
 * No-Op реализация GlobalConsoleLogger для Release APK (Release Source Set).
 * Не хранит данные в памяти, не перехватывает системные потоки и полностью вырезается R8.
 */
object GlobalConsoleLogger {
    const val MAX_LOGS = 0
    const val MAX_ERRORS = 0
    const val MAX_NETWORK_TRACES = 0
    const val MAX_DB_OPERATIONS = 0

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _errors = MutableStateFlow<List<LogEntry>>(emptyList())
    val errors: StateFlow<List<LogEntry>> = _errors.asStateFlow()

    private val _networkTraces = MutableStateFlow<List<NetworkTraceEntry>>(emptyList())
    val networkTraces: StateFlow<List<NetworkTraceEntry>> = _networkTraces.asStateFlow()

    private val _dbOperations = MutableStateFlow<List<DbOperationEntry>>(emptyList())
    val dbOperations: StateFlow<List<DbOperationEntry>> = _dbOperations.asStateFlow()

    private val _dbSnapshot = MutableStateFlow(DatabaseDiagnosticsSnapshot())
    val dbSnapshot: StateFlow<DatabaseDiagnosticsSnapshot> = _dbSnapshot.asStateFlow()

    private val _systemState = MutableStateFlow(SystemDiagnosticsState())
    val systemState: StateFlow<SystemDiagnosticsState> = _systemState.asStateFlow()

    private val _unreadErrorCount = MutableStateFlow(0)
    val unreadErrorCount: StateFlow<Int> = _unreadErrorCount.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    fun newOperationId(prefix: String = "OP"): String = ""
    fun maskSensitive(raw: String?): String = ""
    fun setPaused(paused: Boolean) {}
    fun markErrorsAsRead() {}
    fun setupUncaughtExceptionHandler(context: Context? = null) {}
    fun clearPersistedCrash() {}

    fun d(tag: String, message: String, details: String? = null, operationId: String? = null) {}
    fun i(tag: String, message: String, details: String? = null, operationId: String? = null) {}
    fun w(
        tag: String,
        message: String,
        details: String? = null,
        operationId: String? = null,
        throwable: Throwable? = null
    ) {}
    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        details: String? = null,
        operationId: String? = null
    ) {}
    fun f(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        details: String? = null,
        operationId: String? = null
    ) {}
    fun log(
        level: LogLevel,
        source: String,
        message: String,
        details: String? = null,
        throwable: Throwable? = null,
        operationId: String? = null
    ) {}

    fun recordNetworkCall(
        operationType: String,
        modelName: String,
        durationMs: Long,
        status: NetworkCallStatus,
        httpCode: Int? = null,
        errorType: String? = null,
        errorMessage: String? = null,
        attemptNumber: Int = 1,
        totalModelsInChain: Int = 1,
        details: String? = null,
        operationId: String? = null
    ) {}

    fun recordDbOperation(
        operationName: String,
        tableName: String,
        durationMs: Long,
        isSuccess: Boolean = true,
        details: String? = null,
        errorMessage: String? = null,
        operationId: String? = null
    ) {}

    fun updateDatabaseSnapshot(
        isInitialized: Boolean = true,
        isOpen: Boolean = true,
        schemaVersion: Int = 5,
        activeBudgetId: String? = null,
        tableCounts: Map<String, Int>,
        lastError: String? = null
    ) {}

    fun updateSystemState(
        lifecycleState: String? = null,
        isVoiceRecording: Boolean? = null,
        isAnalyzingVoice: Boolean? = null,
        isAiAuditRunning: Boolean? = null,
        isGeneratingDavidReaction: Boolean? = null,
        activeBudgetId: String? = null,
        activeModalName: String? = null
    ) {}

    fun onLifecycleEvent(eventName: String) {}
    fun clear() {}
    fun clearErrors() {}
    fun clearNetworkTraces() {}
    fun clearDbOperations() {}

    fun filterLogs(
        entries: List<LogEntry> = emptyList(),
        query: String = "",
        levelFilter: LogLevel? = null,
        sourceFilter: String = "ALL"
    ): List<LogEntry> = emptyList()

    fun getUptimeMillis(): Long = 0L
    fun formatUptime(uptimeMs: Long = 0L): String = ""
    fun buildDiagnosticReport(
        appVersionName: String = "",
        appVersionCode: Int = 0,
        buildType: String = ""
    ): String = ""

    fun buildAiDiagnosticPrompt(
        targetError: LogEntry? = null,
        appVersionName: String = "",
        appVersionCode: Int = 0,
        buildType: String = ""
    ): String = ""
}
