package com.jbsan.ldapadvisor.core.logging

import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

class InMemoryAppLogger(
    private val maxEvents: Int = 2_000,
    private val logcatTag: String = "LDAPADvisor",
    private val mirrorToLogcat: Boolean = true,
) : AppLogger {

    private val lock = Any()
    private val events = ArrayDeque<LogEvent>(maxEvents)
    private val sequence = AtomicInteger(0)

    override fun log(
        level: LogLevel,
        component: String,
        message: String,
        errorCode: String?,
        durationMs: Long?,
        throwable: Throwable?,
    ) {
        val sanitizedMessage = LogSanitizer.sanitize(message)
        val sanitizedDetails = throwable?.let {
            LogSanitizer.sanitize(it.stackTraceToString())
        }
        val event = LogEvent(
            timestampEpochMs = System.currentTimeMillis(),
            level = level,
            component = component,
            message = buildString {
                append(sanitizedMessage)
                if (!errorCode.isNullOrBlank()) append(" code=").append(errorCode)
                if (durationMs != null) append(" durationMs=").append(durationMs)
                if (sanitizedDetails != null) append('\n').append(sanitizedDetails)
            },
            errorCode = errorCode,
            durationMs = durationMs,
        )
        synchronized(lock) {
            if (events.size >= maxEvents) events.removeFirst()
            events.addLast(event)
            sequence.incrementAndGet()
        }
        if (mirrorToLogcat) {
            val line = "[$component] ${event.message}"
            when (level) {
                LogLevel.DEBUG -> Log.d(logcatTag, line)
                LogLevel.INFO -> Log.i(logcatTag, line)
                LogLevel.WARNING -> Log.w(logcatTag, line)
                LogLevel.ERROR -> Log.e(logcatTag, line)
            }
        }
    }

    override fun snapshot(): List<LogEvent> = synchronized(lock) { events.toList() }

    override fun clear() = synchronized(lock) { events.clear() }
}
