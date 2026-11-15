package com.example.ddlreminder.util

import com.example.ddlreminder.data.TaskEntity
import java.util.concurrent.TimeUnit

object TaskOrdering {

    private val COMPLETED_VISIBLE_WINDOW_MS = TimeUnit.HOURS.toMillis(24)

    /** Fallback window for tasks restored after they were already 48h overdue. */
    private val DEFAULT_RESTORE_RETENTION_MS = TimeFormat.HOUR_MS * 12

    /**
     * Overdue but still unfinished tasks stay in the list for 48 hours after
     * their deadline; then they are dropped by the cleanup.
     */
    val OVERDUE_RETENTION_MS: Long = TimeFormat.OVERDUE_RETENTION_MS

    fun isOverdue(task: TaskEntity, now: Long): Boolean =
        !task.isCompleted && task.ddl != null && task.ddl <= now

    /**
     * Filters tasks that are gone for good and orders the rest:
     *
     * 1. overdue, unfinished tasks first (oldest deadline first) in the default
     *    order only — as soon as the user dragged a card, the manual order wins
     *    and overdue tasks are no longer pinned;
     * 2. upcoming tasks by deadline, tasks without a deadline last;
     * 3. completed tasks at the very bottom (most recently completed first).
     */
    fun visibleAndSorted(
        tasks: List<TaskEntity>,
        now: Long,
        restoreRetentionMs: Long = DEFAULT_RESTORE_RETENTION_MS
    ): List<TaskEntity> {
        val completedCutoff = now - COMPLETED_VISIBLE_WINDOW_MS

        val visible = tasks.filter { task ->
            if (task.isCompleted) {
                val visibleFrom = maxOf(
                    task.completedAt ?: Long.MAX_VALUE,
                    task.restoredAt ?: Long.MIN_VALUE
                )
                visibleFrom > completedCutoff
            } else {
                !TaskRetention.isExpired(task, now, restoreRetentionMs)
            }
        }

        val hasManualOrder = visible.any { it.sortOrder != null }

        val manualOrder = compareBy<TaskEntity> { it.sortOrder ?: Int.MAX_VALUE }
            .thenBy { it.id }
        val ddlOrder = compareBy<TaskEntity> { it.ddl == null }
            .thenBy { it.ddl ?: Long.MAX_VALUE }
            .thenBy { it.createdAt }
            .thenBy { it.id }

        val (completed, pending) = visible.partition { it.isCompleted }

        // A manual order wins everywhere, including for overdue tasks: once the
        // user dragged a card, the overdue ones are not pinned to the top any
        // more, they simply follow the order the user chose.
        if (hasManualOrder) {
            return pending.sortedWith(manualOrder) + completed.sortedWith(manualOrder)
        }

        val (overdue, upcoming) = pending.partition { it.ddl != null && it.ddl <= now }

        // Default order: overdue first, oldest deadline first, then upcoming by
        // deadline, then the finished ones.
        return overdue.sortedWith(ddlOrder) +
            upcoming.sortedWith(ddlOrder) +
            completed.sortedWith(
                compareByDescending<TaskEntity> { it.completedAt ?: 0L }
                    .thenByDescending { it.id }
            )
    }
}
