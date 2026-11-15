package com.example.ddlreminder.ui

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ddlreminder.R
import com.example.ddlreminder.data.TaskAttachmentEntity
import com.example.ddlreminder.data.TaskEntity
import com.example.ddlreminder.util.TaskRetention
import com.example.ddlreminder.util.TimeFormat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Red circle that appears behind a card while it is swiped to the left. */
private val TrashRed = Color(0xFFE53935)

/** Gap between the card edge and the trash circle (mirrored on both sides). */
private val TrashGap = 6.dp

@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    listId: Long,
    tasks: List<TaskEntity>,
    now: Long,
    notificationsEnabled: Boolean,
    exactAlarmsEnabled: Boolean,
    overdueTextScale: Float,
    restoreRetentionHours: Int,
    tableTextColor: Color?,
    attachments: List<TaskAttachmentEntity>,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onSaveTask: (Long?, String, Long?, String?) -> Unit,
    onToggleTask: (TaskEntity, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onPersistOrder: (List<Long>) -> Unit,
    onEditorOpened: (Long?) -> Unit,
    onEditorDismissed: () -> Unit,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (TaskAttachmentEntity) -> Unit,
    onOpenAttachment: (TaskAttachmentEntity) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }

    val lazyListState = rememberLazyListState()
    val ordered = remember(listId) { mutableStateListOf<TaskEntity>() }

    // The list order stays untouched while the finger is down: the dragged card
    // follows the finger and the cards it passes over shift out of the way by
    // one slot. Only the drop actually moves the item, which keeps the
    // LazyColumn scroll position (and the text inside the cards) stable.
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dropIndex by remember { mutableIntStateOf(-1) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var draggedSpanPx by remember { mutableFloatStateOf(0f) }

    // Only one row can show its trash button at a time.
    var revealedTaskId by remember { mutableStateOf<Long?>(null) }

    val latestPersist by rememberUpdatedState(onPersistOrder)
    val scope = rememberCoroutineScope()

    LaunchedEffect(tasks, listId) {
        if (draggingId == null) {
            // The list the VM emits after a drop carries the persisted order and
            // fresh entities. Replacing every item (clear + addAll) rebuilt the
            // moved cards and made their text re-lay out for a frame, so keep the
            // existing items whenever the order already matches and only refresh
            // their data in place.
            val sameOrder = ordered.size == tasks.size &&
                ordered.indices.all { ordered[it].id == tasks[it].id }
            if (sameOrder) {
                for (index in tasks.indices) {
                    if (ordered[index] != tasks[index]) {
                        ordered[index] = tasks[index]
                    }
                }
            } else {
                ordered.clear()
                ordered.addAll(tasks)
            }
        }
        if (revealedTaskId != null && ordered.none { it.id == revealedTaskId }) {
            revealedTaskId = null
        }
    }

    fun resetDragState() {
        draggingId = null
        dragOffsetY = 0f
        dropIndex = -1
        dragStartIndex = -1
        draggedSpanPx = 0f
    }

    /**
     * Commits the drop. The dragged card, the neighbours' slot shifts and the
     * LazyColumn anchor are all updated in the same frame: animating the shifts
     * back afterwards was what produced the 0.x second flicker.
     */
    fun applyDrop(from: Int, to: Int) {
        if (from >= 0 && to != from) {
            ordered.add(to, ordered.removeAt(from))
            latestPersist(ordered.map { it.id })
        }
        resetDragState()
        // Moving the first card down made LazyColumn keep that card anchored at
        // the top, so the new first card was scrolled out of view. Pinning the
        // scroll position removes the jump.
        if (from == 0 && to != 0) {
            scope.launch { lazyListState.scrollToItem(0) }
        }
    }

    fun finishDrag(persist: Boolean) {
        val id = draggingId
        if (id == null) {
            resetDragState()
            return
        }
        val from = ordered.indexOfFirst { it.id == id }
        val to = if (dropIndex in ordered.indices) dropIndex else from
        if (from < 0) {
            resetDragState()
            return
        }
        if (!persist) {
            scope.launch {
                if (dragOffsetY != 0f) {
                    animate(dragOffsetY, 0f, animationSpec = tween(160, easing = LinearEasing)) {
                            value, _ ->
                        dragOffsetY = value
                    }
                }
                resetDragState()
            }
            return
        }
        if (from == to) {
            scope.launch {
                if (dragOffsetY != 0f) {
                    animate(dragOffsetY, 0f, animationSpec = tween(160, easing = LinearEasing)) {
                            value, _ ->
                        dragOffsetY = value
                    }
                }
                applyDrop(from, to)
            }
            return
        }
        // Glide the card to the target slot before committing, so the final
        // state is reached by one continuous motion and never by a jump.
        val info = lazyListState.layoutInfo.visibleItemsInfo
        val origin = info.firstOrNull { it.index == from }
        val destination = info.firstOrNull { it.index == to }
        if (origin == null || destination == null) {
            applyDrop(from, to)
            return
        }
        val targetOffset = destination.offset + destination.size / 2f -
            (origin.offset + origin.size / 2f)
        scope.launch {
            animate(
                initialValue = dragOffsetY,
                targetValue = targetOffset,
                animationSpec = tween(180, easing = LinearEasing)
            ) { value, _ -> dragOffsetY = value }
            applyDrop(from, to)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PermissionWarnings(
                notificationsEnabled = notificationsEnabled,
                exactAlarmsEnabled = exactAlarmsEnabled,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings
            )

            if (ordered.isEmpty()) {
                Box(Modifier.weight(1f)) {
                    EmptyState(
                        title = stringResource(R.string.empty_tasks),
                        hint = stringResource(R.string.empty_tasks_hint)
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 2.dp,
                        bottom = 108.dp
                    )
                ) {
                    itemsIndexed(items = ordered, key = { _, task -> task.id }) { index, task ->
                        val isDragging = draggingId == task.id
                        val showCompletedHeader = task.isCompleted &&
                            (index == 0 || !ordered[index - 1].isCompleted)

                        // Cards between the dragged one and the drop slot move by
                        // exactly one card height, in real time, while the finger
                        // is still down.
                        val slotShift = when {
                            draggingId == null || isDragging -> 0f
                            dropIndex > dragStartIndex &&
                                index > dragStartIndex && index <= dropIndex -> -draggedSpanPx
                            dropIndex in 0 until dragStartIndex &&
                                index >= dropIndex && index < dragStartIndex -> draggedSpanPx
                            else -> 0f
                        }

                        SwipeableTaskRow(
                            task = task,
                            isDragging = isDragging,
                            isRevealed = revealedTaskId == task.id,
                            dragOffsetY = dragOffsetY,
                            dragShiftY = slotShift,
                            animateShift = draggingId != null,
                            now = now,
                            overdueTextScale = overdueTextScale,
                            restoreRetentionHours = restoreRetentionHours,
                            tableTextColor = tableTextColor,
                            onRevealChange = { revealed ->
                                if (revealed) {
                                    revealedTaskId = task.id
                                } else if (revealedTaskId == task.id) {
                                    revealedTaskId = null
                                }
                            },
                            onDelete = { onDeleteTask(task.id) },
                            onOpen = {
                                editingTask = task
                                onEditorOpened(task.id)
                                showEditor = true
                            },
                            onToggle = { onToggleTask(task, it) },
                            onLongPressDragStart = {
                                val from = ordered.indexOfFirst { it.id == task.id }
                                if (from >= 0) {
                                    draggingId = task.id
                                    dragOffsetY = 0f
                                    dropIndex = from
                                    dragStartIndex = from
                                    draggedSpanPx = lazyListState.layoutInfo
                                        .visibleItemsInfo
                                        .firstOrNull { it.index == from }
                                        ?.size
                                        ?.toFloat()
                                        ?: 0f
                                }
                            },
                            onLongPressDrag = { dragAmountY ->
                                if (draggingId == task.id) {
                                    dragOffsetY += dragAmountY
                                    val info = lazyListState.layoutInfo.visibleItemsInfo
                                    val origin = info.firstOrNull { it.index == dragStartIndex }
                                    if (origin != null && info.isNotEmpty()) {
                                        // The dragged card is laid out where it came
                                        // from, so its centre can be compared with the
                                        // untouched slots. The original slot is a valid
                                        // candidate again: putting the card back exactly
                                        // where it started must not reorder anything.
                                        val center = origin.offset +
                                            dragOffsetY +
                                            origin.size / 2f
                                        val containing = info.firstOrNull {
                                            center >= it.offset &&
                                                center <= it.offset + it.size
                                        }
                                        val nearest = info.minByOrNull {
                                            kotlin.math.abs(
                                                center - (it.offset + it.size / 2f)
                                            )
                                        }
                                        val hit = containing ?: nearest
                                        if (hit != null) {
                                            dropIndex = hit.index
                                        }
                                    }
                                }
                            },
                            onLongPressDragEnd = { finishDrag(persist = true) },
                            onLongPressDragCancel = { finishDrag(persist = false) },
                            header = if (showCompletedHeader) {
                                {
                                    Text(
                                        text = stringResource(
                                            R.string.completed_section,
                                            ordered.count { it.isCompleted }
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(
                                            start = 6.dp,
                                            top = 10.dp,
                                            bottom = 6.dp
                                        )
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingTask = null
                onEditorOpened(null)
                showEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.add_task)
            )
        }
    }

    if (showEditor) {
        TaskEditorSheet(
            task = editingTask,
            attachments = attachments,
            onDismiss = {
                showEditor = false
                onEditorDismissed()
            },
            onSave = { title, ddl, note ->
                onSaveTask(editingTask?.id, title, ddl, note)
                showEditor = false
                onEditorDismissed()
            },
            onDelete = {
                editingTask?.let { onDeleteTask(it.id) }
                showEditor = false
                onEditorDismissed()
            },
            onAddAttachment = onAddAttachment,
            onRemoveAttachment = onRemoveAttachment,
            onOpenAttachment = onOpenAttachment
        )
    }
}

/**
 * One card plus the red trash button behind it.
 *
 * The horizontal drag runs before the long press detector in the modifier
 * chain, so once the card starts moving sideways the long press is cancelled
 * and the reorder drag can never start — even while the finger stays down.
 */
@Composable
private fun SwipeableTaskRow(
    task: TaskEntity,
    isDragging: Boolean,
    isRevealed: Boolean,
    dragOffsetY: Float,
    dragShiftY: Float,
    animateShift: Boolean,
    now: Long,
    overdueTextScale: Float,
    restoreRetentionHours: Int,
    tableTextColor: Color?,
    onRevealChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onLongPressDragStart: () -> Unit,
    onLongPressDrag: (Float) -> Unit,
    onLongPressDragEnd: () -> Unit,
    onLongPressDragCancel: () -> Unit,
    header: (@Composable () -> Unit)?
) {
    val offsetX = remember(task.id) { Animatable(0f) }
    var widthPx by remember(task.id) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Smoothly opens the slot the dragged card is about to drop into. Once the
    // drop is committed (no drag running) the shifts snap to zero in the same
    // frame as the reorder, which removes the post-drop flicker.
    val animatedShift by animateFloatAsState(
        targetValue = dragShiftY,
        animationSpec = if (animateShift) {
            tween(durationMillis = 150, easing = LinearEasing)
        } else {
            snap()
        },
        label = "dragSlotShift"
    )

    // The circle is inset from both the card edge and the row edge so the
    // two never touch; while closed it sits outside the clipped strip.
    val gapPx = with(density) { TrashGap.toPx() }
    val restPx = widthPx / 6f
    val bubblePx = if (widthPx <= 0) {
        0f
    } else {
        (restPx - 2 * gapPx).coerceAtLeast(gapPx)
    }

    // Collapse when another row was revealed (or when the row was closed).
    LaunchedEffect(isRevealed) {
        if (!isRevealed && offsetX.value > -widthPx / 2f) {
            offsetX.animateTo(0f, tween(durationMillis = 180, easing = LinearEasing))
        }
    }

    fun settleAtRest() {
        if (widthPx <= 0) return
        scope.launch {
            offsetX.animateTo(
                targetValue = -widthPx / 6f,
                animationSpec = tween(durationMillis = 170, easing = LinearEasing)
            )
        }
    }

    fun settleClosed() {
        scope.launch {
            offsetX.animateTo(0f, tween(durationMillis = 170, easing = LinearEasing))
            onRevealChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .zIndex(if (isDragging) 2f else if (isRevealed) 1f else 0f)
            // The slot shift of the cards around the dragged one is a layout
            // offset on purpose: positionInRoot then follows the movement, so the
            // glass sampling redraws exactly where the card is drawn. Compensating
            // a graphicsLayer translation instead left the neighbouring cards
            // sampling the backdrop one slot behind (visible as a render offset).
            // While a drag is running the shift is animated so the neighbours
            // glide into their new slot. On the commit frame the drag is already
            // gone, so the raw target (0) is used: `animateFloatAsState` only
            // reaches its snapping target on the next frame, which drew the
            // neighbours one slot away from their new layout for a frame.
            .offset {
                val shift = if (isDragging) 0f else if (animateShift) animatedShift else dragShiftY
                IntOffset(0, shift.roundToInt())
            }
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragOffsetY
                    // Gentle lift feedback while the card is held; small enough
                    // not to overlap the neighbouring cards.
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
            .onSizeChanged { widthPx = it.width }
            // `isRevealed` must stay out of the keys: toggling it mid-gesture
            // restarted this pointer input and aborted the swipe halfway.
            .pointerInput(task.id, widthPx) {
                if (widthPx <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Only to the left, and at most half of the card.
                        val next = (offsetX.value + dragAmount)
                            .coerceIn(-widthPx / 2f, 0f)
                        if (next < -1f) onRevealChange(true)
                        scope.launch { offsetX.snapTo(next) }
                    },
                    onDragEnd = {
                        // Dragging back to the right closes the row again once
                        // it passed the half of the resting offset; otherwise
                        // the row settles at exactly 1/6 of its width.
                        if (offsetX.value > -widthPx / 12f) {
                            settleClosed()
                        } else {
                            settleAtRest()
                        }
                    },
                    onDragCancel = {
                        if (offsetX.value > -widthPx / 12f) {
                            settleClosed()
                        } else {
                            settleAtRest()
                        }
                    }
                )
            }
            .pointerInput(task.id, widthPx) {
                if (widthPx <= 0) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        if (offsetX.value == 0f) onLongPressDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onLongPressDrag(dragAmount.y)
                    },
                    onDragEnd = { onLongPressDragEnd() },
                    onDragCancel = { onLongPressDragCancel() }
                )
            }
    ) {
        // The bubble lives in a layout that does not influence the row height,
        // so the compact circle can be drawn behind the card without making
        // the list row taller than one card.
        // Clip the bubble layer to the row: while the row is closed the
        // circle sits beyond the right edge and must not peek out.
        Box(modifier = Modifier.matchParentSize().clipToBounds()) {
            TrashBubble(
                bubblePx = bubblePx,
                offsetXProvider = { offsetX.value },
                onClick = {
                    if (widthPx <= 0) {
                        onDelete()
                    } else {
                        scope.launch {
                            // Slide the card out of the list before removing it.
                            offsetX.animateTo(
                                targetValue = -widthPx.toFloat(),
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = LinearEasing
                                )
                            )
                            onRevealChange(false)
                            onDelete()
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            x = bubbleShiftPx(
                                widthPx = widthPx,
                                cardOffsetX = offsetX.value,
                                gapPx = gapPx
                            ).roundToInt(),
                            y = 0
                        )
                    }
                    .then(
                        if (bubblePx > 0f) {
                            // Slightly smaller than the revealed strip so the
                            // card and the circle keep a visible gap.
                            Modifier.requiredSize(
                                with(density) { bubblePx.toDp() }
                            )
                        } else {
                            Modifier.size(0.dp)
                        }
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        ) {
            header?.invoke()

            TaskCard(
                task = task,
                now = now,
                overdueTextScale = overdueTextScale,
                restoreRetentionHours = restoreRetentionHours,
                tableTextColor = tableTextColor,
                dimmed = isDragging,
                // Only the drag lift is a graphicsLayer translation. Slot shifts
                // and the left swipe both move positionInRoot already, so they must
                // not be compensated a second time here.
                renderOffset = { Offset(0f, if (isDragging) dragOffsetY else 0f) },
                onToggle = { onToggle(it) },
                onClick = {
                    if (offsetX.value < -1f) {
                        onRevealChange(false)
                    } else {
                        onOpen()
                    }
                }
            )
        }
    }
}

/**
 * Horizontal offset of the trash circle relative to its aligned right edge.
 *
 * The circle is `gap` narrower than the revealed strip at either side. It
 * slides in at the same speed as the card up to the resting offset (1/6 of
 * the row), then crawls at 1/50 of the card speed. While closed its left
 * edge sits exactly on the row edge, so the clipped layer hides it.
 */
private fun bubbleShiftPx(widthPx: Int, cardOffsetX: Float, gapPx: Float): Float {
    if (widthPx <= 0) return 0f
    val rest = widthPx / 6f
    val gap = gapPx.coerceAtMost(rest / 3f)
    val diameter = (rest - 2 * gap).coerceAtLeast(gap)
    val travel = (-cardOffsetX).coerceAtLeast(0f)
    return if (travel <= rest) {
        diameter - travel * (1f - gap / rest)
    } else {
        -gap - (travel - rest) / 50f
    }
}

@Composable
private fun TrashBubble(
    bubblePx: Float,
    offsetXProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The circle only becomes clickable once the card has actually moved.
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(TrashRed)
            .clickable {
                if (offsetXProvider() < -1f) onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (bubblePx > 0f) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.move_to_trash),
                tint = Color.White,
                modifier = Modifier.fillMaxSize(0.42f)
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    now: Long,
    overdueTextScale: Float,
    restoreRetentionHours: Int,
    tableTextColor: Color?,
    dimmed: Boolean,
    renderOffset: () -> Offset,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val completed = task.isCompleted
    val mutedColor = MaterialTheme.colorScheme.outline
    val customColor = tableTextColor
    val titleColor = when {
        customColor != null -> if (completed) customColor.copy(alpha = 0.55f) else customColor
        completed -> mutedColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val ddlText = if (task.ddl == null) {
        stringResource(R.string.no_ddl)
    } else {
        stringResource(R.string.deadline_prefix, TimeFormat.formatDateTime(task.ddl))
    }
    val ddlColor = when {
        customColor != null ->
            customColor.copy(alpha = if (completed) 0.55f else 0.78f)
        completed || task.ddl == null -> mutedColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val completedSuffixColor = customColor?.copy(alpha = 0.55f) ?: mutedColor
    val reminderColor = countdownColor(
        level = TimeFormat.countdownLevel(task.ddl, now),
        completed = completed
    )
    val overdue = !completed && task.ddl != null && task.ddl <= now

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        completed = completed,
        dimmed = dimmed,
        renderOffset = renderOffset,
        contentPadding = PaddingValues(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = completed,
                onCheckedChange = { onToggle(it) },
                colors = if (completed) {
                    CheckboxDefaults.colors(
                        checkedColor = mutedColor,
                        checkmarkColor = MaterialTheme.colorScheme.surface,
                        uncheckedColor = mutedColor
                    )
                } else {
                    CheckboxDefaults.colors()
                }
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    textDecoration = if (completed) TextDecoration.LineThrough else null
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Text(
                        text = ddlText,
                        style = MaterialTheme.typography.bodySmall,
                        color = ddlColor
                    )
                    if (completed) {
                        Text(
                            text = " · " + stringResource(R.string.completed),
                            style = MaterialTheme.typography.bodySmall,
                            color = completedSuffixColor
                        )
                    }
                }
                if (!completed && task.ddl != null) {
                    // "已截止" and the removal countdown share one line; the small
                    // dark chip keeps the marker readable on the glass surface while
                    // the card itself looks like every other one.
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = TimeFormat.countdownText(task.ddl, now),
                            style = MaterialTheme.typography.labelMedium,
                            color = reminderColor
                        )
                        if (overdue) {
                            val remaining = TaskRetention.remainingMs(
                                task = task,
                                now = now,
                                restoreRetentionMs = restoreRetentionHours * TimeFormat.HOUR_MS
                            )
                            Spacer(Modifier.width(6.dp))
                            // User adjustable scale (capped at 1.0, i.e. the original
                            // label size) and no vertical padding: the chip must not
                            // make the overdue card taller than any other card.
                            Text(
                                text = stringResource(
                                    R.string.overdue_removal_countdown,
                                    TimeFormat.formatRemaining(remaining)
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize *
                                        overdueTextScale,
                                    lineHeight =
                                        MaterialTheme.typography.labelSmall.lineHeight *
                                        overdueTextScale
                                ),
                                color = ddlColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 7.dp, vertical = 0.dp)
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.drag_handle),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
            }
        }
    }
}
