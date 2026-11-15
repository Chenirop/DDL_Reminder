package com.example.ddlreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ddlreminder.R

@Composable
fun PermissionWarnings(
    notificationsEnabled: Boolean,
    exactAlarmsEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    if (notificationsEnabled && exactAlarmsEnabled) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!notificationsEnabled) {
            PermissionWarning(
                message = stringResource(R.string.notifications_disabled),
                actionLabel = stringResource(R.string.go_enable),
                onClick = onOpenNotificationSettings
            )
        }
        if (!exactAlarmsEnabled) {
            PermissionWarning(
                message = stringResource(R.string.exact_alarms_disabled),
                actionLabel = stringResource(R.string.go_settings),
                onClick = onOpenExactAlarmSettings
            )
        }
    }
}

@Composable
private fun PermissionWarning(
    message: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClick) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
