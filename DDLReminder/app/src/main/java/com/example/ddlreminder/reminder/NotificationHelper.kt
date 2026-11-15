package com.example.ddlreminder.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.ddlreminder.MainActivity
import com.example.ddlreminder.R
import com.example.ddlreminder.data.TaskEntity
import com.example.ddlreminder.util.TimeFormat

object NotificationHelper {

    const val CHANNEL_ID = "ddl_task_reminders"
    const val EXTRA_LIST_ID = "extra_list_id"
    const val EXTRA_TASK_ID = "extra_task_id"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 250L, 150L, 250L)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * @return true only when the notification was actually posted. The caller
     * uses the result to decide whether the one-shot reminder may be marked sent.
     */
    fun showReminder(
        context: Context,
        task: TaskEntity,
        listName: String,
        type: ReminderType
    ): Boolean {
        val ddl = task.ddl ?: return false
        if (!canPostNotifications(context)) return false

        val notificationId = ReminderIds.forReminder(task.id, type)
        val remaining = (ddl - System.currentTimeMillis()).coerceAtLeast(0L)
        val countdown = TimeFormat.formatRemaining(remaining)
        val contentTitle = context.getString(
            R.string.notification_title,
            listName,
            task.title
        )
        val contentText = context.getString(R.string.notification_countdown, countdown)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_LIST_ID, task.listId)
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setVibrate(longArrayOf(0L, 250L, 150L, 250L))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(contentPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}
