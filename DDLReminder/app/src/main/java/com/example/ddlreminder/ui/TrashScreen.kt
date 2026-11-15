package com.example.ddlreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ddlreminder.R
import com.example.ddlreminder.data.DELETE_SOURCE_AUTO
import com.example.ddlreminder.data.TrashItem
import com.example.ddlreminder.util.TimeFormat

/**
 * Recycle bin: the same card stack as the task list, but for soft deleted
 * tasks. Items never disappear on their own from here — they only vanish once
 * the retention window expired or after an explicit permanent delete.
 *
 * The list can be grouped by the source list. Grouping only changes the
 * presentation: restore / retention rules stay untouched.
 */
@Composable
fun TrashScreen(
    modifier: Modifier = Modifier,
    items: List<TrashItem>,
    now: Long,
    retentionDays: Int,
    onRestore: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    onRestoreMany: (Collection<Long>) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<TrashItem?>(null) }
    var grouped by rememberSaveable { mutableStateOf(false) }

    val groups = remember(items) {
        items.groupBy { it.listName.ifBlank { "" } }
            .toList()
            .sortedBy { (name, _) -> name.lowercase() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.trash_retention_hint, retentionDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 2.dp)
        )

        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { grouped = !grouped }) {
                    Text(
                        text = stringResource(
                            if (grouped) R.string.trash_show_all else R.string.trash_group_by_list
                        )
                    )
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = { onRestoreMany(items.map { it.task.id }) }) {
                    Text(stringResource(R.string.trash_restore_all))
                }
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    title = stringResource(R.string.trash_empty_title),
                    hint = stringResource(R.string.trash_empty_hint)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 2.dp,
                    bottom = 24.dp
                )
            ) {
                if (!grouped) {
                    items(items = items, key = { it.task.id }) { item ->
                        TrashCard(
                            item = item,
                            now = now,
                            retentionDays = retentionDays,
                            onRestore = { onRestore(item.task.id) },
                            onDeleteForever = { pendingDelete = item }
                        )
                    }
                } else {
                    groups.forEach { (name, groupItems) ->
                        item(key = "group_$name") {
                            TrashGroupHeader(
                                name = name.ifBlank {
                                    stringResource(R.string.trash_group_unknown)
                                },
                                count = groupItems.size,
                                onRestoreGroup = {
                                    onRestoreMany(groupItems.map { it.task.id })
                                }
                            )
                        }
                        items(items = groupItems, key = { it.task.id }) { item ->
                            TrashCard(
                                item = item,
                                now = now,
                                retentionDays = retentionDays,
                                onRestore = { onRestore(item.task.id) },
                                onDeleteForever = { pendingDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_forever_title)) },
            text = { Text(stringResource(R.string.delete_forever_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteForever(item.task.id)
                        pendingDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete_forever),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TrashGroupHeader(
    name: String,
    count: Int,
    onRestoreGroup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.trash_group_title, name, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        TextButton(onClick = onRestoreGroup) {
            Text(stringResource(R.string.trash_restore_group))
        }
    }
}

@Composable
private fun TrashCard(
    item: TrashItem,
    now: Long,
    retentionDays: Int,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val task = item.task
    val deletedAt = task.deletedAt ?: now
    val remaining = (deletedAt + retentionDays * TimeFormat.DAY_MS - now)
        .coerceAtLeast(0L)
    val mutedColor = MaterialTheme.colorScheme.outline

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        if (task.deleteSource == DELETE_SOURCE_AUTO) {
                            R.string.trash_source_auto
                        } else {
                            R.string.trash_source_user
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Text(
                text = stringResource(
                    R.string.trash_deleted_at,
                    TimeFormat.formatDateTime(deletedAt)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
                modifier = Modifier.padding(top = 3.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (item.listName.isBlank()) {
                        stringResource(R.string.trash_expires_in, TimeFormat.formatRemaining(remaining))
                    } else {
                        stringResource(
                            R.string.trash_list_and_expiry,
                            item.listName,
                            TimeFormat.formatRemaining(remaining)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.restore))
                }
                TextButton(onClick = onDeleteForever) {
                    Text(
                        text = stringResource(R.string.delete_forever),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
