package com.example.ddlreminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ddlreminder.R
import com.example.ddlreminder.data.ListSummary

/**
 * Compact popup anchored to the bottom bar: pick an existing list, rename or
 * delete it, or create a new one right at the bottom of the same window.
 */
@Composable
fun ListSwitcherDialog(
    summaries: List<ListSummary>,
    selectedListId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (ListSummary) -> Unit,
    onDelete: (ListSummary) -> Unit,
    onToggleNotifications: (ListSummary) -> Unit,
    onDismiss: () -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column {
                Text(
                    text = stringResource(R.string.switch_list),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
                )

                if (summaries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.list_empty_picker),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(items = summaries, key = { it.id }) { summary ->
                            ListPickerRow(
                                summary = summary,
                                selected = summary.id == selectedListId,
                                onClick = { onSelect(summary.id) },
                                onRename = { onRename(summary) },
                                onDelete = { onDelete(summary) },
                                onToggleNotifications = { onToggleNotifications(summary) }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                if (adding) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { if (it.length <= 40) newName = it },
                            placeholder = { Text(stringResource(R.string.new_list_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = newName.isNotBlank(),
                            onClick = {
                                onCreate(newName.trim())
                                newName = ""
                                adding = false
                            }
                        ) {
                            Text(stringResource(R.string.add))
                        }
                    }
                } else {
                    TextButton(
                        onClick = { adding = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.new_list))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListPickerRow(
    summary: ListSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleNotifications: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.size(18.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 4.dp)
        ) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1
            )
            if (summary.pendingCount > 0) {
                Text(
                    text = stringResource(R.string.list_pending_count, summary.pendingCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        IconButton(
            onClick = onToggleNotifications,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(
                    if (summary.notificationsEnabled) {
                        R.string.list_notifications_on
                    } else {
                        R.string.list_notifications_off
                    }
                ),
                tint = if (summary.notificationsEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more),
                tint = MaterialTheme.colorScheme.outline
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename_list)) },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.delete_list),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                }
            )
        }
    }
}
