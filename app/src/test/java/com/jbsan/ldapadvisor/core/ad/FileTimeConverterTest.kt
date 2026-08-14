package com.jbsan.ldapadvisor.core.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FileTimeConverterTest {
    @Test
    fun zeroIsZero() {
        assertEquals(FileTimeConverter.FileTimeValue.Zero, FileTimeConverter.parse(0L))
    }

    @Test
    fun neverExpiresSpecial() {
        assertEquals(
            FileTimeConverter.FileTimeValue.Never,
            FileTimeConverter.parse(0x7FFF_FFFF_FFFF_FFFFL),
        )
        assertEquals(FileTimeConverter.FileTimeValue.Never, FileTimeConverter.parse(-1L))
    }

    @Test
    fun knownUnixEpoch() {
        // 116444736000000000 FILETIME == 1970-01-01T00:00:00Z
        val value = FileTimeConverter.parse(116444736000000000L)
        assertTrue(value is FileTimeConverter.FileTimeValue.InstantValue)
        assertEquals(Instant.EPOCH, (value as FileTimeConverter.FileTimeValue.InstantValue).instant)
    }

    @Test
    fun roundTrip() {
        val instant = Instant.parse("2020-01-02T03:04:05Z")
        val ft = FileTimeConverter.toFileTime(instant)
        val parsed = FileTimeConverter.parse(ft) as FileTimeConverter.FileTimeValue.InstantValue
        assertEquals(instant, parsed.instant)
    }

    @Test
    fun negativeDuration() {
        // -10_000_000 = 1 second duration in 100ns units
        val parsed = FileTimeConverter.parse(-10_000_000L)
        assertTrue(parsed is FileTimeConverter.FileTimeValue.DurationValue)
        assertEquals(1_000_000_000L, (parsed as FileTimeConverter.FileTimeValue.DurationValue).duration.toNanos())
    }
}
