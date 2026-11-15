package com.example.ddlreminder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ddlreminder.data.AppPreferences
import com.example.ddlreminder.data.TodoDatabase
import com.example.ddlreminder.data.TodoRepository
import com.example.ddlreminder.data.TrashCleanup

class CleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = TodoRepository(TodoDatabase.get(applicationContext).taskDao())
            TrashCleanup.run(
                repository = repository,
                preferences = AppPreferences(applicationContext),
                now = System.currentTimeMillis()
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
