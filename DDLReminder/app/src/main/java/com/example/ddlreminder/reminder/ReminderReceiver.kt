package com.example.ddlreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ddlreminder.data.TodoDatabase
import com.example.ddlreminder.data.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return

        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        val type = ReminderType.fromCode(
            intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_TYPE, -1)
        ) ?: return
        if (taskId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                deliver(context.applicationContext, taskId, type)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, taskId: Long, type: ReminderType) {
        val database = TodoDatabase.get(context)
        val dao = database.taskDao()
        val task = dao.getTask(taskId) ?: return
        val ddl = task.ddl ?: return
        if (task.isCompleted) return

        val alreadySent = when (type) {
            ReminderType.TWENTY_FOUR_HOURS -> task.remind24Sent
            ReminderType.TWELVE_HOURS -> task.remind12Sent
        }
        if (alreadySent) return

        val now = System.currentTimeMillis()
        if (now >= ddl) return

        val triggerAt = ddl - type.offsetMillis
        if (now + TimeFormatSlack < triggerAt) {
            // Alarms should not fire early. Re-arm defensively instead of showing
            // a reminder too soon.
            ReminderScheduler(context).scheduleTask(task)
            return
        }

        // If the user denied notification permission, keep the sent flag unset.
        // There is no useful make-up notification once the alarm point is past.
        if (!NotificationHelper.canPostNotifications(context)) return

        val list = dao.getList(task.listId) ?: return
        val repository = TodoRepository(dao)
        val is24Hour = type == ReminderType.TWENTY_FOUR_HOURS
        val claimed = repository.claimReminder(task.id, is24Hour)
        if (!claimed) return

        val shown = try {
            NotificationHelper.showReminder(context, task, list.name, type)
        } catch (_: RuntimeException) {
            false
        }

        if (!shown) {
            repository.releaseReminder(task.id, is24Hour)
        }
    }

    private companion object {
        const val TimeFormatSlack = 60_000L
    }
}
