package com.example.ddlreminder.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Very small drag-and-drop helper for [androidx.compose.foundation.lazy.LazyColumn].
 * The dragged card is translated by [draggingItemOffset]; once its centre enters
 * another visible item the rows are swapped through [onMove] and the offset is
 * corrected so the card stays under the finger.
 */
class DragDropState internal constructor(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingItemIndex by mutableIntStateOf(NO_INDEX)
        private set

    var draggingItemOffset by mutableFloatStateOf(0f)
        private set

    val isDragging: Boolean get() = draggingItemIndex != NO_INDEX

    fun startDrag(index: Int) {
        draggingItemIndex = index
        draggingItemOffset = 0f
    }

    fun drag(deltaY: Float) {
        val index = draggingItemIndex
        if (index == NO_INDEX) return

        draggingItemOffset += deltaY

        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.index == index } ?: return
        val draggedCenter = current.offset + draggingItemOffset + current.size / 2f
        val target = visible.firstOrNull { item ->
            item.index != index &&
                draggedCenter >= item.offset &&
                draggedCenter <= item.offset + item.size
        } ?: return

        onMove(index, target.index)
        draggingItemOffset += current.offset - target.offset
        draggingItemIndex = target.index
    }

    fun stopDrag() {
        draggingItemIndex = NO_INDEX
        draggingItemOffset = 0f
    }

    companion object {
        const val NO_INDEX = -1
    }
}

@Composable
fun rememberDragDropState(
    listState: LazyListState,
    moveKey: Any? = Unit,
    onMove: (from: Int, to: Int) -> Unit
): DragDropState {
    val latestOnMove by rememberUpdatedState(onMove)
    return remember(listState, moveKey) {
        DragDropState(listState) { from, to -> latestOnMove(from, to) }
    }
}
