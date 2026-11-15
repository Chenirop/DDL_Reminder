package com.example.ddlreminder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ddlreminder.R
import com.example.ddlreminder.data.TaskAttachmentEntity
import com.example.ddlreminder.data.TaskEntity
import com.example.ddlreminder.util.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    task: TaskEntity?,
    attachments: List<TaskAttachmentEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long?, String?) -> Unit,
    onDelete: () -> Unit,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (TaskAttachmentEntity) -> Unit,
    onOpenAttachment: (TaskAttachmentEntity) -> Unit
) {
    val editorKey = task?.id ?: -1L
    var title by remember(editorKey) { mutableStateOf(task?.title.orEmpty()) }
    var hasDdl by remember(editorKey) { mutableStateOf(task?.ddl != null) }
    var ddlMillis by remember(editorKey) { mutableStateOf(task?.ddl) }
    var note by remember(editorKey) { mutableStateOf(task?.note.orEmpty()) }
    var showDatePicker by remember(editorKey) { mutableStateOf(false) }
    var showTimePicker by remember(editorKey) { mutableStateOf(false) }
    var showDeleteConfirm by remember(editorKey) { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSave = title.trim().isNotEmpty()

    // The picked document is copied into the app's private storage right away,
    // so the attachment keeps working without a persistable URI permission.
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onAddAttachment)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(
                    if (task == null) R.string.add_task else R.string.edit_task
                ),
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 120) title = it },
                label = { Text(stringResource(R.string.task_title)) },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            Text(
                text = stringResource(R.string.ddl_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )

            val currentDdl = ddlMillis
            if (hasDdl && currentDdl != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(TimeFormat.formatDate(currentDdl))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(TimeFormat.formatTime(currentDdl))
                    }
                    IconButton(
                        onClick = {
                            hasDdl = false
                            ddlMillis = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_ddl)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.ddl_reminder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_ddl))
                }
                Text(
                    text = stringResource(R.string.ddl_optional_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = stringResource(R.string.notes_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= NOTE_MAX_LENGTH) note = it },
                label = { Text(stringResource(R.string.note_label)) },
                placeholder = { Text(stringResource(R.string.note_hint)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            OutlinedButton(
                onClick = { attachmentPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_attachment))
            }

            if (attachments.isEmpty()) {
                Text(
                    text = stringResource(R.string.attachment_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                attachments.forEach { attachment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAttachment(attachment) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    if (attachment.isImage) {
                                        R.string.attachment_type_image
                                    } else {
                                        R.string.attachment_type_file
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onRemoveAttachment(attachment) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove_attachment)
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (task != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            title.trim(),
                            if (hasDdl) ddlMillis else null,
                            note.trim().ifEmpty { null }
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (showDatePicker) {
        val baseMillis = ddlMillis ?: defaultDdlMillis()
        val baseDate = Instant.ofEpochMilli(baseMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val initialSelection = baseDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialSelection
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDateMillis = datePickerState.selectedDateMillis
                        if (selectedDateMillis != null) {
                            val selectedDate = Instant.ofEpochMilli(selectedDateMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            // Picking a date only changes the day: the time defaults
                            // to 23:59 and is edited separately by the user.
                            ddlMillis = selectedDate
                                .atTime(DEFAULT_TIME)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            hasDdl = true
                            showDatePicker = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val timeSource = ddlMillis
    if (showTimePicker && timeSource != null) {
        val localTime = Instant.ofEpochMilli(timeSource)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

        TimeWheelDialog(
            initialHour = localTime.hour,
            initialMinute = localTime.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val current = ddlMillis ?: return@TimeWheelDialog
                val currentDate = Instant.ofEpochMilli(current)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                ddlMillis = currentDate
                    .atTime(hour, minute)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                hasDdl = true
                showTimePicker = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_task)) },
            text = { Text(stringResource(R.string.delete_task_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Time used whenever the user picks a day without touching the time picker. */
private val DEFAULT_TIME: LocalTime = LocalTime.of(23, 59)

/** Free-form notes stay short enough to render without pagination. */
private const val NOTE_MAX_LENGTH = 2000

private fun defaultDdlMillis(): Long {
    return LocalDate.now()
        .plusDays(1)
        .atTime(DEFAULT_TIME)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
