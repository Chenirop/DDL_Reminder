package com.example.ddlreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ddlreminder.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hour / minute picker built from two snapping wheels. Only the row in the
 * middle is fully visible; the further a number is from the selection the
 * smaller and more transparent it becomes.
 */
@Composable
fun TimeWheelDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_time)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelColumn(
                    count = 24,
                    initialIndex = hour,
                    onSelected = { hour = it }
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                WheelColumn(
                    count = 60,
                    initialIndex = minute,
                    onSelected = { minute = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute) }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun WheelColumn(
    count: Int,
    initialIndex: Int,
    onSelected: (Int) -> Unit
) {
    val itemHeight = 40.dp
    val visibleItems = 5
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        itemHeight.toPx()
    }

    val centerFloat by remember(itemHeightPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex +
                listState.firstVisibleItemScrollOffset / itemHeightPx
        }
    }

    LaunchedEffect(centerFloat) {
        onSelected(centerFloat.roundToInt().coerceIn(0, count - 1))
    }

    Box(
        modifier = Modifier
            .width(74.dp)
            .height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp)
                )
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItems / 2)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(count = count) { index ->
                val distance = abs(index - centerFloat)
                val scale = (1f - distance * 0.13f).coerceIn(0.66f, 1f)
                val alpha = when {
                    distance < 0.5f -> 1f
                    distance < 1.5f -> 0.48f
                    distance < 2.5f -> 0.24f
                    else -> 0.10f
                }

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                    )
                }
            }
        }
    }
}
