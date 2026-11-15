package com.example.ddlreminder.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.ddlreminder.data.TaskEntity

class ReminderScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(AlarmManager::class.java)

    fun scheduleTask(task: TaskEntity) {
        cancelTask(task.id)

        val ddl = task.ddl
        if (task.isCompleted || ddl == null) return

        val now = System.currentTimeMillis()
        for (type in ReminderType.entries) {
            val alreadySent = when (type) {
                ReminderType.TWENTY_FOUR_HOURS -> task.remind24Sent
                ReminderType.TWELVE_HOURS -> task.remind12Sent
            }
            if (alreadySent) continue

            val triggerAt = ddl - type.offsetMillis
            // Only future reminder points are scheduled. Missed points are not sent later.
            if (triggerAt > now) {
                setAlarm(triggerAt, pendingIntent(task.id, type))
            }
        }
    }

    fun scheduleAll(tasks: List<TaskEntity>) {
        tasks.forEach(::scheduleTask)
    }

    fun cancelTask(taskId: Long) {
        ReminderType.entries.forEach { cancelReminder(taskId, it) }
    }

    fun cancelReminder(taskId: Long, type: ReminderType) {
        val pi = PendingIntent.getBroadcast(
            appContext,
            ReminderIds.forReminder(taskId, type),
            reminderIntent(taskId, type),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager?.cancel(pi)
            pi.cancel()
        }
    }

    private fun pendingIntent(taskId: Long, type: ReminderType): PendingIntent {
        return PendingIntent.getBroadcast(
            appContext,
            ReminderIds.forReminder(taskId, type),
            reminderIntent(taskId, type),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderIntent(taskId: Long, type: ReminderType): Intent {
        return Intent(appContext, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("ddlreminder://reminder/$taskId/${type.code}")
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_REMINDER_TYPE, type.code)
        }
    }

    private fun setAlarm(triggerAt: Long, pendingIntent: PendingIntent) {
        if (canScheduleExactAlarms(appContext)) {
            try {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                return
            } catch (_: SecurityException) {
                // Permission may have been revoked between the check and this call.
            } catch (_: RuntimeException) {
                // Fall through to the inexact fallback.
            }
        }

        // Graceful degradation: the reminder still fires, but the OS may delay it.
        try {
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Nothing else can be done without crashing the app.
        } catch (_: RuntimeException) {
            // Nothing else can be done without crashing the app.
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.ddlreminder.action.REMIND"
        const val EXTRA_TASK_ID = "reminder_task_id"
        const val EXTRA_REMINDER_TYPE = "reminder_type"

        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val manager = context.getSystemService(AlarmManager::class.java) ?: return false
            return manager.canScheduleExactAlarms()
        }
    }
}
