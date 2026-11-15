package com.example.ddlreminder.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ddlreminder.util.TimeFormat
import java.util.concurrent.TimeUnit

object CleanupScheduler {

    private const val PERIODIC_WORK_NAME = "cleanup_completed_tasks_periodic"
    private const val ONE_TIME_TASK_PREFIX = "cleanup_completed_task_"
    private const val ONE_TIME_OVERDUE_PREFIX = "cleanup_overdue_task_"

    fun enqueuePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Deletes a completed task 24h after it was checked. */
    fun scheduleTaskCleanup(context: Context, taskId: Long, completedAt: Long) {
        val elapsed = System.currentTimeMillis() - completedAt
        val delay = (TimeFormat.DAY_MS - elapsed).coerceAtLeast(0L)
        enqueueOneTime(context, oneTimeName(taskId), delay)
    }

    fun cancelTaskCleanup(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(oneTimeName(taskId))
    }

    /** Removes an unfinished task at [dueAt] (48h after the deadline by default). */
    fun scheduleOverdueCleanup(context: Context, taskId: Long, dueAt: Long) {
        val delay = (dueAt - System.currentTimeMillis()).coerceAtLeast(0L)
        enqueueOneTime(context, overdueName(taskId), delay)
    }

    fun cancelOverdueCleanup(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(overdueName(taskId))
    }

    private fun enqueueOneTime(context: Context, name: String, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<CleanupWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            name,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun oneTimeName(taskId: Long): String = "$ONE_TIME_TASK_PREFIX$taskId"

    private fun overdueName(taskId: Long): String = "$ONE_TIME_OVERDUE_PREFIX$taskId"
}
