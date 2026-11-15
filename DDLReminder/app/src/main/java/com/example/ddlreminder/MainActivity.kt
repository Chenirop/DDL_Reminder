package com.example.ddlreminder

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ddlreminder.reminder.NotificationHelper
import com.example.ddlreminder.reminder.ReminderScheduler
import com.example.ddlreminder.ui.DdlReminderTheme
import com.example.ddlreminder.ui.TodoApp
import com.example.ddlreminder.ui.TodoViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: TodoViewModel by viewModels()
    private val notificationsEnabled = MutableStateFlow(false)
    private val exactAlarmsEnabled = MutableStateFlow(true)

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    private val permissionPreferences by lazy {
        getSharedPreferences("permission_preferences", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            refreshPermissionStates()
        }

        handleNotificationIntent(intent)
        refreshPermissionStates()

        setContent {
            val notifications by notificationsEnabled.collectAsStateWithLifecycle()
            val exactAlarms by exactAlarmsEnabled.collectAsStateWithLifecycle()

            DdlReminderTheme {
                TodoApp(
                    viewModel = viewModel,
                    notificationsEnabled = notifications,
                    exactAlarmsEnabled = exactAlarms,
                    onOpenNotificationSettings = ::openNotificationSettings,
                    onOpenExactAlarmSettings = ::openExactAlarmSettings
                )
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
        viewModel.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val listId = intent?.getLongExtra(NotificationHelper.EXTRA_LIST_ID, -1L) ?: -1L
        if (listId > 0L) {
            viewModel.openListFromNotification(listId)
            intent?.removeExtra(NotificationHelper.EXTRA_LIST_ID)
            intent?.removeExtra(NotificationHelper.EXTRA_TASK_ID)
        }
    }

    private fun refreshPermissionStates() {
        notificationsEnabled.value = canPostNotifications()
        exactAlarmsEnabled.value = ReminderScheduler.canScheduleExactAlarms(this)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (permissionPreferences.getBoolean(KEY_NOTIFICATION_ASKED, false)) return

        permissionPreferences.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppDetails()
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Fall through to the app details page.
            }
        }
        openAppDetails()
    }

    private fun openAppDetails() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // There is no further settings page to open.
        }
    }

    private companion object {
        const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
    }
}
