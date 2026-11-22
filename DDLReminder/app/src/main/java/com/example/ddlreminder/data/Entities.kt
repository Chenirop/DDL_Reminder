package com.example.ddlreminder.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Values stored in [TaskEntity.deleteSource]. */
const val DELETE_SOURCE_NONE = 0
const val DELETE_SOURCE_USER = 1
const val DELETE_SOURCE_AUTO = 2

@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Whether tasks of this list may pop up reminder notifications. */
    @ColumnInfo(defaultValue = "1")
    val notificationsEnabled: Boolean = true
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("listId"),
        Index(value = ["listId", "isCompleted"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val listId: Long,
    val title: String,
    val ddl: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val remind24Sent: Boolean = false,
    val remind12Sent: Boolean = false,
    /**
     * User-defined position inside the list. When every task of a list has a
     * null value the list falls back to the automatic DDL ordering; dragging a
     * card assigns explicit positions to the whole list.
     */
    val sortOrder: Int? = null,
    /** Non-null while the task sits in the recycle bin (soft deleted). */
    val deletedAt: Long? = null,
    /** One of [DELETE_SOURCE_NONE], [DELETE_SOURCE_USER], [DELETE_SOURCE_AUTO]. */
    @ColumnInfo(defaultValue = "0")
    val deleteSource: Int = DELETE_SOURCE_NONE,
    /**
     * When the task was last restored from the recycle bin. The automatic
     * cleanup uses it to grant a fresh grace period, otherwise a restored
     * overdue task would be removed again on the very next cleanup run.
     */
    val restoredAt: Long? = null,
    /** Optional free-form note edited in the task editor. */
    val note: String? = null
)

@Entity(
    tableName = "task_attachments",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class TaskAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val taskId: Long,
    /** Name shown to the user, taken from the picked document. */
    val fileName: String,
    /** Absolute path of the private copy stored under filesDir/attachments. */
    val localPath: String,
    val mimeType: String? = null,
    val isImage: Boolean = false,
    val createdAt: Long
)

/** A soft deleted task joined with the name of its list, for the trash screen. */
data class TrashItem(
    @Embedded val task: TaskEntity,
    val listName: String
)
