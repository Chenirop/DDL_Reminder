package com.example.ddlreminder.reminder

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.ddlreminder.MainActivity
import com.example.ddlreminder.data.TaskEntity
import com.example.ddlreminder.data.TodoDatabase
import com.example.ddlreminder.data.TodoRepository
import com.example.ddlreminder.ui.DdlReminderTheme
import com.example.ddlreminder.util.TimeFormat
import kotlinx.coroutines.launch

/**
 * Full-screen reminder shown over the lock screen. It is launched from the
 * ongoing notification through a full screen intent, so the reminder stays
 * visible until the user explicitly acts on it.
 */
class ReminderAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) {
            finish()
            return
        }

        val repository = TodoRepository(TodoDatabase.get(this).taskDao())

        setContent {
            DdlReminderTheme {
                ReminderAlertContent(
                    taskId = taskId,
                    loadTask = { repository.getTask(it) },
                    loadListName = { listId -> repository.getList(listId)?.name.orEmpty() },
                    onComplete = {
                        lifecycleScope.launch {
                            repository.markCompleted(taskId)
                            ReminderScheduler(this@ReminderAlertActivity).cancelTask(taskId)
                            NotificationHelper.cancelTask(this@ReminderAlertActivity, taskId)
                            finish()
                        }
                    },
                    onDismissAlert = {
                        NotificationHelper.cancelTask(this@ReminderAlertActivity, taskId)
                        finish()
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this@ReminderAlertActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun ReminderAlertContent(
    taskId: Long,
    loadTask: suspend (Long) -> TaskEntity?,
    loadListName: suspend (Long) -> String,
    onComplete: () -> Unit,
    onDismissAlert: () -> Unit,
    onOpenApp: () -> Unit
) {
    var task by remember { mutableStateOf<TaskEntity?>(null) }
    var listName by remember { mutableStateOf("") }

    LaunchedEffect(taskId) {
        task = loadTask(taskId)
        task?.let { listName = loadListName(it.listId) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xE6000000)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DDL 提醒",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = task?.title ?: "任务",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (listName.isBlank()) "清单" else listName,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            val ddl = task?.ddl
            if (ddl != null) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "截止 " + TimeFormat.formatDateTime(ddl),
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = TimeFormat.countdownText(ddl, System.currentTimeMillis()),
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("完成")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismissAlert,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("关闭提醒")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenApp) {
                Text("打开应用", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
