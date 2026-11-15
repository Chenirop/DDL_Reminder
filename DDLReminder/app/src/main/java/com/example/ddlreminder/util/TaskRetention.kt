package com.example.ddlreminder.util

import com.example.ddlreminder.data.TaskEntity

/**
 * Single source of truth for "when does an unfinished task leave the list".
 *
 * The regular rule is 48h after the deadline. A task restored from the recycle
 * bin gets a fresh 48h window — unless it was already overdue for more than 48h
 * when it was restored: those stay only for the short, user configured window
 * (12h by default) so a resurrected old task does not clutter the list forever.
 */
object TaskRetention {

    fun removalDeadline(task: TaskEntity, restoreRetentionMs: Long): Long? {
        val ddl = task.ddl ?: return null
        val restoredAt = task.restoredAt
        val overdueWindow = TimeFormat.OVERDUE_RETENTION_MS
        return when {
            restoredAt == null -> ddl + overdueWindow
            restoredAt >= ddl + overdueWindow -> restoredAt + restoreRetentionMs
            // Restored while the task was still inside its normal lifetime (or
            // within the 48h grace): give it the full window from whichever of
            // the two is later, so a future deadline is never cut short.
            else -> maxOf(ddl, restoredAt) + overdueWindow
        }
    }

    fun isExpired(task: TaskEntity, now: Long, restoreRetentionMs: Long): Boolean {
        val deadline = removalDeadline(task, restoreRetentionMs) ?: return false
        return deadline <= now
    }

    /** Milliseconds until the automatic removal; 0 when already due. */
    fun remainingMs(task: TaskEntity, now: Long, restoreRetentionMs: Long): Long {
        val deadline = removalDeadline(task, restoreRetentionMs) ?: return 0L
        return (deadline - now).coerceAtLeast(0L)
    }

    /** True for a task that only got the short post-restore window. */
    fun isShortRestoreWindow(task: TaskEntity): Boolean {
        val ddl = task.ddl ?: return false
        val restoredAt = task.restoredAt ?: return false
        return restoredAt >= ddl + TimeFormat.OVERDUE_RETENTION_MS
    }
}
