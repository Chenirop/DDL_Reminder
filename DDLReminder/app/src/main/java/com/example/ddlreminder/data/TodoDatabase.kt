package com.example.ddlreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskListEntity::class, TaskEntity::class, TaskAttachmentEntity::class],
    version = 4,
    exportSchema = false
)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        private const val DB_NAME = "ddl_reminder.db"

        @Volatile
        private var INSTANCE: TodoDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER")
            }
        }

        /**
         * Recycle bin + attachments: tasks are soft deleted instead of removed,
         * and every task can carry a list of locally stored attachments.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER")
                database.execSQL(
                    "ALTER TABLE tasks ADD COLUMN deleteSource INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL("ALTER TABLE tasks ADD COLUMN restoredAt INTEGER")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `mimeType` TEXT,
                        `isImage` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_task_attachments_taskId` " +
                        "ON `task_attachments` (`taskId`)"
                )
            }
        }

        /**
         * Free-form notes attached to a task.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN note TEXT")
            }
        }

        fun get(context: Context): TodoDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TodoDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
