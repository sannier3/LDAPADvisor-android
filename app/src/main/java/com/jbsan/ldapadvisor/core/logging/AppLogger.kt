package com.jbsan.ldapadvisor.core.logging

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

data class LogEvent(
    val timestampEpochMs: Long,
    val level: LogLevel,
    val component: String,
    val message: String,
    val errorCode: String? = null,
    val durationMs: Long? = null,
)

interface AppLogger {
    fun log(
        level: LogLevel,
        component: String,
        message: String,
        errorCode: String? = null,
        durationMs: Long? = null,
        throwable: Throwable? = null,
    )

    fun debug(component: String, message: String) =
        log(LogLevel.DEBUG, component, message)

    fun info(component: String, message: String) =
        log(LogLevel.INFO, component, message)

    fun warning(component: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARNING, component, message, throwable = throwable)

    fun error(component: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, component, message, throwable = throwable)

    fun snapshot(): List<LogEvent>
    fun clear()
}
