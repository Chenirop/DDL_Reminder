package com.example.ddlreminder.reminder

import com.example.ddlreminder.util.TimeFormat

enum class ReminderType(val code: Int, val offsetMillis: Long) {
    TWENTY_FOUR_HOURS(0, 24 * TimeFormat.HOUR_MS),
    TWELVE_HOURS(1, 12 * TimeFormat.HOUR_MS);

    companion object {
        fun fromCode(code: Int): ReminderType? = entries.firstOrNull { it.code == code }
    }
}

object ReminderIds {
    /**
     * Derives one stable Android notification / PendingIntent id from a task id
     * and its reminder node.
     */
    fun forReminder(taskId: Long, type: ReminderType): Int {
        val base = (taskId and 0x3FFFFFFFL).toInt()
        return base * 2 + type.code
    }
}
