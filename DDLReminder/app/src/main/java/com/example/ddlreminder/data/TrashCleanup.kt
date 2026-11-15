package com.example.ddlreminder.data

import com.example.ddlreminder.util.AttachmentStore
import com.example.ddlreminder.util.TaskRetention
import com.example.ddlreminder.util.TimeFormat

/**
 * The periodic / foreground cleanup that used to delete old tasks outright.
 * It now feeds the recycle bin: user deleted tasks always land there, while the
 * automatic 24h (completed) and 48h (overdue) removals only do so when the
 * matching setting is enabled. The bin itself is emptied after the configured
 * retention period.
 */
object TrashCleanup {

    suspend fun run(
        repository: TodoRepository,
        preferences: AppPreferences,
        now: Long = System.currentTimeMillis()
    ) {
        val restoreWindowMs = preferences.trashRestoreRetentionHours * TimeFormat.HOUR_MS
        val completed = repository.getCompletedBefore(now - TimeFormat.DAY_MS)
        val overdue = repository.getIncompleteTasksWithDdl()
            .filter { TaskRetention.isExpired(it, now, restoreWindowMs) }
        val expired = completed + overdue

        if (preferences.keepAutoDeletedInTrash) {
            expired.forEach { task ->
                repository.moveTaskToTrash(task.id, now, DELETE_SOURCE_AUTO)
            }
        } else {
            expired.forEach { task -> permanentlyDelete(repository, task.id) }
        }

        val retentionCutoff = now - preferences.trashRetentionDays * TimeFormat.DAY_MS
        repository.getTrashBefore(retentionCutoff).forEach { task ->
            permanentlyDelete(repository, task.id)
        }
    }

    /** Deletes the task together with the attachment files it owns. */
    suspend fun permanentlyDelete(repository: TodoRepository, taskId: Long) {
        repository.getAttachments(taskId).forEach { attachment ->
            AttachmentStore.delete(attachment.localPath)
        }
        repository.deleteTask(taskId)
    }
}
