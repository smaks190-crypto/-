package com.example.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

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
    val epochMillis: Long = System.currentTimeMillis(),
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
    val epochMillis: Long = System.currentTimeMillis(),
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
    val epochMillis: Long = System.currentTimeMillis(),
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
    val lifecycleState: String = "INITIALIZED",
    val isVoiceRecording: Boolean = false,
    val isAnalyzingVoice: Boolean = false,
    val isAiAuditRunning: Boolean = false,
    val isGeneratingDavidReaction: Boolean = false,
    val activeBudgetId: String = "—",
    val activeModalName: String = "None"
)

/**
 * Централизованная потокобезопасная система диагностики и логирования Developer Console (Debug Source Set).
 * Включает:
 * - Кольцевые буферы логов, ошибок, сетевых/ИИ вызовов и операций БД Room;
 * - Автоматическую маскировку чувствительных данных (API-ключи Gemini, Bearer-токены, PIN-коды, пароли, карты);
 * - Перехват необработанных исключений с сохранением последнего фатального краша между сессиями и делегированием стандартному обработчику Android;
 * - Генерацию структурированного диагностического отчёта и промпта «Скопировать для ИИ».
 */
object GlobalConsoleLogger {
    const val MAX_LOGS = 500
    const val MAX_ERRORS = 150
    const val MAX_NETWORK_TRACES = 150
    const val MAX_DB_OPERATIONS = 150

    private const val PREFS_NAME = "debug_console_prefs"
    private const val KEY_LAST_CRASH_MESSAGE = "last_crash_message"
    private const val KEY_LAST_CRASH_STACK = "last_crash_stack"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"
    private const val KEY_LAST_CRASH_THREAD = "last_crash_thread"
    private const val KEY_LAST_CRASH_OP_ID = "last_crash_op_id"

    private val lock = Any()
    private val idCounter = AtomicLong(1L)
    private val opCounter = AtomicLong(1L)
    private val appStartMillis = System.currentTimeMillis()

    @Volatile
    private var isCrashHandlerInstalled = false

    @Volatile
    private var appContextRef: Context? = null

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

    // Regex patterns for sensitive data masking
    private val geminiKeyRegex = Regex("""AIza[0-9A-Za-z_\-]{16,}""")
    private val bearerTokenRegex = Regex("""(?i)(Bearer\s+)[A-Za-z0-9._\-+/=]{8,}""")
    private val headerApiKeyRegex = Regex("""(?i)(x-goog-api-key\s*[:=]\s*)(?!AIza\*\*\*\[MASKED_API_KEY\])([^\s,;"]+)""")
    private val queryKeyParamRegex = Regex("""(?i)(?<![A-Za-z0-9_\-])(["']?(?:api[_-]?key|key|token|secret|password|pin|pin[_-]?hash|access[_-]?token)["']?\s*[:=]\s*["']?)(?!\[MASKED|AIza\*\*\*\[MASKED)([^"'\s&,;}{]+)(["']?)""")
    private val base64AudioBlobRegex = Regex("""("data"\s*:\s*")[A-Za-z0-9+/=\r\n]{80,}(")""")
    private val cardNumberRegex = Regex("""\b(?:\d[ -]*?){13,19}\b""")

    private fun formatTime(epochMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(epochMillis))
    }

    fun newOperationId(prefix: String = "OP"): String {
        val cleanPrefix = prefix.trim().uppercase(Locale.US).ifBlank { "OP" }
        return "$cleanPrefix-${opCounter.getAndIncrement().toString().padStart(4, '0')}"
    }

    fun maskSensitive(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var masked = raw
        masked = geminiKeyRegex.replace(masked, "AIza***[MASKED_API_KEY]")
        masked = bearerTokenRegex.replace(masked) { "${it.groupValues[1]}[MASKED_TOKEN]" }
        masked = headerApiKeyRegex.replace(masked) { "${it.groupValues[1]}[MASKED_API_KEY]" }
        masked = queryKeyParamRegex.replace(masked) {
            "${it.groupValues[1]}[MASKED_SECRET]${it.groupValues[3]}"
        }
        masked = base64AudioBlobRegex.replace(masked) {
            "${it.groupValues[1]}[MASKED_BINARY_PAYLOAD]${it.groupValues[2]}"
        }
        masked = cardNumberRegex.replace(masked) { match ->
            val digitsOnly = match.value.filter { it.isDigit() }
            if (digitsOnly.length in 13..19) {
                "**** **** **** ${digitsOnly.takeLast(4)}"
            } else {
                match.value
            }
        }
        return masked
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun markErrorsAsRead() {
        _unreadErrorCount.value = 0
    }

    fun setupUncaughtExceptionHandler(context: Context? = null) {
        val appContext = context?.applicationContext
        if (appContext != null) {
            appContextRef = appContext
            restorePreviousSessionCrashIfAny(appContext)
        }

        if (isCrashHandlerInstalled) return
        synchronized(lock) {
            if (isCrashHandlerInstalled) return
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                handleUncaughtException(thread, throwable, defaultHandler)
            }
            isCrashHandlerInstalled = true
        }
    }

    internal fun handleUncaughtException(
        thread: Thread,
        throwable: Throwable,
        delegateHandler: Thread.UncaughtExceptionHandler?
    ) {
        try {
            val opId = newOperationId("CRASH")
            val summary = maskSensitive(
                "Uncaught ${throwable.javaClass.simpleName} in thread [${thread.name}]: ${throwable.localizedMessage ?: throwable.message ?: "No message"}"
            )
            val stack = maskSensitive(throwable.stackTraceToString())
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

            appContextRef?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_LAST_CRASH_MESSAGE, summary)
                ?.putString(KEY_LAST_CRASH_STACK, stack)
                ?.putString(KEY_LAST_CRASH_TIME, timeStr)
                ?.putString(KEY_LAST_CRASH_THREAD, thread.name)
                ?.putString(KEY_LAST_CRASH_OP_ID, opId)
                ?.commit()

            addEntryInternal(
                level = LogLevel.FATAL,
                source = "CRASH",
                message = summary,
                details = "Поток: ${thread.name} | Время: $timeStr",
                stackTrace = stack,
                operationId = opId,
                ignorePause = true
            )
        } catch (_: Throwable) {
            // Никогда не блокируем цепочку завершения процесса при фатальном сбое
        } finally {
            delegateHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun restorePreviousSessionCrashIfAny(appContext: Context) {
        try {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val msg = prefs.getString(KEY_LAST_CRASH_MESSAGE, null)
            val stack = prefs.getString(KEY_LAST_CRASH_STACK, null)
            val timeStr = prefs.getString(KEY_LAST_CRASH_TIME, null)
            val threadName = prefs.getString(KEY_LAST_CRASH_THREAD, "main")
            val opId = prefs.getString(KEY_LAST_CRASH_OP_ID, "CRASH-PREV")

            if (!msg.isNullOrBlank()) {
                synchronized(lock) {
                    val alreadyRestored = _errors.value.any { it.isPreviousSessionCrash && it.message == msg }
                    if (!alreadyRestored) {
                        val now = System.currentTimeMillis()
                        val entry = LogEntry(
                            id = idCounter.getAndIncrement(),
                            timestamp = timeStr?.takeLast(12) ?: formatTime(now),
                            epochMillis = now,
                            level = LogLevel.FATAL,
                            source = "CRASH",
                            message = "[Прошлая сессия] $msg",
                            details = "Сохранено из предыдущего запуска | Время сбоя: ${timeStr ?: "—"} | Поток: $threadName",
                            stackTrace = stack,
                            operationId = opId,
                            isPreviousSessionCrash = true
                        )
                        _logs.value = (_logs.value + entry).takeLast(MAX_LOGS)
                        _errors.value = (_errors.value + entry).takeLast(MAX_ERRORS)
                        _unreadErrorCount.value = _unreadErrorCount.value + 1
                    }
                }
            }

            // Также проверяем legacy crash_prefs на случай миграции со старой консоли
            val legacyPrefs = appContext.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
            val legacyCrash = legacyPrefs.getString("last_crash", null)
            if (!legacyCrash.isNullOrBlank()) {
                legacyPrefs.edit().remove("last_crash").apply()
                if (msg.isNullOrBlank()) {
                    val maskedLegacy = maskSensitive(legacyCrash)
                    val firstLine = maskedLegacy.lineSequence().firstOrNull() ?: "Fatal Crash"
                    val now = System.currentTimeMillis()
                    val entry = LogEntry(
                        id = idCounter.getAndIncrement(),
                        timestamp = formatTime(now),
                        epochMillis = now,
                        level = LogLevel.FATAL,
                        source = "CRASH",
                        message = "[Прошлая сессия] $firstLine",
                        details = "Восстановлено из crash_prefs",
                        stackTrace = maskedLegacy,
                        operationId = "CRASH-LEGACY",
                        isPreviousSessionCrash = true
                    )
                    synchronized(lock) {
                        _logs.value = (_logs.value + entry).takeLast(MAX_LOGS)
                        _errors.value = (_errors.value + entry).takeLast(MAX_ERRORS)
                        _unreadErrorCount.value = _unreadErrorCount.value + 1
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun clearPersistedCrash() {
        try {
            appContextRef?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.clear()
                ?.apply()
        } catch (_: Throwable) {}
    }

    fun d(
        tag: String,
        message: String,
        details: String? = null,
        operationId: String? = null
    ) {
        val maskedMsg = maskSensitive(message)
        safeLogcat(Log.DEBUG, tag, maskedMsg, null)
        addEntryInternal(
            level = LogLevel.DEBUG,
            source = tag,
            message = maskedMsg,
            details = details?.let { maskSensitive(it) },
            stackTrace = null,
            operationId = operationId
        )
    }

    fun i(
        tag: String,
        message: String,
        details: String? = null,
        operationId: String? = null
    ) {
        val maskedMsg = maskSensitive(message)
        safeLogcat(Log.INFO, tag, maskedMsg, null)
        addEntryInternal(
            level = LogLevel.INFO,
            source = tag,
            message = maskedMsg,
            details = details?.let { maskSensitive(it) },
            stackTrace = null,
            operationId = operationId
        )
    }

    fun w(
        tag: String,
        message: String,
        details: String? = null,
        operationId: String? = null,
        throwable: Throwable? = null
    ) {
        val maskedMsg = maskSensitive(message)
        safeLogcat(Log.WARN, tag, maskedMsg, throwable)
        addEntryInternal(
            level = LogLevel.WARNING,
            source = tag,
            message = maskedMsg,
            details = details?.let { maskSensitive(it) },
            stackTrace = throwable?.let { maskSensitive(it.stackTraceToString()) },
            operationId = operationId
        )
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        details: String? = null,
        operationId: String? = null
    ) {
        val maskedMsg = maskSensitive(message)
        val computedStack = throwable?.let { maskSensitive(it.stackTraceToString()) }
        val computedDetails = buildString {
            if (!details.isNullOrBlank()) append(maskSensitive(details))
            if (throwable != null) {
                if (isNotEmpty()) append(" | ")
                append("${throwable.javaClass.simpleName}: ${maskSensitive(throwable.localizedMessage ?: throwable.message ?: "")}")
            }
        }.ifBlank { null }

        safeLogcat(Log.ERROR, tag, maskedMsg, throwable)
        addEntryInternal(
            level = LogLevel.ERROR,
            source = tag,
            message = maskedMsg,
            details = computedDetails,
            stackTrace = computedStack,
            operationId = operationId,
            ignorePause = true
        )
    }

    fun f(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        details: String? = null,
        operationId: String? = null
    ) {
        val maskedMsg = maskSensitive(message)
        val computedStack = throwable?.let { maskSensitive(it.stackTraceToString()) }
        val computedDetails = buildString {
            if (!details.isNullOrBlank()) append(maskSensitive(details))
            if (throwable != null) {
                if (isNotEmpty()) append(" | ")
                append("${throwable.javaClass.simpleName}: ${maskSensitive(throwable.localizedMessage ?: throwable.message ?: "")}")
            }
        }.ifBlank { null }

        safeLogcat(Log.ASSERT, tag, maskedMsg, throwable)
        addEntryInternal(
            level = LogLevel.FATAL,
            source = tag,
            message = maskedMsg,
            details = computedDetails,
            stackTrace = computedStack,
            operationId = operationId,
            ignorePause = true
        )
    }

    fun log(
        level: LogLevel,
        source: String,
        message: String,
        details: String? = null,
        throwable: Throwable? = null,
        operationId: String? = null
    ) {
        when (level.canonical) {
            LogLevel.DEBUG -> d(source, message, details, operationId)
            LogLevel.INFO -> i(source, message, details, operationId)
            LogLevel.WARNING, LogLevel.WARN -> w(source, message, details, operationId, throwable)
            LogLevel.ERROR -> e(source, message, throwable, details, operationId)
            LogLevel.FATAL -> f(source, message, throwable, details, operationId)
        }
    }

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
    ) {
        val now = System.currentTimeMillis()
        val opId = operationId ?: newOperationId("AI")
        val maskedError = errorMessage?.let { maskSensitive(it) }
        val maskedDetails = details?.let { maskSensitive(it) }

        val trace = NetworkTraceEntry(
            id = idCounter.getAndIncrement(),
            operationId = opId,
            timestamp = formatTime(now),
            epochMillis = now,
            operationType = maskSensitive(operationType),
            modelName = modelName.ifBlank { "unknown-model" },
            durationMs = durationMs.coerceAtLeast(0L),
            status = status,
            httpCode = httpCode,
            errorType = errorType,
            errorMessage = maskedError,
            attemptNumber = attemptNumber,
            totalModelsInChain = totalModelsInChain,
            details = maskedDetails
        )

        synchronized(lock) {
            val updated = (_networkTraces.value + trace).takeLast(MAX_NETWORK_TRACES)
            _networkTraces.value = updated
        }

        val summary = buildString {
            append("[$operationType] model=$modelName • ${durationMs}ms • ${status.label}")
            if (httpCode != null) append(" (HTTP $httpCode)")
            if (totalModelsInChain > 1) append(" [попытка $attemptNumber/$totalModelsInChain]")
        }

        when (status) {
            NetworkCallStatus.SUCCESS -> i(
                tag = "GEMINI",
                message = summary,
                details = maskedDetails,
                operationId = opId
            )
            NetworkCallStatus.FALLBACK -> w(
                tag = "GEMINI",
                message = "$summary → переключение на резервную модель",
                details = listOfNotNull(maskedError, maskedDetails).joinToString(" | ").ifBlank { null },
                operationId = opId
            )
            NetworkCallStatus.ERROR -> e(
                tag = "GEMINI",
                message = summary,
                details = listOfNotNull(errorType, maskedError, maskedDetails).joinToString(" | ").ifBlank { null },
                operationId = opId
            )
        }
    }

    fun recordDbOperation(
        operationName: String,
        tableName: String,
        durationMs: Long,
        isSuccess: Boolean = true,
        details: String? = null,
        errorMessage: String? = null,
        operationId: String? = null
    ) {
        val now = System.currentTimeMillis()
        val opId = operationId ?: newOperationId("DB")
        val maskedDetails = details?.let { maskSensitive(it) }
        val maskedError = errorMessage?.let { maskSensitive(it) }

        val entry = DbOperationEntry(
            id = idCounter.getAndIncrement(),
            operationId = opId,
            timestamp = formatTime(now),
            epochMillis = now,
            operationName = operationName,
            tableName = tableName,
            durationMs = durationMs.coerceAtLeast(0L),
            isSuccess = isSuccess,
            details = maskedDetails,
            errorMessage = maskedError
        )

        synchronized(lock) {
            _dbOperations.value = (_dbOperations.value + entry).takeLast(MAX_DB_OPERATIONS)
            if (!isSuccess && !maskedError.isNullOrBlank()) {
                _dbSnapshot.value = _dbSnapshot.value.copy(
                    lastError = "${formatTime(now)}: $operationName ($tableName) — $maskedError",
                    lastUpdatedTimestamp = formatTime(now)
                )
            }
        }

        if (isSuccess) {
            d(
                tag = "ROOM",
                message = "$operationName [$tableName] выполнено за ${durationMs}ms",
                details = maskedDetails,
                operationId = opId
            )
        } else {
            e(
                tag = "ROOM",
                message = "Ошибка БД $operationName [$tableName] (${durationMs}ms)",
                details = listOfNotNull(maskedError, maskedDetails).joinToString(" | ").ifBlank { null },
                operationId = opId
            )
        }
    }

    fun updateDatabaseSnapshot(
        isInitialized: Boolean = true,
        isOpen: Boolean = true,
        schemaVersion: Int = 5,
        activeBudgetId: String? = null,
        tableCounts: Map<String, Int>,
        lastError: String? = null
    ) {
        val nowStr = formatTime()
        synchronized(lock) {
            val current = _dbSnapshot.value
            _dbSnapshot.value = current.copy(
                isInitialized = isInitialized,
                isOpen = isOpen,
                schemaVersion = schemaVersion,
                activeBudgetId = activeBudgetId ?: current.activeBudgetId,
                tableCounts = tableCounts,
                lastUpdatedTimestamp = nowStr,
                lastError = lastError?.let { maskSensitive(it) } ?: current.lastError
            )
        }
    }

    fun updateSystemState(
        lifecycleState: String? = null,
        isVoiceRecording: Boolean? = null,
        isAnalyzingVoice: Boolean? = null,
        isAiAuditRunning: Boolean? = null,
        isGeneratingDavidReaction: Boolean? = null,
        activeBudgetId: String? = null,
        activeModalName: String? = null
    ) {
        synchronized(lock) {
            val cur = _systemState.value
            _systemState.value = cur.copy(
                lifecycleState = lifecycleState ?: cur.lifecycleState,
                isVoiceRecording = isVoiceRecording ?: cur.isVoiceRecording,
                isAnalyzingVoice = isAnalyzingVoice ?: cur.isAnalyzingVoice,
                isAiAuditRunning = isAiAuditRunning ?: cur.isAiAuditRunning,
                isGeneratingDavidReaction = isGeneratingDavidReaction ?: cur.isGeneratingDavidReaction,
                activeBudgetId = activeBudgetId ?: cur.activeBudgetId,
                activeModalName = activeModalName ?: cur.activeModalName
            )
        }
    }

    fun onLifecycleEvent(eventName: String) {
        updateSystemState(lifecycleState = eventName)
        d("LIFECYCLE", "MainActivity → $eventName")
    }

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
            _errors.value = emptyList()
            _networkTraces.value = emptyList()
            _dbOperations.value = emptyList()
            _unreadErrorCount.value = 0
        }
        clearPersistedCrash()
    }

    fun clearErrors() {
        synchronized(lock) {
            _errors.value = emptyList()
            _logs.value = _logs.value.filterNot {
                it.level == LogLevel.ERROR || it.level == LogLevel.FATAL
            }
            _unreadErrorCount.value = 0
        }
        clearPersistedCrash()
    }

    fun clearNetworkTraces() {
        synchronized(lock) {
            _networkTraces.value = emptyList()
        }
    }

    fun clearDbOperations() {
        synchronized(lock) {
            _dbOperations.value = emptyList()
        }
    }

    fun filterLogs(
        entries: List<LogEntry> = _logs.value,
        query: String = "",
        levelFilter: LogLevel? = null,
        sourceFilter: String = "ALL"
    ): List<LogEntry> {
        val trimmedQuery = query.trim().lowercase(Locale.getDefault())
        val normalizedSource = sourceFilter.trim().uppercase(Locale.US)
        val canonicalLevel = levelFilter?.canonical

        return entries.filter { entry ->
            val levelMatches = when (canonicalLevel) {
                null -> true
                LogLevel.ERROR -> entry.level.canonical == LogLevel.ERROR || entry.level.canonical == LogLevel.FATAL
                else -> entry.level.canonical == canonicalLevel
            }
            val sourceMatches = if (normalizedSource.isBlank() || normalizedSource == "ALL") {
                true
            } else {
                entry.source.uppercase(Locale.US).contains(normalizedSource)
            }
            val queryMatches = if (trimmedQuery.isEmpty()) {
                true
            } else {
                entry.message.lowercase(Locale.getDefault()).contains(trimmedQuery) ||
                    entry.source.lowercase(Locale.getDefault()).contains(trimmedQuery) ||
                    (entry.details?.lowercase(Locale.getDefault())?.contains(trimmedQuery) == true) ||
                    (entry.operationId?.lowercase(Locale.getDefault())?.contains(trimmedQuery) == true) ||
                    (entry.stackTrace?.lowercase(Locale.getDefault())?.contains(trimmedQuery) == true)
            }
            levelMatches && sourceMatches && queryMatches
        }
    }

    fun getUptimeMillis(): Long = (System.currentTimeMillis() - appStartMillis).coerceAtLeast(0L)

    fun formatUptime(uptimeMs: Long = getUptimeMillis()): String {
        val totalSeconds = uptimeMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%02dч %02dм %02dс", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02dм %02dс", minutes, seconds)
        }
    }

    fun buildDiagnosticReport(
        appVersionName: String = BuildConfig.VERSION_NAME,
        appVersionCode: Int = BuildConfig.VERSION_CODE,
        buildType: String = BuildConfig.BUILD_TYPE
    ): String {
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)
        val freeMemMb = maxMemMb - usedMemMb

        val snap = _dbSnapshot.value
        val sys = _systemState.value
        val allErrors = _errors.value
        val netList = _networkTraces.value
        val dbOps = _dbOperations.value
        val recentLogs = _logs.value.takeLast(100)

        val manufacturer = try { Build.MANUFACTURER ?: "Unknown" } catch (_: Throwable) { "Unknown" }
        val model = try { Build.MODEL ?: "Unknown" } catch (_: Throwable) { "Unknown" }
        val sdkInt = try { Build.VERSION.SDK_INT } catch (_: Throwable) { 0 }
        val releaseVer = try { Build.VERSION.RELEASE ?: "Unknown" } catch (_: Throwable) { "Unknown" }

        val rawReport = buildString {
            appendLine("==================================================")
            appendLine("OBSIDIAN NEO-BANK (DAVID FINANCE) — DIAGNOSTIC REPORT")
            appendLine("==================================================")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            appendLine("App Version: v$appVersionName (code $appVersionCode) [$buildType]")
            appendLine("Device: $manufacturer $model (Android $releaseVer, API $sdkInt)")
            appendLine("Uptime: ${formatUptime()}")
            appendLine("Memory (JVM Heap): used=${usedMemMb}MB / max=${maxMemMb}MB (free=${freeMemMb}MB)")
            appendLine("Lifecycle: ${sys.lifecycleState} | Modal: ${sys.activeModalName} | ActiveBudget: ${sys.activeBudgetId}")
            appendLine("Background Tasks: VoiceRec=${sys.isVoiceRecording}, VoiceAI=${sys.isAnalyzingVoice}, Audit=${sys.isAiAuditRunning}, DavidReaction=${sys.isGeneratingDavidReaction}")
            appendLine()

            appendLine("--- DATABASE (ROOM) SNAPSHOT ---")
            appendLine("Database: ${snap.databaseName} (schema v${snap.schemaVersion}) | Initialized=${snap.isInitialized} | Open=${snap.isOpen}")
            appendLine("Row Counts: " + if (snap.tableCounts.isEmpty()) "not collected yet" else snap.tableCounts.entries.joinToString(", ") { "${it.key}=${it.value}" })
            if (!snap.lastError.isNullOrBlank()) {
                appendLine("Last DB Error: ${snap.lastError}")
            }
            if (dbOps.isNotEmpty()) {
                appendLine("Recent DB Operations (${dbOps.size}):")
                dbOps.takeLast(15).forEach { op ->
                    appendLine("  [${op.timestamp}] (${op.operationId}) ${op.operationName} on ${op.tableName} -> ${op.durationMs}ms [${if (op.isSuccess) "OK" else "FAIL"}] ${op.details ?: ""}")
                }
            }
            appendLine()

            appendLine("--- NETWORK & GEMINI AI TRACES (${netList.size}) ---")
            if (netList.isEmpty()) {
                appendLine("No network/AI calls recorded in this session.")
            } else {
                val successCount = netList.count { it.status == NetworkCallStatus.SUCCESS }
                val fallbackCount = netList.count { it.status == NetworkCallStatus.FALLBACK }
                val errorCount = netList.count { it.status == NetworkCallStatus.ERROR }
                val avgMs = netList.map { it.durationMs }.average().toLong()
                appendLine("Summary: SUCCESS=$successCount, FALLBACK=$fallbackCount, ERROR=$errorCount, AVG_LATENCY=${avgMs}ms")
                netList.takeLast(20).forEach { net ->
                    appendLine("  [${net.timestamp}] (${net.operationId}) ${net.operationType} | model=${net.modelName} | ${net.durationMs}ms | ${net.status.label} ${net.httpCode?.let { "HTTP $it" } ?: ""} ${net.errorMessage ?: ""}")
                }
            }
            appendLine()

            appendLine("--- ERRORS & EXCEPTIONS (${allErrors.size}) ---")
            if (allErrors.isEmpty()) {
                appendLine("No errors or uncaught exceptions recorded.")
            } else {
                allErrors.takeLast(25).forEachIndexed { idx, err ->
                    appendLine("#${idx + 1} [${err.timestamp}] [${err.level.label}] [${err.source}] ${err.operationId?.let { "($it)" } ?: ""}")
                    appendLine("Message: ${err.message}")
                    if (!err.details.isNullOrBlank()) appendLine("Details: ${err.details}")
                    if (!err.stackTrace.isNullOrBlank()) {
                        appendLine("StackTrace:")
                        appendLine(err.stackTrace.take(2500))
                    }
                    appendLine("---")
                }
            }
            appendLine()

            appendLine("--- RECENT LOGS (last ${recentLogs.size} of ${_logs.value.size}) ---")
            recentLogs.forEach { log ->
                val opTag = log.operationId?.let { " [$it]" } ?: ""
                val detailSuffix = log.details?.let { " | $it" } ?: ""
                appendLine("${log.timestamp} [${log.level.label}][${log.source}]$opTag ${log.message}$detailSuffix")
            }
        }
        return maskSensitive(rawReport)
    }

    fun buildAiDiagnosticPrompt(
        targetError: LogEntry? = null,
        appVersionName: String = BuildConfig.VERSION_NAME,
        appVersionCode: Int = BuildConfig.VERSION_CODE,
        buildType: String = BuildConfig.BUILD_TYPE
    ): String {
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val manufacturer = try { Build.MANUFACTURER ?: "Unknown" } catch (_: Throwable) { "Unknown" }
        val model = try { Build.MODEL ?: "Unknown" } catch (_: Throwable) { "Unknown" }
        val sdkInt = try { Build.VERSION.SDK_INT } catch (_: Throwable) { 0 }
        val releaseVer = try { Build.VERSION.RELEASE ?: "Unknown" } catch (_: Throwable) { "Unknown" }

        val primaryError = targetError ?: _errors.value.lastOrNull()
        val snap = _dbSnapshot.value
        val sys = _systemState.value

        val correlatedLogs = if (primaryError != null) {
            val all = _logs.value
            val byOpId = if (!primaryError.operationId.isNullOrBlank()) {
                all.filter { it.operationId == primaryError.operationId }
            } else emptyList()
            val preceding = all.filter { it.epochMillis <= primaryError.epochMillis + 1000L }.takeLast(18)
            (byOpId + preceding).distinctBy { it.id }.takeLast(20)
        } else {
            _logs.value.takeLast(20)
        }

        val recentNetwork = _networkTraces.value.takeLast(8)
        val recentDb = _dbOperations.value.takeLast(8)

        val prompt = buildString {
            appendLine("# Диагностический отчёт для ИИ-разработчика — Obsidian Neo-Bank (David Finance)")
            appendLine()
            appendLine("## 1. Контекст проекта и окружения")
            appendLine("- **Репозиторий:** `https://github.com/smaks190-crypto/-.git`")
            appendLine("- **Стек:** Kotlin, Jetpack Compose (Material 3), MVVM + StateFlow, Room Database (v${snap.schemaVersion}), Retrofit2 + Gemini API")
            appendLine("- **Версия приложения:** `v$appVersionName` (`versionCode=$appVersionCode`, `buildType=$buildType`)")
            appendLine("- **Устройство:** `$manufacturer $model` (Android $releaseVer, SDK $sdkInt)")
            appendLine("- **Состояние памяти и аптайм:** Heap `${usedMemMb}MB / ${maxMemMb}MB`, Uptime `${formatUptime()}`")
            appendLine("- **Экран / Жизненный цикл:** Lifecycle=`${sys.lifecycleState}`, ActiveModal=`${sys.activeModalName}`, ActiveBudget=`${sys.activeBudgetId}`")
            appendLine("- **Фоновые задачи:** VoiceRec=`${sys.isVoiceRecording}`, VoiceAI=`${sys.isAnalyzingVoice}`, Audit=`${sys.isAiAuditRunning}`, DavidReaction=`${sys.isGeneratingDavidReaction}`")
            appendLine()

            appendLine("## 2. Описание целевой ошибки")
            if (primaryError != null) {
                appendLine("- **Уровень:** `${primaryError.level.canonical.label}` ${if (primaryError.isPreviousSessionCrash) "(Фатальный сбой из прошлой сессии)" else ""}")
                appendLine("- **Источник (модуль):** `${primaryError.source}`")
                appendLine("- **Время:** `${primaryError.timestamp}`")
                if (!primaryError.operationId.isNullOrBlank()) {
                    appendLine("- **Operation ID:** `${primaryError.operationId}`")
                }
                appendLine("- **Сообщение:** `${primaryError.message}`")
                if (!primaryError.details.isNullOrBlank()) {
                    appendLine("- **Детали контекста:** `${primaryError.details}`")
                }
                if (!primaryError.stackTrace.isNullOrBlank()) {
                    appendLine()
                    appendLine("### Stack Trace:")
                    appendLine("```text")
                    appendLine(primaryError.stackTrace.take(3500))
                    appendLine("```")
                }
            } else {
                appendLine("- Явных исключений (`ERROR` / `FATAL`) не зафиксировано; ниже приведён срез последних логов, сетевых вызовов и операций БД для диагностики поведения.")
            }
            appendLine()

            appendLine("## 3. Предшествующие и связанные события (Logs)")
            appendLine("```text")
            if (correlatedLogs.isEmpty()) {
                appendLine("Логи отсутствуют")
            } else {
                correlatedLogs.forEach { log ->
                    val opStr = log.operationId?.let { " [$it]" } ?: ""
                    val detStr = log.details?.let { " | $it" } ?: ""
                    appendLine("${log.timestamp} [${log.level.canonical.label}][${log.source}]$opStr ${log.message}$detStr")
                }
            }
            appendLine("```")
            appendLine()

            appendLine("## 4. Последние сетевые / Gemini AI вызовы и состояние Room DB")
            appendLine("- **Таблицы Room DB:** " + if (snap.tableCounts.isEmpty()) "нет данных" else snap.tableCounts.entries.joinToString(", ") { "${it.key}=${it.value}" })
            if (!snap.lastError.isNullOrBlank()) {
                appendLine("- **Последняя ошибка Room:** `${snap.lastError}`")
            }
            if (recentNetwork.isNotEmpty()) {
                appendLine("- **Последние вызовы Gemini / Network:**")
                recentNetwork.forEach { net ->
                    appendLine("  - `[${net.timestamp}]` `${net.operationId}` `${net.operationType}` (`${net.modelName}`) — `${net.durationMs}ms`, статус: `${net.status.label}` ${net.httpCode?.let { "(HTTP $it)" } ?: ""} ${net.errorMessage ?: ""}")
                }
            }
            if (recentDb.isNotEmpty()) {
                appendLine("- **Последние операции БД:**")
                recentDb.forEach { op ->
                    appendLine("  - `[${op.timestamp}]` `${op.operationId}` `${op.operationName}` (`${op.tableName}`) — `${op.durationMs}ms` [${if (op.isSuccess) "OK" else "FAIL"}]")
                }
            }
            appendLine()

            appendLine("## 5. Задача для ИИ")
            appendLine("1. Найди точную первопричину (root cause) ошибки в кодовой базе проекта по указанному источнику, Stack Trace и цепочке событий.")
            appendLine("2. Проверь пограничные случаи (null-безопасность, отмена корутин `CancellationException`, ошибки HTTP/Gemini fallback, миграции и транзакции Room).")
            appendLine("3. Предложи минимальное, безопасное и полностью компилируемое исправление в конкретных файлах проекта, не ломая существующую бизнес-логику и реактивный поток `StateFlow`.")
        }

        return maskSensitive(prompt)
    }

    private fun addEntryInternal(
        level: LogLevel,
        source: String,
        message: String,
        details: String? = null,
        stackTrace: String? = null,
        operationId: String? = null,
        ignorePause: Boolean = false
    ) {
        val canonicalLevel = level.canonical
        if (_isPaused.value && !ignorePause && canonicalLevel != LogLevel.ERROR && canonicalLevel != LogLevel.FATAL) {
            return
        }

        val now = System.currentTimeMillis()
        val cleanSource = source.trim().uppercase(Locale.US).ifBlank { "APP" }
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = formatTime(now),
            epochMillis = now,
            level = canonicalLevel,
            source = cleanSource,
            message = message,
            details = details,
            stackTrace = stackTrace,
            operationId = operationId
        )

        synchronized(lock) {
            val currentLogs = _logs.value
            _logs.value = if (currentLogs.size >= MAX_LOGS) {
                currentLogs.drop(currentLogs.size - MAX_LOGS + 1) + entry
            } else {
                currentLogs + entry
            }

            if (canonicalLevel == LogLevel.ERROR || canonicalLevel == LogLevel.FATAL) {
                val currentErrors = _errors.value
                _errors.value = if (currentErrors.size >= MAX_ERRORS) {
                    currentErrors.drop(currentErrors.size - MAX_ERRORS + 1) + entry
                } else {
                    currentErrors + entry
                }
                _unreadErrorCount.value = _unreadErrorCount.value + 1
            }
        }
    }

    private fun safeLogcat(priority: Int, tag: String, message: String, throwable: Throwable?) {
        try {
            when (priority) {
                Log.DEBUG -> Log.d(tag, message)
                Log.INFO -> Log.i(tag, message)
                Log.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
                Log.ERROR, Log.ASSERT -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            }
        } catch (_: Throwable) {
            // Безопасное игнорирование в JVM Unit-тестах без Robolectric
        }
    }
}
