package com.example.ddlreminder.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ddlreminder.R
import com.example.ddlreminder.data.AppPreferences
import com.example.ddlreminder.data.ListSummary
import com.example.ddlreminder.data.RenderScheme

@Composable
fun TodoApp(
    viewModel: TodoViewModel,
    notificationsEnabled: Boolean,
    exactAlarmsEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    val selectedListId by viewModel.selectedListId.collectAsStateWithLifecycle()
    val selectedList by viewModel.selectedList.collectAsStateWithLifecycle()
    val summaries by viewModel.listSummaries.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val stats by viewModel.dataStats.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val background by viewModel.background.collectAsStateWithLifecycle()
    val glassFillAlpha by viewModel.glassFillAlpha.collectAsStateWithLifecycle()
    val renderScheme by viewModel.renderScheme.collectAsStateWithLifecycle()
    val winuiFillAlpha by viewModel.winuiFillAlpha.collectAsStateWithLifecycle()
    val winuiBlur by viewModel.winuiBlur.collectAsStateWithLifecycle()
    val winuiRefraction by viewModel.winuiRefraction.collectAsStateWithLifecycle()
    val winuiChromatic by viewModel.winuiChromatic.collectAsStateWithLifecycle()
    val winuiGlare by viewModel.winuiGlare.collectAsStateWithLifecycle()
    val winuiPerformanceMode by viewModel.winuiPerformanceMode.collectAsStateWithLifecycle()
    val overdueTextScale by viewModel.overdueTextScale.collectAsStateWithLifecycle()
    val editorAttachments by viewModel.editorAttachments.collectAsStateWithLifecycle()
    val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
    val keepAutoDeletedInTrash by viewModel.keepAutoDeletedInTrash.collectAsStateWithLifecycle()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val trashRestoreRetentionHours by
        viewModel.trashRestoreRetentionHours.collectAsStateWithLifecycle()
    val tableTextColor by viewModel.tableTextColor.collectAsStateWithLifecycle()
    val settingsTextColor by viewModel.settingsTextColor.collectAsStateWithLifecycle()
    val solidFillAlpha by viewModel.solidFillAlpha.collectAsStateWithLifecycle()
    val solidFillColor by viewModel.solidFillColor.collectAsStateWithLifecycle()
    val solidStrokeWidth by viewModel.solidStrokeWidth.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var showListPicker by remember { mutableStateOf(false) }
    var showCreateList by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ListSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ListSummary?>(null) }
    var showRenameUser by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    val currentListId = selectedListId
    val currentSummary = summaries.firstOrNull { it.id == currentListId }
    val currentListName = selectedList?.name
        ?: currentSummary?.name
        ?: ""
    val pendingCount = currentSummary?.pendingCount ?: 0

    BackHandler(enabled = showListPicker || showSettings || showTrash) {
        if (showListPicker) {
            showListPicker = false
        } else if (showTrash) {
            showTrash = false
        } else {
            showSettings = false
        }
    }

    // Settings is rendered on a dark scrim so the pure white labels stay readable
    // on top of any custom picture; the system bar icons follow the same state.
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val hasCustomBackground = background.bitmap != null
    val whiteLabels = showSettings || hasCustomBackground
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val backdrop = remember(
        background.bitmap,
        darkTheme,
        colorScheme.primary,
        colorScheme.secondary,
        showSettings
    ) {
        val overlay = when {
            showSettings -> Color.Black.copy(alpha = 0.42f)
            hasCustomBackground -> Color.Black.copy(alpha = 0.22f)
            else -> Color.Transparent
        }
        BackdropSpec(
            bitmap = background.bitmap,
            dark = darkTheme,
            primary = colorScheme.primary,
            secondary = colorScheme.secondary,
            overlay = overlay
        )
    }

    val view = LocalView.current
    LaunchedEffect(showSettings, darkTheme, hasCustomBackground) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val lightBarIcons = !whiteLabels && !darkTheme
        controller.isAppearanceLightStatusBars = lightBarIcons
        controller.isAppearanceLightNavigationBars = lightBarIcons
    }

    // The WinUI shader samples a rasterized copy of the backdrop. It is built
    // once per background / window size, never per card, and only while the
    // WinUI scheme is actually selected.
    val glassTexture = rememberGlassTexture(
        backdrop = backdrop,
        viewport = if (renderScheme == RenderScheme.LiquidWinUI) rootSize else IntSize.Zero,
        blurDp = winuiBlur
    )

    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalBackdropViewport provides rootSize,
        LocalRenderScheme provides renderScheme,
        LocalGlassFillAlpha provides glassFillAlpha,
        LocalSolidFillAlpha provides solidFillAlpha,
        LocalSolidFillColor provides Color(solidFillColor),
        LocalSolidStrokeWidth provides solidStrokeWidth.dp,
        LocalWinUIFillAlpha provides winuiFillAlpha,
        LocalWinUIBlur provides winuiBlur.dp,
        LocalWinUIRefraction provides winuiRefraction,
        LocalWinUIChromatic provides winuiChromatic,
        LocalWinUIGlare provides winuiGlare,
        LocalWinUIPerformanceMode provides winuiPerformanceMode,
        LocalGlassTexture provides glassTexture
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    if (coordinates.size != rootSize) rootSize = coordinates.size
                }
        ) {
            AppBackground(modifier = Modifier.fillMaxSize())

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    AppTopBar(
                        title = when {
                            showSettings -> stringResource(R.string.settings_title)
                            showTrash -> stringResource(R.string.trash_title)
                            else -> currentListName.ifBlank {
                                stringResource(R.string.nav_lists)
                            }
                        },
                        subtitle = when {
                            showSettings -> null
                            showTrash -> stringResource(R.string.trash_count, trashItems.size)
                            pendingCount > 0 -> stringResource(
                                R.string.list_pending_count,
                                pendingCount
                            )
                            else -> null
                        },
                        whiteLabels = whiteLabels,
                        showMenu = !showSettings && !showTrash,
                        onBack = if (showTrash) {
                            { showTrash = false }
                        } else {
                            null
                        },
                        onOpenTrash = { showTrash = true },
                        onResetOrder = if (!showSettings && !showTrash && currentListId != null) {
                            { currentListId?.let { viewModel.resetTaskOrder(it) } }
                        } else {
                            null
                        }
                    )
                },
                bottomBar = {
                    if (!showTrash) {
                        AppBottomBar(
                            listLabel = currentListName.ifBlank {
                                stringResource(R.string.nav_lists)
                            },
                            listSelected = !showSettings,
                            whiteLabels = whiteLabels,
                            onListsClick = {
                                // Coming back from Settings keeps the list that was
                                // open before: no picker popup any more.
                                if (showSettings) {
                                    showSettings = false
                                } else {
                                    showListPicker = true
                                }
                            },
                            onSettingsClick = {
                                showListPicker = false
                                showSettings = true
                            }
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                val contentModifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)

                when {
                    showTrash -> TrashScreen(
                        modifier = contentModifier,
                        items = trashItems,
                        now = now,
                        retentionDays = trashRetentionDays,
                        onRestore = viewModel::restoreTask,
                        onDeleteForever = viewModel::deleteTaskForever,
                        onRestoreMany = viewModel::restoreTasks
                    )

                    showSettings -> SettingsScreen(
                        modifier = contentModifier,
                        userName = userName,
                        stats = stats,
                        notificationsEnabled = notificationsEnabled,
                        exactAlarmsEnabled = exactAlarmsEnabled,
                        suggestedFileName = viewModel.suggestedBackupFileName(),
                        backgroundActive = background.bitmap != null,
                        glassFillAlpha = glassFillAlpha,
                        renderScheme = renderScheme,
                        winuiFillAlpha = winuiFillAlpha,
                        winuiBlur = winuiBlur,
                        winuiRefraction = winuiRefraction,
                        winuiChromatic = winuiChromatic,
                        winuiGlare = winuiGlare,
                        winuiPerformanceMode = winuiPerformanceMode,
                        overdueTextScale = overdueTextScale,
                        keepAutoDeletedInTrash = keepAutoDeletedInTrash,
                        trashRetentionDays = trashRetentionDays,
                        trashRestoreRetentionHours = trashRestoreRetentionHours,
                        tableTextColor = tableTextColor,
                        settingsTextColor = settingsTextColor,
                        solidFillAlpha = solidFillAlpha,
                        solidFillColor = solidFillColor,
                        solidStrokeWidth = solidStrokeWidth,
                        onRenameUser = { showRenameUser = true },
                        onExport = viewModel::exportData,
                        onImport = viewModel::prepareImport,
                        onBackgroundPicked = viewModel::setBackground,
                        onResetBackground = viewModel::restoreDefaultBackground,
                        onGlassFillAlphaChange = viewModel::setGlassFillAlpha,
                        onRenderSchemeChange = viewModel::setRenderScheme,
                        onWinuiFillAlphaChange = viewModel::setWinuiFillAlpha,
                        onWinuiBlurChange = viewModel::setWinuiBlur,
                        onWinuiRefractionChange = viewModel::setWinuiRefraction,
                        onWinuiChromaticChange = viewModel::setWinuiChromatic,
                        onWinuiGlareChange = viewModel::setWinuiGlare,
                        onWinuiPerformanceModeChange = viewModel::setWinuiPerformanceMode,
                        onOverdueTextScaleChange = viewModel::setOverdueTextScale,
                        onKeepAutoDeletedChange = viewModel::setKeepAutoDeletedInTrash,
                        onTrashRetentionChange = viewModel::setTrashRetentionDays,
                        onTrashRestoreRetentionChange = viewModel::setTrashRestoreRetentionHours,
                        onTableTextColorChange = viewModel::setTableTextColor,
                        onSettingsTextColorChange = viewModel::setSettingsTextColor,
                        onSolidFillAlphaChange = viewModel::setSolidFillAlpha,
                        onSolidFillColorChange = viewModel::setSolidFillColor,
                        onSolidStrokeWidthChange = viewModel::setSolidStrokeWidth,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenExactAlarmSettings = onOpenExactAlarmSettings
                    )

                    currentListId == null -> NoListsContent(
                        modifier = contentModifier,
                        onCreateList = { showCreateList = true }
                    )

                    else -> TasksScreen(
                        modifier = contentModifier,
                        listId = currentListId,
                        tasks = tasks,
                        now = now,
                        notificationsEnabled = notificationsEnabled,
                        exactAlarmsEnabled = exactAlarmsEnabled,
                        overdueTextScale = overdueTextScale,
                        restoreRetentionHours = trashRestoreRetentionHours,
                        tableTextColor = tableTextColor.takeIf { it != AppPreferences.NO_COLOR }
                            ?.let { Color(it) },
                        attachments = editorAttachments,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                        onSaveTask = { taskId, title, ddl, note ->
                            viewModel.saveTask(currentListId, taskId, title, ddl, note)
                        },
                        onToggleTask = viewModel::setTaskCompleted,
                        onDeleteTask = viewModel::deleteTask,
                        onPersistOrder = { ids -> viewModel.persistTaskOrder(currentListId, ids) },
                        onEditorOpened = viewModel::openTaskEditor,
                        onEditorDismissed = viewModel::closeTaskEditor,
                        onAddAttachment = viewModel::addEditorAttachment,
                        onRemoveAttachment = viewModel::removeEditorAttachment,
                        onOpenAttachment = viewModel::openAttachment
                    )
                }
            }
        }
    }

    if (showListPicker) {
        ListSwitcherDialog(
            summaries = summaries,
            selectedListId = currentListId,
            onSelect = { id ->
                viewModel.openList(id)
                showSettings = false
                showListPicker = false
            },
            onCreate = { name ->
                viewModel.createList(name)
                showSettings = false
                showListPicker = false
            },
            onRename = { summary ->
                showListPicker = false
                renameTarget = summary
            },
            onDelete = { summary ->
                showListPicker = false
                deleteTarget = summary
            },
            onDismiss = { showListPicker = false }
        )
    }

    if (showCreateList) {
        ListNameDialog(
            title = stringResource(R.string.add_list),
            initialName = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showCreateList = false },
            onConfirm = { name ->
                viewModel.createList(name)
                showCreateList = false
                showSettings = false
            }
        )
    }

    renameTarget?.let { target ->
        ListNameDialog(
            title = stringResource(R.string.rename_list),
            initialName = target.name,
            confirmLabel = stringResource(R.string.save),
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameList(target.id, name)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        DeleteListDialog(
            listName = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteList(target.id)
                deleteTarget = null
            }
        )
    }

    if (showRenameUser) {
        TextInputDialog(
            title = stringResource(R.string.change_user_name),
            label = stringResource(R.string.user_name),
            initialValue = userName,
            onDismiss = { showRenameUser = false },
            onConfirm = { name ->
                viewModel.updateUserName(name)
                showRenameUser = false
            }
        )
    }

    importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onMerge = { viewModel.confirmImport(false) },
            onReplace = { viewModel.confirmImport(true) },
            onDismiss = { viewModel.cancelImport() }
        )
    }
}

/**
 * Compact app bar: 48dp of content instead of the default 64dp, so the task list
 * gets noticeably more room; the status bar inset is kept for edge-to-edge.
 */
@Composable
private fun AppTopBar(
    title: String,
    subtitle: String?,
    whiteLabels: Boolean,
    showMenu: Boolean,
    onBack: (() -> Unit)?,
    onOpenTrash: () -> Unit,
    onResetOrder: (() -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = if (whiteLabels) Color.White else colorScheme.onSurface
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (whiteLabels) Color.White else colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (whiteLabels) {
                        Color.White.copy(alpha = 0.72f)
                    } else {
                        colorScheme.outline
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showMenu) {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more),
                        tint = if (whiteLabels) Color.White else colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trash_title)) },
                        onClick = {
                            menuExpanded = false
                            onOpenTrash()
                        }
                    )
                    if (onResetOrder != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.restore_default_order)) },
                            onClick = {
                                menuExpanded = false
                                onResetOrder()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Transparent, slightly shorter bottom bar. The gradient scrim keeps the labels
 * readable while the page content still shows through the bar.
 */
@Composable
private fun AppBottomBar(
    listLabel: String,
    listSelected: Boolean,
    whiteLabels: Boolean,
    onListsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrim = if (whiteLabels) {
        Color.Black.copy(alpha = 0.46f)
    } else {
        colorScheme.surface.copy(alpha = 0.62f)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, scrim)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp)
        ) {
            BottomBarItem(
                icon = Icons.AutoMirrored.Filled.List,
                label = listLabel,
                selected = listSelected,
                whiteLabels = whiteLabels,
                onClick = onListsClick,
                modifier = Modifier.weight(1f)
            )
            BottomBarItem(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.nav_settings),
                selected = !listSelected,
                whiteLabels = whiteLabels,
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    whiteLabels: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = when {
        whiteLabels && selected -> Color.White
        whiteLabels -> Color.White.copy(alpha = 0.62f)
        selected -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp)
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun NoListsContent(
    modifier: Modifier = Modifier,
    onCreateList: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(120.dp))
        EmptyState(
            title = stringResource(R.string.empty_lists_title),
            hint = stringResource(R.string.empty_lists_hint)
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreateList) {
            Text(stringResource(R.string.add_list))
        }
    }
}
