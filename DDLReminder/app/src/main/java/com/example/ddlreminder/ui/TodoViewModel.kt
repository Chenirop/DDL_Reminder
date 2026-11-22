package com.example.ddlreminder.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ddlreminder.data.AppPreferences
import com.example.ddlreminder.data.BackgroundRepository
import com.example.ddlreminder.data.BackgroundState
import com.example.ddlreminder.data.BackupCodec
import com.example.ddlreminder.data.DELETE_SOURCE_USER
import com.example.ddlreminder.data.ListSummary
import com.example.ddlreminder.data.RenderScheme
import com.example.ddlreminder.data.TaskAttachmentEntity
import com.example.ddlreminder.data.TaskEntity
import com.example.ddlreminder.data.TaskListEntity
import com.example.ddlreminder.data.TodoDatabase
import com.example.ddlreminder.data.TodoRepository
import com.example.ddlreminder.data.TrashCleanup
import com.example.ddlreminder.data.TrashItem
import com.example.ddlreminder.reminder.NotificationHelper
import com.example.ddlreminder.reminder.ReminderScheduler
import com.example.ddlreminder.util.AttachmentStore
import com.example.ddlreminder.util.TaskOrdering
import com.example.ddlreminder.util.TaskRetention
import com.example.ddlreminder.util.TimeFormat
import com.example.ddlreminder.work.CleanupScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppStats(
    val listCount: Int,
    val taskCount: Int
)

data class ImportPreview(
    val fileName: String,
    val listCount: Int,
    val taskCount: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = TodoRepository(TodoDatabase.get(appContext).taskDao())
    private val reminderScheduler = ReminderScheduler(appContext)
    private val preferences = AppPreferences(appContext)
    private val backgroundRepository = BackgroundRepository(appContext)

    /** Background picture state, observed by the whole UI layer. */
    val background: StateFlow<BackgroundState> = backgroundRepository.state

    /** Non null while the user picks the visible part of a new background. */
    private val _backgroundCrop = MutableStateFlow<Bitmap?>(null)
    val backgroundCrop: StateFlow<Bitmap?> = _backgroundCrop.asStateFlow()

    /** Opacity of the glass card fill; 0 = fully transparent, 1 = opaque. */
    private val _glassFillAlpha = MutableStateFlow(preferences.glassFillAlpha)
    val glassFillAlpha: StateFlow<Float> = _glassFillAlpha.asStateFlow()

    /**
     * Rendering scheme of the task cards. Solid is the universally supported
     * fallback, Liquid the original backdrop glass and LiquidWinUI the WinUI
     * inspired glass (hash blur, rim refraction, dispersion, edge light).
     */
    private val _renderScheme = MutableStateFlow(preferences.renderScheme)
    val renderScheme: StateFlow<RenderScheme> = _renderScheme.asStateFlow()

    /** WinUI glass fill opacity, independent of the other two schemes. */
    private val _winuiFillAlpha = MutableStateFlow(preferences.winuiFillAlpha)
    val winuiFillAlpha: StateFlow<Float> = _winuiFillAlpha.asStateFlow()

    /** WinUI backdrop blur radius in dp. 0 keeps the backdrop sharp. */
    private val _winuiBlur = MutableStateFlow(preferences.winuiBlur)
    val winuiBlur: StateFlow<Float> = _winuiBlur.asStateFlow()

    private val _winuiRefraction = MutableStateFlow(preferences.winuiRefraction)
    val winuiRefraction: StateFlow<Float> = _winuiRefraction.asStateFlow()

    private val _winuiChromatic = MutableStateFlow(preferences.winuiChromatic)
    val winuiChromatic: StateFlow<Float> = _winuiChromatic.asStateFlow()

    private val _winuiGlare = MutableStateFlow(preferences.winuiGlare)
    val winuiGlare: StateFlow<Float> = _winuiGlare.asStateFlow()

    private val _winuiPerformanceMode = MutableStateFlow(preferences.winuiPerformanceMode)
    val winuiPerformanceMode: StateFlow<Boolean> = _winuiPerformanceMode.asStateFlow()

    /** Relative font scale of the overdue removal marker on task cards. */
    private val _overdueTextScale = MutableStateFlow(preferences.overdueTextScale)
    val overdueTextScale: StateFlow<Float> = _overdueTextScale.asStateFlow()

    private val _selectedListId = MutableStateFlow<Long?>(null)
    val selectedListId: StateFlow<Long?> = _selectedListId.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _userName = MutableStateFlow(preferences.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    // ------------------------------------------------------------ trash state

    private val _keepAutoDeletedInTrash =
        MutableStateFlow(preferences.keepAutoDeletedInTrash)
    val keepAutoDeletedInTrash: StateFlow<Boolean> = _keepAutoDeletedInTrash.asStateFlow()

    private val _trashRetentionDays = MutableStateFlow(preferences.trashRetentionDays)
    val trashRetentionDays: StateFlow<Int> = _trashRetentionDays.asStateFlow()

    /**
     * Hours a task that is restored after being overdue for more than 48h may
     * stay in the list. Only those restored tasks use this window.
     */
    private val _trashRestoreRetentionHours =
        MutableStateFlow(preferences.trashRestoreRetentionHours)
    val trashRestoreRetentionHours: StateFlow<Int> =
        _trashRestoreRetentionHours.asStateFlow()

    /** Custom text colors (ARGB) of the cards; 0 means "theme default". */
    private val _tableTextColor = MutableStateFlow(preferences.tableTextColor)
    val tableTextColor: StateFlow<Int> = _tableTextColor.asStateFlow()

    private val _settingsTextColor = MutableStateFlow(preferences.settingsTextColor)
    val settingsTextColor: StateFlow<Int> = _settingsTextColor.asStateFlow()

    /** Solid rendering scheme: independent fill opacity, colour and stroke. */
    private val _solidFillAlpha = MutableStateFlow(preferences.solidFillAlpha)
    val solidFillAlpha: StateFlow<Float> = _solidFillAlpha.asStateFlow()

    private val _solidFillColor = MutableStateFlow(preferences.solidFillColor)
    val solidFillColor: StateFlow<Int> = _solidFillColor.asStateFlow()

    private val _solidStrokeWidth = MutableStateFlow(preferences.solidStrokeWidth)
    val solidStrokeWidth: StateFlow<Float> = _solidStrokeWidth.asStateFlow()

    /**
     * Tasks currently visible in the recycle bin. User deletions are always
     * listed; automatically removed tasks only when the matching setting is on.
     * Expired entries are hidden immediately, even before the cleanup runs.
     */
    val trashItems: StateFlow<List<TrashItem>> = combine(
        repository.observeTrashItems(),
        _keepAutoDeletedInTrash,
        _trashRetentionDays,
        _now
    ) { items, keepAutoDeleted, retentionDays, currentTime ->
        val cutoff = currentTime - retentionDays * TimeFormat.DAY_MS
        items.filter { item ->
            val deletedAt = item.task.deletedAt ?: return@filter false
            deletedAt > cutoff && (keepAutoDeleted || item.task.deleteSource == DELETE_SOURCE_USER)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --------------------------------------------------------- editor state

    private val _editorAttachments = MutableStateFlow<List<TaskAttachmentEntity>>(emptyList())
    val editorAttachments: StateFlow<List<TaskAttachmentEntity>> =
        _editorAttachments.asStateFlow()

    private var editorTaskId: Long? = null

    /** Attachments removed while editing; only deleted once the task is saved. */
    private val removedAttachments = mutableListOf<TaskAttachmentEntity>()

    private var pendingImport: BackupCodec.BackupDocument? = null

    val listSummaries: StateFlow<List<ListSummary>> = repository
        .observeListSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dataStats: StateFlow<AppStats> = combine(
        listSummaries,
        repository.observeTaskCount()
    ) { lists, taskCount ->
        AppStats(listCount = lists.size, taskCount = taskCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStats(0, 0))

    val selectedList: StateFlow<TaskListEntity?> = _selectedListId
        .flatMapLatest { listId ->
            if (listId == null) flowOf(null) else repository.observeList(listId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tasks: StateFlow<List<TaskEntity>> = combine(
        _selectedListId.flatMapLatest { listId ->
            if (listId == null) flowOf(emptyList()) else repository.observeTasks(listId)
        },
        _now,
        _trashRestoreRetentionHours
    ) { taskList, currentTime, restoreHours ->
        TaskOrdering.visibleAndSorted(
            taskList,
            currentTime,
            restoreHours * TimeFormat.HOUR_MS
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        restoreSelectedList()
        startMinuteTicker()
        viewModelScope.launch { backgroundRepository.loadStored() }
    }

    private fun restoreSelectedList() {
        viewModelScope.launch {
            val savedId = preferences.lastListId
            val saved = if (savedId > 0) repository.getList(savedId) else null
            if (saved != null) {
                _selectedListId.value = saved.id
            } else {
                repository.getFirstList()?.let { openList(it.id) }
            }
        }
    }

    fun onForeground() {
        _now.value = System.currentTimeMillis()
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            TrashCleanup.run(repository, preferences, currentTime)
            reminderScheduler.scheduleAll(repository.getIncompleteTasksWithDdl())
        }
    }

    fun openList(listId: Long) {
        _selectedListId.value = listId
        preferences.lastListId = listId
    }

    fun openListFromNotification(listId: Long) {
        viewModelScope.launch {
            if (repository.getList(listId) != null) {
                openList(listId)
            }
        }
    }

    fun createList(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val newId = repository.createList(cleanName, System.currentTimeMillis())
            openList(newId)
        }
    }

    fun renameList(listId: Long, name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            repository.renameList(listId, cleanName, System.currentTimeMillis())
        }
    }

    fun deleteList(listId: Long) {
        viewModelScope.launch {
            val tasks = repository.getTasksForList(listId)
            tasks.forEach { task ->
                reminderScheduler.cancelTask(task.id)
                CleanupScheduler.cancelTaskCleanup(appContext, task.id)
                CleanupScheduler.cancelOverdueCleanup(appContext, task.id)
                repository.getAttachments(task.id).forEach { attachment ->
                    AttachmentStore.delete(attachment.localPath)
                }
            }
            repository.deleteList(listId)
            if (_selectedListId.value == listId) {
                _selectedListId.value = null
                preferences.lastListId = AppPreferences.NO_LIST
                repository.getFirstList()?.let { openList(it.id) }
            }
        }
    }

    fun saveTask(listId: Long, taskId: Long?, title: String, ddl: Long?, note: String?) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val cleanNote = note?.trim()?.ifEmpty { null }

        // Take a snapshot synchronously: the editor sheet closes right after
        // this call and would otherwise clear the staged attachments first.
        val attachmentSnapshot = _editorAttachments.value.toList()
        val removedSnapshot = removedAttachments.toList()
        resetEditorState()

        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val savedId = if (taskId == null) {
                val newTask = TaskEntity(
                    listId = listId,
                    title = cleanTitle,
                    ddl = ddl,
                    note = cleanNote,
                    createdAt = currentTime,
                    updatedAt = currentTime
                )
                val newId = repository.createTask(newTask)
                // Keep the manual ordering: append new tasks at the very end.
                val maxOrder = repository.maxSortOrder(listId)
                if (maxOrder != null) {
                    repository.setTaskSortOrder(newId, maxOrder + 1, currentTime)
                }
                newId
            } else {
                val existing = repository.getTask(taskId) ?: return@launch
                val updated = existing.copy(
                    title = cleanTitle,
                    ddl = ddl,
                    note = cleanNote,
                    remind24Sent = false,
                    remind12Sent = false,
                    updatedAt = currentTime
                )
                reminderScheduler.cancelTask(taskId)
                repository.updateTask(updated)
                taskId
            }

            attachmentSnapshot.forEach { attachment ->
                if (attachment.id == 0L) {
                    repository.addAttachment(attachment.copy(taskId = savedId))
                }
            }
            removedSnapshot.forEach { attachment ->
                repository.deleteAttachment(attachment)
                AttachmentStore.delete(attachment.localPath)
            }

            repository.getTask(savedId)?.let { saved ->
                reminderScheduler.scheduleTask(saved)
                scheduleOverdueCleanup(saved)
            }
        }
    }

    fun setTaskCompleted(task: TaskEntity, completed: Boolean) {
        if (task.isCompleted == completed) return

        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            if (completed) {
                repository.markCompleted(task.id, currentTime)
                reminderScheduler.cancelTask(task.id)
                CleanupScheduler.cancelOverdueCleanup(appContext, task.id)
                CleanupScheduler.scheduleTaskCleanup(appContext, task.id, currentTime)
            } else {
                repository.markIncomplete(task.id, currentTime)
                CleanupScheduler.cancelTaskCleanup(appContext, task.id)
                reminderScheduler.cancelTask(task.id)
                repository.getTask(task.id)?.let { restored ->
                    reminderScheduler.scheduleTask(restored)
                    scheduleOverdueCleanup(restored)
                }
            }
        }
    }

    /** Moves a task to the recycle bin (the old "delete" behaviour). */
    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            reminderScheduler.cancelTask(taskId)
            CleanupScheduler.cancelTaskCleanup(appContext, taskId)
            CleanupScheduler.cancelOverdueCleanup(appContext, taskId)
            repository.moveTaskToTrash(
                id = taskId,
                now = System.currentTimeMillis(),
                source = DELETE_SOURCE_USER
            )
            _message.value = "已移入回收站"
        }
    }

    fun restoreTask(taskId: Long) {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            repository.restoreTask(taskId, currentTime)
            val restored = repository.getTask(taskId) ?: return@launch
            if (restored.isCompleted) {
                // The restored task gets a fresh 24h visibility window.
                CleanupScheduler.scheduleTaskCleanup(appContext, taskId, currentTime)
            } else {
                reminderScheduler.scheduleTask(restored)
                scheduleOverdueCleanup(restored)
            }
            _message.value = "已恢复任务"
        }
    }

    fun deleteTaskForever(taskId: Long) {
        viewModelScope.launch {
            TrashCleanup.permanentlyDelete(repository, taskId)
            _message.value = "已彻底删除"
        }
    }

    /** Restores several recycle-bin entries at once (group or whole bin). */
    fun restoreTasks(taskIds: Collection<Long>) {
        if (taskIds.isEmpty()) return
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            var restoredCount = 0
            taskIds.forEach { taskId ->
                repository.restoreTask(taskId, currentTime)
                val restored = repository.getTask(taskId) ?: return@forEach
                restoredCount++
                if (restored.isCompleted) {
                    CleanupScheduler.scheduleTaskCleanup(appContext, taskId, currentTime)
                } else {
                    reminderScheduler.scheduleTask(restored)
                    scheduleOverdueCleanup(restored)
                }
            }
            _message.value = "已恢复 $restoredCount 个事件"
        }
    }

    // ------------------------------------------------------------ editor

    /** Loads the attachments of [taskId]; `null` prepares a brand new task. */
    fun openTaskEditor(taskId: Long?) {
        discardEditorState()
        editorTaskId = taskId
        if (taskId == null) return
        viewModelScope.launch {
            val loaded = repository.getAttachments(taskId)
            if (editorTaskId == taskId) {
                _editorAttachments.value = loaded
            }
        }
    }

    /** Leaves the editor without saving: copies that were never committed go. */
    fun closeTaskEditor() {
        val pending = _editorAttachments.value.filter { it.id == 0L }
        pending.forEach { AttachmentStore.delete(it.localPath) }
        resetEditorState()
    }

    fun addEditorAttachment(uri: Uri) {
        viewModelScope.launch {
            val attachment = withContext(Dispatchers.IO) {
                runCatching { AttachmentStore.import(appContext, uri) }
            }
            attachment.onSuccess { created ->
                _editorAttachments.update { it + created }
            }.onFailure {
                _message.value = "无法读取所选文件"
            }
        }
    }

    fun removeEditorAttachment(attachment: TaskAttachmentEntity) {
        if (attachment.id > 0L) {
            removedAttachments.add(attachment)
        } else {
            AttachmentStore.delete(attachment.localPath)
        }
        _editorAttachments.update { current ->
            current.filterNot { it == attachment }
        }
    }

    fun openAttachment(attachment: TaskAttachmentEntity) {
        if (!AttachmentStore.open(appContext, attachment)) {
            _message.value = "没有可以打开该附件的应用"
        }
    }

    private fun resetEditorState() {
        editorTaskId = null
        removedAttachments.clear()
        _editorAttachments.value = emptyList()
    }

    private fun discardEditorState() {
        val pending = _editorAttachments.value.filter { it.id == 0L }
        pending.forEach { AttachmentStore.delete(it.localPath) }
        resetEditorState()
    }

    // ------------------------------------------------------ trash settings

    fun setKeepAutoDeletedInTrash(enabled: Boolean) {
        _keepAutoDeletedInTrash.value = enabled
        preferences.keepAutoDeletedInTrash = enabled
    }

    fun setTrashRetentionDays(days: Int) {
        val clamped = days.coerceIn(
            AppPreferences.MIN_TRASH_RETENTION_DAYS,
            AppPreferences.MAX_TRASH_RETENTION_DAYS
        )
        _trashRetentionDays.value = clamped
        preferences.trashRetentionDays = clamped
    }

    /** Hours an already expired task may stay after being restored. */
    fun setTrashRestoreRetentionHours(hours: Int) {
        val clamped = hours.coerceIn(
            AppPreferences.MIN_TRASH_RESTORE_RETENTION_HOURS,
            AppPreferences.MAX_TRASH_RESTORE_RETENTION_HOURS
        )
        _trashRestoreRetentionHours.value = clamped
        preferences.trashRestoreRetentionHours = clamped
        // The new value must take effect immediately: re-arm the cleanup for
        // every unfinished task that carries a deadline.
        viewModelScope.launch {
            repository.getAllTasks()
                .filter { !it.isCompleted }
                .forEach { scheduleOverdueCleanup(it) }
        }
    }

    /** Pass [AppPreferences.NO_COLOR] to fall back to the theme default. */
    fun setTableTextColor(argb: Int) {
        _tableTextColor.value = argb
        preferences.tableTextColor = argb
    }

    fun setSettingsTextColor(argb: Int) {
        _settingsTextColor.value = argb
        preferences.settingsTextColor = argb
    }

    fun persistTaskOrder(listId: Long, orderedTaskIds: List<Long>) {
        if (orderedTaskIds.isEmpty()) return
        viewModelScope.launch {
            repository.applyTaskOrder(orderedTaskIds, System.currentTimeMillis())
        }
    }

    fun resetTaskOrder(listId: Long) {
        viewModelScope.launch {
            repository.clearTaskOrder(listId)
        }
    }

    fun updateUserName(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        preferences.userName = cleanName
        _userName.value = cleanName
    }

    fun setBackground(uri: Uri) {
        viewModelScope.launch {
            val applied = backgroundRepository.setBackground(uri)
            _message.value = if (applied) "背景图片已更新" else "无法读取所选图片"
        }
    }

    fun restoreDefaultBackground() {
        backgroundRepository.clear()
        _message.value = "已恢复默认背景"
    }

    /** Loads [uri] and opens the crop dialog instead of applying it directly. */
    fun prepareBackgroundCrop(uri: Uri) {
        viewModelScope.launch {
            val source = backgroundRepository.loadForCrop(uri)
            if (source != null) {
                _backgroundCrop.value = source
            } else {
                _message.value = "无法读取所选图片"
            }
        }
    }

    fun cancelBackgroundCrop() {
        _backgroundCrop.value = null
    }

    /** Persists the cropped area as the new background picture. */
    fun applyBackgroundCrop(bitmap: Bitmap) {
        viewModelScope.launch {
            val applied = backgroundRepository.applyCropped(bitmap)
            _backgroundCrop.value = null
            _message.value = if (applied) "背景图片已更新" else "无法保存背景图片"
        }
    }

    /**
     * Turns pop-up reminders on/off for one whole list. Disabling also removes
     * notifications that are already posted.
     */
    fun setListNotifications(listId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.setListNotifications(listId, enabled, now)
            repository.getTasksForList(listId).forEach { task ->
                if (!enabled) {
                    reminderScheduler.cancelTask(task.id)
                    NotificationHelper.cancelTask(appContext, task.id)
                } else if (!task.isCompleted && task.ddl != null) {
                    reminderScheduler.scheduleTask(task)
                }
            }
            _message.value = if (enabled) "已开启该清单的提醒" else "已关闭该清单的提醒"
        }
    }

    /** [alpha] is the fill opacity (0 transparent .. 1 opaque). */
    fun setGlassFillAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(
            AppPreferences.MIN_GLASS_FILL_ALPHA,
            AppPreferences.MAX_GLASS_FILL_ALPHA
        )
        _glassFillAlpha.value = clamped
        preferences.glassFillAlpha = clamped
    }

    /** Picks one of the three rendering schemes; persisted immediately. */
    fun setRenderScheme(scheme: RenderScheme) {
        _renderScheme.value = scheme
        preferences.renderScheme = scheme
    }

    /** WinUI scheme tint opacity (0 transparent .. 1 opaque). */
    fun setWinuiFillAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(
            AppPreferences.MIN_WINUI_FILL_ALPHA,
            AppPreferences.MAX_WINUI_FILL_ALPHA
        )
        _winuiFillAlpha.value = clamped
        preferences.winuiFillAlpha = clamped
    }

    /** WinUI backdrop blur radius in dp, 0..24. */
    fun setWinuiBlur(blur: Float) {
        val clamped = blur.coerceIn(
            AppPreferences.MIN_WINUI_BLUR,
            AppPreferences.MAX_WINUI_BLUR
        )
        _winuiBlur.value = clamped
        preferences.winuiBlur = clamped
    }

    fun setWinuiRefraction(value: Float) {
        val clamped = value.coerceIn(
            AppPreferences.MIN_WINUI_UNIT,
            AppPreferences.MAX_WINUI_UNIT
        )
        _winuiRefraction.value = clamped
        preferences.winuiRefraction = clamped
    }

    fun setWinuiChromatic(value: Float) {
        val clamped = value.coerceIn(
            AppPreferences.MIN_WINUI_UNIT,
            AppPreferences.MAX_WINUI_UNIT
        )
        _winuiChromatic.value = clamped
        preferences.winuiChromatic = clamped
    }

    fun setWinuiGlare(value: Float) {
        val clamped = value.coerceIn(
            AppPreferences.MIN_WINUI_UNIT,
            AppPreferences.MAX_WINUI_UNIT
        )
        _winuiGlare.value = clamped
        preferences.winuiGlare = clamped
    }

    fun setWinuiPerformanceMode(enabled: Boolean) {
        _winuiPerformanceMode.value = enabled
        preferences.winuiPerformanceMode = enabled
    }

    /** Solid scheme fill opacity (0 transparent .. 1 opaque). */
    fun setSolidFillAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(
            AppPreferences.MIN_SOLID_FILL_ALPHA,
            AppPreferences.MAX_SOLID_FILL_ALPHA
        )
        _solidFillAlpha.value = clamped
        preferences.solidFillAlpha = clamped
    }

    /** ARGB fill colour of the solid scheme. */
    fun setSolidFillColor(argb: Int) {
        _solidFillColor.value = argb
        preferences.solidFillColor = argb
    }

    /** Highlight stroke width (dp) of the solid scheme; 0 hides the stroke. */
    fun setSolidStrokeWidth(width: Float) {
        val clamped = width.coerceIn(
            AppPreferences.MIN_SOLID_STROKE_WIDTH,
            AppPreferences.MAX_SOLID_STROKE_WIDTH
        )
        _solidStrokeWidth.value = clamped
        preferences.solidStrokeWidth = clamped
    }

    /** [scale] is relative to the unscaled label size, capped at 1.0. */
    fun setOverdueTextScale(scale: Float) {
        val clamped = scale.coerceIn(
            AppPreferences.MIN_OVERDUE_TEXT_SCALE,
            AppPreferences.MAX_OVERDUE_TEXT_SCALE
        )
        _overdueTextScale.value = clamped
        preferences.overdueTextScale = clamped
    }

    /**
     * Puts every preference back to its default. Lists, tasks, trash items and
     * the user name are untouched; only appearance and reminder settings reset.
     */
    fun resetAllSettings() {
        preferences.resetToDefaults()
        backgroundRepository.clear()
        _backgroundCrop.value = null
        _glassFillAlpha.value = preferences.glassFillAlpha
        _renderScheme.value = preferences.renderScheme
        _winuiFillAlpha.value = preferences.winuiFillAlpha
        _winuiBlur.value = preferences.winuiBlur
        _winuiRefraction.value = preferences.winuiRefraction
        _winuiChromatic.value = preferences.winuiChromatic
        _winuiGlare.value = preferences.winuiGlare
        _winuiPerformanceMode.value = preferences.winuiPerformanceMode
        _overdueTextScale.value = preferences.overdueTextScale
        _keepAutoDeletedInTrash.value = preferences.keepAutoDeletedInTrash
        _trashRetentionDays.value = preferences.trashRetentionDays
        _trashRestoreRetentionHours.value = preferences.trashRestoreRetentionHours
        _tableTextColor.value = preferences.tableTextColor
        _settingsTextColor.value = preferences.settingsTextColor
        _solidFillAlpha.value = preferences.solidFillAlpha
        _solidFillColor.value = preferences.solidFillColor
        _solidStrokeWidth.value = preferences.solidStrokeWidth
        _message.value = "已恢复默认设置"
        viewModelScope.launch {
            repository.getAllTasks()
                .filter { !it.isCompleted }
                .forEach { scheduleOverdueCleanup(it) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val lists = repository.getAllLists()
                    val tasksByList = repository.getAllTasks()
                        .filter { it.deletedAt == null }
                        .groupBy { it.listId }
                    val backupLists = lists.map { list ->
                        BackupCodec.BackupList(
                            name = list.name,
                            createdAt = list.createdAt,
                            tasks = tasksByList[list.id].orEmpty().map { task ->
                                BackupCodec.BackupTask(
                                    title = task.title,
                                    ddl = task.ddl,
                                    isCompleted = task.isCompleted,
                                    completedAt = task.completedAt,
                                    createdAt = task.createdAt,
                                    updatedAt = task.updatedAt,
                                    sortOrder = task.sortOrder
                                )
                            }
                        )
                    }
                    val json = BackupCodec.encode(_userName.value, backupLists)
                    val stream = appContext.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("无法写入所选文件")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    backupLists.size to backupLists.sumOf { it.tasks.size }
                }
            }
            result.onSuccess { (listCount, taskCount) ->
                _message.value = "已导出 $listCount 个清单、$taskCount 条任务"
            }.onFailure { error ->
                _message.value = "导出失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = queryDisplayName(uri) ?: "备份文件"
                    val raw = appContext.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("无法读取所选文件")
                    fileName to BackupCodec.decode(raw)
                }
            }
            result.onSuccess { (fileName, document) ->
                pendingImport = document
                _importPreview.value = ImportPreview(
                    fileName = fileName,
                    listCount = document.lists.size,
                    taskCount = document.taskCount
                )
            }.onFailure { error ->
                _message.value = "导入失败：${error.message ?: "文件格式不正确"}"
            }
        }
    }

    fun confirmImport(replaceExisting: Boolean) {
        val document = pendingImport ?: return
        pendingImport = null
        _importPreview.value = null

        viewModelScope.launch {
            val result = runCatching {
                val now = System.currentTimeMillis()
                if (replaceExisting) {
                    repository.getAllTasks().forEach { task ->
                        reminderScheduler.cancelTask(task.id)
                        CleanupScheduler.cancelTaskCleanup(appContext, task.id)
                        CleanupScheduler.cancelOverdueCleanup(appContext, task.id)
                    }
                    repository.getAllAttachments().forEach { attachment ->
                        AttachmentStore.delete(attachment.localPath)
                    }
                    repository.deleteAllTasks()
                    repository.deleteAllLists()
                    AttachmentStore.clearAll(appContext)
                }

                var importedTasks = 0
                document.lists.forEach { backupList ->
                    val listId = if (!replaceExisting) {
                        repository.findListByName(backupList.name)?.id
                            ?: repository.createList(backupList.name, backupList.createdAt)
                    } else {
                        repository.createList(backupList.name, backupList.createdAt)
                    }

                    backupList.tasks.forEach { backupTask ->
                        val entity = TaskEntity(
                            listId = listId,
                            title = backupTask.title,
                            ddl = backupTask.ddl,
                            isCompleted = backupTask.isCompleted,
                            completedAt = backupTask.completedAt,
                            createdAt = backupTask.createdAt,
                            updatedAt = backupTask.updatedAt,
                            remind24Sent = false,
                            remind12Sent = false,
                            sortOrder = backupTask.sortOrder
                        )
                        val newId = repository.createTask(entity)
                        repository.getTask(newId)?.let { saved ->
                            reminderScheduler.scheduleTask(saved)
                            scheduleOverdueCleanup(saved)
                        }
                        importedTasks++
                    }
                }
                importedTasks to document.lists.size
            }

            result.onSuccess { (taskCount, listCount) ->
                _message.value = "已导入 $listCount 个清单、$taskCount 条任务"
                if (_selectedListId.value == null) {
                    repository.getFirstList()?.let { openList(it.id) }
                }
            }.onFailure { error ->
                _message.value = "导入失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun cancelImport() {
        pendingImport = null
        _importPreview.value = null
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    fun suggestedBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "ddl-backup-$stamp.json"
    }

    /** Overdue unfinished tasks are removed 48h after their deadline. */
    private fun scheduleOverdueCleanup(task: TaskEntity) {
        val restoreWindowMs = preferences.trashRestoreRetentionHours * TimeFormat.HOUR_MS
        val deadline = TaskRetention.removalDeadline(task, restoreWindowMs)
        if (!task.isCompleted && deadline != null) {
            CleanupScheduler.scheduleOverdueCleanup(appContext, task.id, deadline)
        } else {
            CleanupScheduler.cancelOverdueCleanup(appContext, task.id)
        }
    }

    private fun startMinuteTicker() {
        viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                _now.value = currentTime
                val delayToNextMinute = TimeFormat.MINUTE_MS - (currentTime % TimeFormat.MINUTE_MS)
                delay(delayToNextMinute + 50L)
            }
        }
    }
}
