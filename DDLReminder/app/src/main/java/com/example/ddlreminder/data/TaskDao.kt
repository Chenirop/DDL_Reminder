package com.example.ddlreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query(
        """
        SELECT l.id AS id, l.name AS name,
               l.notificationsEnabled AS notificationsEnabled,
               COUNT(CASE WHEN t.isCompleted = 0 THEN 1 END) AS pendingCount
        FROM task_lists l
        LEFT JOIN tasks t ON t.listId = l.id AND t.deletedAt IS NULL
        GROUP BY l.id, l.name, l.createdAt, l.notificationsEnabled
        ORDER BY l.createdAt ASC
        """
    )
    fun observeListSummaries(): Flow<List<ListSummary>>

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL")
    fun observeTaskCount(): Flow<Int>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    fun observeList(id: Long): Flow<TaskListEntity?>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getList(id: Long): TaskListEntity?

    @Query("SELECT * FROM task_lists WHERE name = :name LIMIT 1")
    suspend fun findListByName(name: String): TaskListEntity?

    @Query(
        "UPDATE task_lists SET notificationsEnabled = :enabled, updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun setListNotifications(id: Long, enabled: Boolean, now: Long)

    @Query("SELECT * FROM task_lists ORDER BY createdAt ASC")
    suspend fun getAllLists(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstList(): TaskListEntity?

    @Insert
    suspend fun insertList(list: TaskListEntity): Long

    @Insert
    suspend fun insertLists(lists: List<TaskListEntity>): List<Long>

    @Query("UPDATE task_lists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renameList(id: Long, name: String, now: Long)

    @Query("DELETE FROM task_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM task_lists")
    suspend fun deleteAllLists()

    @Query("SELECT * FROM tasks WHERE listId = :listId AND deletedAt IS NULL")
    fun observeTasks(listId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE listId = :listId")
    suspend fun getTasksForList(listId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT MAX(sortOrder) FROM tasks WHERE listId = :listId AND deletedAt IS NULL")
    suspend fun maxSortOrder(listId: Long): Int?

    @Query("UPDATE tasks SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int?, now: Long)

    @Query("UPDATE tasks SET sortOrder = NULL WHERE listId = :listId AND deletedAt IS NULL")
    suspend fun clearSortOrder(listId: Long)

    /**
     * Persists a whole drag-and-drop order in one transaction. Writing the rows
     * one by one outside a transaction made Room emit a new task list after every
     * single UPDATE; the intermediate orders did not match the list on screen, so
     * the UI rebuilt every card and their text visibly re-laid out for a frame.
     */
    @Transaction
    suspend fun applyTaskOrder(orderedTaskIds: List<Long>, now: Long) {
        orderedTaskIds.forEachIndexed { index, id -> updateSortOrder(id, index, now) }
    }

    @Query(
        "UPDATE tasks SET isCompleted = 1, completedAt = :now, updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun markCompleted(id: Long, now: Long)

    @Query(
        "UPDATE tasks SET isCompleted = 0, completedAt = NULL, " +
            "remind24Sent = 0, remind12Sent = 0, updatedAt = :now WHERE id = :id"
    )
    suspend fun markIncomplete(id: Long, now: Long)

    @Query(
        "SELECT * FROM tasks WHERE isCompleted = 0 AND ddl IS NOT NULL " +
            "AND deletedAt IS NULL"
    )
    suspend fun getIncompleteTasksWithDdl(): List<TaskEntity>

    @Query(
        "UPDATE tasks SET remind24Sent = 1, updatedAt = :now " +
            "WHERE id = :id AND isCompleted = 0 AND ddl IS NOT NULL AND remind24Sent = 0"
    )
    suspend fun claim24HourReminder(id: Long, now: Long): Int

    @Query(
        "UPDATE tasks SET remind12Sent = 1, updatedAt = :now " +
            "WHERE id = :id AND isCompleted = 0 AND ddl IS NOT NULL AND remind12Sent = 0"
    )
    suspend fun claim12HourReminder(id: Long, now: Long): Int

    @Query("UPDATE tasks SET remind24Sent = 0 WHERE id = :id")
    suspend fun release24HourReminder(id: Long)

    @Query("UPDATE tasks SET remind12Sent = 0 WHERE id = :id")
    suspend fun release12HourReminder(id: Long)

    // ---------------------------------------------------------------- trash

    /**
     * Moves a task into the recycle bin. [source] is [DELETE_SOURCE_USER] when
     * the user removed it, [DELETE_SOURCE_AUTO] for the automatic 24h/48h
     * cleanup.
     */
    @Query(
        "UPDATE tasks SET deletedAt = :now, deleteSource = :source, updatedAt = :now " +
            "WHERE id = :id AND deletedAt IS NULL"
    )
    suspend fun softDeleteTask(id: Long, now: Long, source: Int)

    /** Brings a task back from the recycle bin. */
    @Query(
        "UPDATE tasks SET deletedAt = NULL, deleteSource = 0, restoredAt = :now, " +
            "updatedAt = :now WHERE id = :id"
    )
    suspend fun restoreTask(id: Long, now: Long)

    @Query(
        """
        SELECT t.*, l.name AS listName
        FROM tasks t
        JOIN task_lists l ON l.id = t.listId
        WHERE t.deletedAt IS NOT NULL
        ORDER BY t.deletedAt DESC
        """
    )
    fun observeTrashItems(): Flow<List<TrashItem>>

    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff"
    )
    suspend fun getTrashBefore(cutoff: Long): List<TaskEntity>

    // --------------------------------------------------- automatic cleanup

    /**
     * Unfinished tasks whose deadline passed before [cutoff]. A task restored
     * from the recycle bin gets a fresh 48h grace period after [restoredAt].
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND isCompleted = 0 " +
            "AND ddl IS NOT NULL AND ddl <= :cutoff " +
            "AND (restoredAt IS NULL OR restoredAt <= :cutoff)"
    )
    suspend fun getOverdueIncompleteBefore(cutoff: Long): List<TaskEntity>

    /**
     * Completed tasks whose completion time is older than [cutoff]; restoring a
     * task restarts its 24h window through [restoredAt].
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND isCompleted = 1 " +
            "AND completedAt IS NOT NULL " +
            "AND COALESCE(restoredAt, completedAt) <= :cutoff"
    )
    suspend fun getCompletedBefore(cutoff: Long): List<TaskEntity>

    // ----------------------------------------------------------- attachments

    @Query(
        "SELECT * FROM task_attachments WHERE taskId = :taskId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    fun observeAttachments(taskId: Long): Flow<List<TaskAttachmentEntity>>

    @Query(
        "SELECT * FROM task_attachments WHERE taskId = :taskId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getAttachments(taskId: Long): List<TaskAttachmentEntity>

    @Query("SELECT * FROM task_attachments")
    suspend fun getAllAttachments(): List<TaskAttachmentEntity>

    @Insert
    suspend fun insertAttachment(attachment: TaskAttachmentEntity): Long

    @Delete
    suspend fun deleteAttachment(attachment: TaskAttachmentEntity)

    @Query("DELETE FROM task_attachments WHERE taskId = :taskId")
    suspend fun deleteAttachmentsForTask(taskId: Long)
}
