package com.jbsan.ldapadvisor.core.ad

import java.time.Duration
import java.time.Instant

/**
 * Windows FILETIME: 100-nanosecond intervals since 1601-01-01 UTC.
 */
object FileTimeConverter {
    private const val EPOCH_DIFF_100NS = 116444736000000000L
    private const val NEVER_EXPIRES = 0x7FFF_FFFF_FFFF_FFFFL
    // Practical Instant bounds expressed in epoch millis (avoid Instant.MIN overflow).
    private const val MIN_EPOCH_MILLIS = -62_135_596_800_000L // year ~0001
    private const val MAX_EPOCH_MILLIS = 253_402_300_799_000L // year 9999

    sealed class FileTimeValue {
        data object Zero : FileTimeValue()
        data object Never : FileTimeValue()
        data object Invalid : FileTimeValue()
        data class InstantValue(val instant: Instant) : FileTimeValue()
        data class DurationValue(val duration: Duration) : FileTimeValue()
    }

    fun parse(raw: Long): FileTimeValue = when {
        raw == 0L -> FileTimeValue.Zero
        raw == NEVER_EXPIRES || raw == -1L -> FileTimeValue.Never
        raw < 0L -> {
            // Negative FILETIME intervals are used for durations (e.g. maxPwdAge).
            val hundredNanos = -raw
            val nanos = try {
                Math.multiplyExact(hundredNanos, 100L)
            } catch (_: ArithmeticException) {
                return FileTimeValue.Invalid
            }
            FileTimeValue.DurationValue(Duration.ofNanos(nanos))
        }
        else -> {
            val unixMillis = (raw - EPOCH_DIFF_100NS) / 10_000L
            if (unixMillis < MIN_EPOCH_MILLIS || unixMillis > MAX_EPOCH_MILLIS) {
                FileTimeValue.Invalid
            } else {
                FileTimeValue.InstantValue(Instant.ofEpochMilli(unixMillis))
            }
        }
    }

    fun parse(raw: String?): FileTimeValue {
        if (raw.isNullOrBlank()) return FileTimeValue.Invalid
        return try {
            parse(raw.trim().toLong())
        } catch (_: NumberFormatException) {
            FileTimeValue.Invalid
        }
    }

    fun toFileTime(instant: Instant): Long {
        val millis = instant.toEpochMilli()
        return millis * 10_000L + EPOCH_DIFF_100NS
    }

    fun isNeverExpiresAccount(raw: Long): Boolean =
        raw == 0L || raw == NEVER_EXPIRES || raw == -1L
}
