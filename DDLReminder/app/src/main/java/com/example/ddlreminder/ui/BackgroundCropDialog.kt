package com.example.ddlreminder.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Full-screen crop screen shown right after the user picks a background image.
 *
 * It is rendered inside the app window (not a dialog), so it really covers the
 * whole screen, including the areas behind the status and navigation bars; the
 * bars are hidden while cropping and restored afterwards.
 *
 * The whole screen is the crop frame: the picture is drawn edge to edge, the
 * user can pan and pinch-zoom it, and whatever is visible when they tap 确定
 * becomes the new background. Only 取消 / 确定 are drawn on top of the picture.
 */
@Composable
fun BackgroundCropScreen(
    source: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var pan by remember(source) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val image = remember(source) { source.asImageBitmap() }
    val context = LocalContext.current

    BackHandler(onBack = onCancel)

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(source) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val next = (zoom * zoomChange).coerceIn(1f, 4f)
                        val maxShiftX = (source.width * baseScale(viewport, source) * next -
                            viewport.width) / 2f
                        val maxShiftY = (source.height * baseScale(viewport, source) * next -
                            viewport.height) / 2f
                        zoom = next
                        pan = Offset(
                            (pan.x + panChange.x).coerceIn(-maxShiftX, maxShiftX),
                            (pan.y + panChange.y).coerceIn(-maxShiftY, maxShiftY)
                        )
                    }
                }
        ) {
            val scale = baseScale(viewport, source) * zoom
            val drawWidth = source.width * scale
            val drawHeight = source.height * scale
            val left = (size.width - drawWidth) / 2f + pan.x
            val top = (size.height - drawHeight) / 2f + pan.y
            drawIntoCanvas { canvas ->
                canvas.drawImageRect(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(source.width, source.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(
                        drawWidth.roundToInt().coerceAtLeast(1),
                        drawHeight.roundToInt().coerceAtLeast(1)
                    ),
                    paint = Paint()
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("取消", color = Color.White)
            }
            Button(
                onClick = { onConfirm(crop(source, viewport, zoom, pan)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("确定")
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun baseScale(viewport: IntSize, source: Bitmap): Float {
    if (viewport.width <= 0 || viewport.height <= 0) return 1f
    return max(
        viewport.width.toFloat() / source.width,
        viewport.height.toFloat() / source.height
    )
}

private fun crop(source: Bitmap, viewport: IntSize, zoom: Float, pan: Offset): Bitmap {
    if (viewport.width <= 0 || viewport.height <= 0) return source
    val scale = baseScale(viewport, source) * zoom
    val drawWidth = source.width * scale
    val drawHeight = source.height * scale
    val left = (viewport.width - drawWidth) / 2f + pan.x
    val top = (viewport.height - drawHeight) / 2f + pan.y

    val leftSrc = ((-left) / scale).roundToInt()
        .coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val topSrc = ((-top) / scale).roundToInt()
        .coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val widthSrc = (viewport.width / scale).roundToInt()
        .coerceIn(1, (source.width - leftSrc).coerceAtLeast(1))
    val heightSrc = (viewport.height / scale).roundToInt()
        .coerceIn(1, (source.height - topSrc).coerceAtLeast(1))
    return Bitmap.createBitmap(source, leftSrc, topSrc, widthSrc, heightSrc)
}
