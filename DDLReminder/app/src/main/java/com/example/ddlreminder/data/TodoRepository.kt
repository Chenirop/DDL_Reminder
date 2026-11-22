package com.example.ddlreminder.data

import kotlinx.coroutines.flow.Flow

class TodoRepository(private val dao: TaskDao) {

    fun observeListSummaries(): Flow<List<ListSummary>> = dao.observeListSummaries()

    fun observeTaskCount(): Flow<Int> = dao.observeTaskCount()

    fun observeList(id: Long): Flow<TaskListEntity?> = dao.observeList(id)

    fun observeTasks(listId: Long): Flow<List<TaskEntity>> = dao.observeTasks(listId)

    fun observeTrashItems(): Flow<List<TrashItem>> = dao.observeTrashItems()

    fun observeAttachments(taskId: Long): Flow<List<TaskAttachmentEntity>> =
        dao.observeAttachments(taskId)

    suspend fun getAttachments(taskId: Long): List<TaskAttachmentEntity> =
        dao.getAttachments(taskId)

    suspend fun getAllAttachments(): List<TaskAttachmentEntity> = dao.getAllAttachments()

    suspend fun addAttachment(attachment: TaskAttachmentEntity): Long =
        dao.insertAttachment(attachment)

    suspend fun deleteAttachment(attachment: TaskAttachmentEntity) =
        dao.deleteAttachment(attachment)

    suspend fun deleteAttachmentsForTask(taskId: Long) = dao.deleteAttachmentsForTask(taskId)

    suspend fun getList(id: Long): TaskListEntity? = dao.getList(id)

    suspend fun findListByName(name: String): TaskListEntity? = dao.findListByName(name)

    suspend fun setListNotifications(listId: Long, enabled: Boolean, now: Long) =
        dao.setListNotifications(listId, enabled, now)

    suspend fun getAllLists(): List<TaskListEntity> = dao.getAllLists()

    suspend fun getFirstList(): TaskListEntity? = dao.getFirstList()

    suspend fun createList(name: String, createdAt: Long = System.currentTimeMillis()): Long =
        dao.insertList(
            TaskListEntity(
                name = name.trim(),
                createdAt = createdAt,
                updatedAt = createdAt
            )
        )

    suspend fun renameList(id: Long, name: String, now: Long = System.currentTimeMillis()) {
        dao.renameList(id, name.trim(), now)
    }

    suspend fun deleteList(id: Long) = dao.deleteList(id)

    suspend fun deleteAllLists() = dao.deleteAllLists()

    suspend fun getTasksForList(listId: Long): List<TaskEntity> = dao.getTasksForList(listId)

    suspend fun getAllTasks(): List<TaskEntity> = dao.getAllTasks()

    suspend fun getTask(id: Long): TaskEntity? = dao.getTask(id)

    suspend fun createTask(task: TaskEntity): Long = dao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = dao.updateTask(task)

    /** Permanently removes a task; attachments cascade in the database. */
    suspend fun deleteTask(id: Long) = dao.deleteTask(id)

    suspend fun deleteAllTasks() = dao.deleteAllTasks()

    /** Moves a task to the recycle bin. */
    suspend fun moveTaskToTrash(
        id: Long,
        now: Long = System.currentTimeMillis(),
        source: Int = DELETE_SOURCE_USER
    ) = dao.softDeleteTask(id, now, source)

    suspend fun restoreTask(id: Long, now: Long = System.currentTimeMillis()) =
        dao.restoreTask(id, now)

    suspend fun markCompleted(id: Long, now: Long = System.currentTimeMillis()) {
        dao.markCompleted(id, now)
    }

    suspend fun markIncomplete(id: Long, now: Long = System.currentTimeMillis()) {
        dao.markIncomplete(id, now)
    }

    suspend fun getIncompleteTasksWithDdl(): List<TaskEntity> = dao.getIncompleteTasksWithDdl()

    suspend fun claimReminder(id: Long, is24Hour: Boolean): Boolean {
        val now = System.currentTimeMillis()
        return if (is24Hour) {
            dao.claim24HourReminder(id, now) > 0
        } else {
            dao.claim12HourReminder(id, now) > 0
        }
    }

    suspend fun releaseReminder(id: Long, is24Hour: Boolean) {
        if (is24Hour) dao.release24HourReminder(id) else dao.release12HourReminder(id)
    }

    suspend fun getCompletedBefore(cutoff: Long): List<TaskEntity> =
        dao.getCompletedBefore(cutoff)

    suspend fun getOverdueIncompleteBefore(cutoff: Long): List<TaskEntity> =
        dao.getOverdueIncompleteBefore(cutoff)

    suspend fun getTrashBefore(cutoff: Long): List<TaskEntity> = dao.getTrashBefore(cutoff)

    suspend fun maxSortOrder(listId: Long): Int? = dao.maxSortOrder(listId)

    suspend fun setTaskSortOrder(
        taskId: Long,
        sortOrder: Int?,
        now: Long = System.currentTimeMillis()
    ) = dao.updateSortOrder(taskId, sortOrder, now)

    /** Writes explicit positions (0..n-1) following the given order. */
    suspend fun applyTaskOrder(orderedTaskIds: List<Long>, now: Long = System.currentTimeMillis()) {
        dao.applyTaskOrder(orderedTaskIds, now)
    }

    /** Drops the manual positions so the list falls back to the DDL ordering. */
    suspend fun clearTaskOrder(listId: Long) = dao.clearSortOrder(listId)
}
