package com.example.ddlreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ddlreminder.data.AppPreferences
import com.example.ddlreminder.data.TodoDatabase
import com.example.ddlreminder.data.TodoRepository
import com.example.ddlreminder.data.TrashCleanup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                NotificationHelper.createChannel(appContext)
                val dao = TodoDatabase.get(appContext).taskDao()

                ReminderScheduler(appContext).scheduleAll(
                    dao.getIncompleteTasksWithDdl()
                )
                TrashCleanup.run(TodoRepository(dao), AppPreferences(appContext))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
