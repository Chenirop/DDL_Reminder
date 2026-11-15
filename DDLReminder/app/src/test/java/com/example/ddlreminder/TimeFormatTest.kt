package com.example.ddlreminder

import com.example.ddlreminder.util.CountdownLevel
import com.example.ddlreminder.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatRemaining_usesRequiredPattern() {
        val remaining = 1 * TimeFormat.DAY_MS +
            3 * TimeFormat.HOUR_MS +
            5 * TimeFormat.MINUTE_MS
        assertEquals("01day03hour05min", TimeFormat.formatRemaining(remaining))
    }

    @Test
    fun countdownText_containsExactPrefixAndSuffix() {
        val now = 1_000_000L
        val ddl = now + TimeFormat.DAY_MS + 2 * TimeFormat.HOUR_MS
        assertEquals(
            "距离截止时间还有01day02hour00min",
            TimeFormat.countdownText(ddl, now)
        )
        assertEquals("已截止", TimeFormat.countdownText(now - 1, now))
    }

    @Test
    fun countdownLevel_matchesThreeColorBands() {
        val now = 0L
        assertEquals(CountdownLevel.GREEN, TimeFormat.countdownLevel(now + 3 * TimeFormat.DAY_MS, now))
        assertEquals(CountdownLevel.ORANGE, TimeFormat.countdownLevel(now + 2 * TimeFormat.DAY_MS, now))
        assertEquals(CountdownLevel.ORANGE, TimeFormat.countdownLevel(now + TimeFormat.DAY_MS, now))
        assertEquals(CountdownLevel.RED, TimeFormat.countdownLevel(now + TimeFormat.DAY_MS - 1, now))
        assertEquals(CountdownLevel.RED, TimeFormat.countdownLevel(now, now))
        assertEquals(CountdownLevel.NONE, TimeFormat.countdownLevel(null, now))
    }
}
