package com.example.ddlreminder.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CountdownLevel {
    NONE,
    GREEN,
    ORANGE,
    RED
}

object TimeFormat {

    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 60 * MINUTE_MS
    const val DAY_MS = 24 * HOUR_MS

    /** Overdue, unfinished tasks are removed 48 hours after their deadline. */
    const val OVERDUE_RETENTION_MS = 2 * DAY_MS

    private val dateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val timeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun formatDateTime(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }

    fun formatDate(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
    }

    fun formatTime(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
    }

    /**
     * Always returns e.g. "01day03hour05min". The caller prepends the prefix.
     */
    fun formatRemaining(remainingMillis: Long): String {
        val totalMinutes = remainingMillis.coerceAtLeast(0L) / MINUTE_MS
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return String.format(Locale.US, "%02dday%02dhour%02dmin", days, hours, minutes)
    }

    fun countdownText(ddl: Long?, now: Long): String {
        if (ddl == null) return ""
        if (ddl <= now) return "已截止"
        return "距离截止时间还有${formatRemaining(ddl - now)}"
    }

    fun countdownLevel(ddl: Long?, now: Long): CountdownLevel {
        if (ddl == null) return CountdownLevel.NONE
        val remaining = ddl - now
        return when {
            remaining <= 0L -> CountdownLevel.RED
            remaining > 2 * DAY_MS -> CountdownLevel.GREEN
            remaining >= DAY_MS -> CountdownLevel.ORANGE
            else -> CountdownLevel.RED
        }
    }

    /**
     * Time left before an overdue task is removed from the list (deadline + 48h).
     * Returns `null` for tasks without a deadline.
     */
    fun overdueRemovalRemaining(ddl: Long?, now: Long): Long? {
        if (ddl == null) return null
        return (ddl + OVERDUE_RETENTION_MS - now).coerceAtLeast(0L)
    }
}
