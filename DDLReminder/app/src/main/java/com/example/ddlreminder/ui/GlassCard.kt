package com.example.ddlreminder.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient as AndroidLinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.RadialGradient as AndroidRadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader as AndroidShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ddlreminder.data.RenderScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToInt

/**
 * The drawing recipe of the page background. It is shared by the full screen
 * background and by every glass card, so the cards can re-render exactly the
 * pixels that sit underneath them (and therefore support backdrop sampling
 * without capturing a screenshot).
 */
data class BackdropSpec(
    val bitmap: ImageBitmap?,
    val dark: Boolean,
    val primary: Color,
    val secondary: Color,
    val overlay: Color = Color.Transparent
)

val LocalBackdrop = compositionLocalOf {
    BackdropSpec(
        bitmap = null,
        dark = false,
        primary = Color(0xFF1E5AA8),
        secondary = Color(0xFF545F71)
    )
}

/** Size (px) of the root window, used to align the sampled background. */
val LocalBackdropViewport = compositionLocalOf { IntSize.Zero }

/**
 * Opacity of the tint that fills every "liquid glass" card (0 = fully
 * transparent, 1 = opaque). The user can change it from the settings screen.
 */
val LocalGlassFillAlpha = compositionLocalOf { 0.16f }

/** Which of the three rendering schemes the cards use. */
val LocalRenderScheme = compositionLocalOf { RenderScheme.Liquid }

/** User settings of the solid scheme. */
val LocalSolidFillAlpha = compositionLocalOf { 0.85f }
val LocalSolidFillColor = compositionLocalOf { Color.Black }
val LocalSolidStrokeWidth = compositionLocalOf { 1.5.dp }

/** Independent user settings of the WinUI glass scheme. */
val LocalWinUIFillAlpha = compositionLocalOf { 0.24f }
val LocalWinUIBlur = compositionLocalOf { 12.dp }
val LocalWinUIRefraction = compositionLocalOf { 0.6f }
val LocalWinUIChromatic = compositionLocalOf { 0.5f }
val LocalWinUIGlare = compositionLocalOf { 0.5f }

/** Skips the per-card mesh warp; the WinUI card becomes a plain blurred fill. */
val LocalWinUIPerformanceMode = compositionLocalOf { false }

/**
 * Rasterized copy of the page background used as the sampling source of the
 * Android 13+ WinUI shader. Provided once per screen (not per card) so the
 * bitmap is never rebuilt for every list row.
 */
val LocalGlassTexture = compositionLocalOf<ImageBitmap?> { null }

enum class GlassTint { Light, Dark }

private const val GLASS_MAGNIFICATION = 1.01f

/**
 * Scale of the sharp copy that is shown only inside the rim band. The shader in
 * the reference video offsets every sample along the *inverse* normal, i.e.
 * towards the center of the glass, and the offset grows with (distance / radius)
 * powered up so it is strongest at the border. Redrawing the backdrop slightly
 * scaled-down about the card center and keeping only the rim reads the same way:
 * the rim shows the background pulled towards the middle of the card, which is
 * what bends a lens at its edge. Plain Compose drawing, so it also works below
 * Android 13 (the blurred frost is still used there).
 */
private const val REFRACT_SCALE = 0.88f
private const val REFRACT_STEPS = 14

/** Maximum width (px) of the background copy sampled by the WinUI shader. */
private const val WINUI_TEXTURE_MAX_WIDTH = 720

// ------------------------------------------------------------------ background

@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current
    val viewport = LocalBackdropViewport.current
    val bitmap = backdrop.bitmap
    if (bitmap != null) {
        // The user picture goes through a plain [Image] so it stays on the
        // standard drawing path on every device (HarmonyOS included).
        Box(modifier = modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (backdrop.overlay.alpha > 0f) {
                Box(Modifier.fillMaxSize().background(backdrop.overlay))
            }
        }
        return
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasSize = if (viewport.width > 0 && viewport.height > 0) {
            Size(viewport.width.toFloat(), viewport.height.toFloat())
        } else {
            size
        }
        drawBackdropLayer(backdrop, canvasSize)
    }
}

/**
 * Paints the whole background into [canvasSize], independent of the actual
 * draw surface size. Glass cards call it with the window size while their own
 * canvas is clipped to the card, which is what produces the "see through the
 * glass" effect.
 */
internal fun DrawScope.drawBackdropLayer(
    backdrop: BackdropSpec,
    canvasSize: Size,
    alpha: Float = 1f,
    inflate: Float = 0f
) {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return
    val opacity = alpha.coerceIn(0f, 1f)
    if (opacity <= 0f) return

    // The rim samples the backdrop slightly below 1:1, so a few extra pixels
    // past the window bounds keep the card fully covered up to its border.
    val area = Rect(
        left = -inflate,
        top = -inflate,
        right = canvasSize.width + inflate,
        bottom = canvasSize.height + inflate
    )

    val bitmap = backdrop.bitmap
    if (bitmap != null) {
        drawImageCover(bitmap, area, opacity)
    } else {
        val top = if (backdrop.dark) Color(0xFF0B1017) else Color(0xFFF4F8FF)
        val bottom = if (backdrop.dark) Color(0xFF161D27) else Color(0xFFE6EDF9)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(top.copy(alpha = opacity), bottom.copy(alpha = opacity)),
                startY = 0f,
                endY = canvasSize.height
            ),
            topLeft = area.topLeft,
            size = area.size
        )
        drawBlob(
            color = backdrop.primary.copy(alpha = (if (backdrop.dark) 0.38f else 0.24f) * opacity),
            center = Offset(canvasSize.width * 0.12f, canvasSize.height * 0.04f),
            radius = canvasSize.maxDimension * 0.62f
        )
        drawBlob(
            color = backdrop.secondary.copy(alpha = (if (backdrop.dark) 0.30f else 0.18f) * opacity),
            center = Offset(canvasSize.width * 0.95f, canvasSize.height * 0.55f),
            radius = canvasSize.maxDimension * 0.55f
        )
        drawBlob(
            color = backdrop.primary.copy(alpha = (if (backdrop.dark) 0.22f else 0.14f) * opacity),
            center = Offset(canvasSize.width * 0.30f, canvasSize.height * 1.02f),
            radius = canvasSize.maxDimension * 0.50f
        )
        // Smaller, brighter accents give the card backdrop enough structure to
        // make the blur and the 1.01x magnification actually visible.
        drawBlob(
            color = backdrop.primary.copy(alpha = (if (backdrop.dark) 0.30f else 0.20f) * opacity),
            center = Offset(canvasSize.width * 0.62f, canvasSize.height * 0.26f),
            radius = canvasSize.maxDimension * 0.34f
        )
        drawBlob(
            color = backdrop.secondary.copy(alpha = (if (backdrop.dark) 0.26f else 0.16f) * opacity),
            center = Offset(canvasSize.width * 0.16f, canvasSize.height * 0.74f),
            radius = canvasSize.maxDimension * 0.30f
        )
        drawBlob(
            color = Color.White.copy(alpha = (if (backdrop.dark) 0.10f else 0.34f) * opacity),
            center = Offset(canvasSize.width * 0.84f, canvasSize.height * 0.10f),
            radius = canvasSize.maxDimension * 0.26f
        )
    }

    if (backdrop.overlay.alpha > 0f) {
        drawRect(
            color = backdrop.overlay.copy(alpha = backdrop.overlay.alpha * opacity),
            topLeft = area.topLeft,
            size = area.size
        )
    }
}

private fun DrawScope.drawBlob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** Draws [bitmap] so that it covers [area] (center crop, no stretching). */
private fun DrawScope.drawImageCover(bitmap: ImageBitmap, area: Rect, alpha: Float) {
    if (bitmap.width == 0 || bitmap.height == 0) return
    val sourceWidth = bitmap.width.toFloat()
    val sourceHeight = bitmap.height.toFloat()
    val scale = max(area.width / sourceWidth, area.height / sourceHeight)
    val targetWidth = sourceWidth * scale
    val targetHeight = sourceHeight * scale
    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(
            (area.left + (area.width - targetWidth) / 2f).roundToInt(),
            (area.top + (area.height - targetHeight) / 2f).roundToInt()
        ),
        dstSize = IntSize(targetWidth.roundToInt(), targetHeight.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low
    )
}

// --------------------------------------------------------------- WinUI glass

/** Soft edge band: the reference uses 16 DIP of glass thickness. */
private val WINUI_RIM_BAND = 8.dp

/** Fixed tint of the WinUI glass so it stays clear but readable. */
private const val WINUI_TINT_ALPHA = 0.10f

/** Number of concentric rim bands used to fake the quartic refraction. */
private const val WINUI_RIM_RINGS = 26
private const val WINUI_RIM_STEPS = 28


/**
 * Three box passes approximate a Gaussian blur closely enough for glass and run
 * in pure Kotlin, so every Android version gets the same amount of blur (the
 * old RenderEffect path only existed on API 31+).
 */
private fun blurBitmap(source: Bitmap, radiusPx: Float): Bitmap {
    if (radiusPx < 0.6f) return source
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return source
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val radius = radiusPx.roundToInt().coerceIn(1, 96)
    boxBlurPass(pixels, width, height, radius, horizontal = true)
    boxBlurPass(pixels, width, height, radius, horizontal = false)
    boxBlurPass(pixels, width, height, radius, horizontal = true)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlurPass(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val window = radius * 2 + 1
    val out = IntArray(pixels.size)
    if (horizontal) {
        for (y in 0 until height) {
            var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
            val row = y * width
            for (i in -radius..radius) {
                val c = pixels[row + i.coerceIn(0, width - 1)]
                sumA += (c ushr 24) and 0xFF
                sumR += (c ushr 16) and 0xFF
                sumG += (c ushr 8) and 0xFF
                sumB += c and 0xFF
            }
            for (x in 0 until width) {
                out[row + x] = ((sumA / window) shl 24) or
                    ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or
                    (sumB / window)
                val add = pixels[row + (x + radius + 1).coerceAtMost(width - 1)]
                val sub = pixels[row + (x - radius).coerceAtLeast(0)]
                sumA += ((add ushr 24) and 0xFF) - ((sub ushr 24) and 0xFF)
                sumR += ((add ushr 16) and 0xFF) - ((sub ushr 16) and 0xFF)
                sumG += ((add ushr 8) and 0xFF) - ((sub ushr 8) and 0xFF)
                sumB += (add and 0xFF) - (sub and 0xFF)
            }
        }
    } else {
        for (x in 0 until width) {
            var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
            for (i in -radius..radius) {
                val c = pixels[i.coerceIn(0, height - 1) * width + x]
                sumA += (c ushr 24) and 0xFF
                sumR += (c ushr 16) and 0xFF
                sumG += (c ushr 8) and 0xFF
                sumB += c and 0xFF
            }
            for (y in 0 until height) {
                out[y * width + x] = ((sumA / window) shl 24) or
                    ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or
                    (sumB / window)
                val add = pixels[(y + radius + 1).coerceAtMost(height - 1) * width + x]
                val sub = pixels[(y - radius).coerceAtLeast(0) * width + x]
                sumA += ((add ushr 24) and 0xFF) - ((sub ushr 24) and 0xFF)
                sumR += ((add ushr 16) and 0xFF) - ((sub ushr 16) and 0xFF)
                sumG += ((add ushr 8) and 0xFF) - ((sub ushr 8) and 0xFF)
                sumB += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
    System.arraycopy(out, 0, pixels, 0, out.size)
}

/** Blur radius in pixels for a texture that is [textureWidth] wide instead of [viewportWidth]. */
private fun textureBlurRadius(blurDp: Float, density: Float, textureWidth: Int, viewportWidth: Int): Float {
    if (blurDp <= 0f || viewportWidth <= 0) return 0f
    return blurDp * density * textureWidth / viewportWidth
}

/**
 * Returns a pre blurred copy of the custom background bitmap (or null when the
 * background is the built in gradient, or blur is disabled). The bitmap is
 * kept at most [WINUI_BLUR_MAX_WIDTH] px wide, which is plenty for a wash of
 * blur and much cheaper than blurring the full photo on every recomposition.
 */
@Composable
fun rememberGlassTexture(
    backdrop: BackdropSpec,
    viewport: IntSize,
    blurDp: Float = 0f
): ImageBitmap? {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val viewportWidth = viewport.width
    val viewportHeight = viewport.height
    val blurStep = blurDp.coerceIn(0f, 24f).roundToInt()
    val image by produceState<ImageBitmap?>(
        initialValue = null,
        backdrop,
        viewportWidth,
        viewportHeight,
        blurStep,
        density.density
    ) {
        value = withContext(Dispatchers.Default) {
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                null
            } else {
                runCatching {
                    // The rim is composited from dozens of draws, so the page
                    // backdrop is rasterised once: every rim pass is then a
                    // single image draw instead of a stack of gradients.
                    val targetWidth = (WINUI_TEXTURE_WIDTH / (1f + blurStep * 0.45f))
                        .coerceAtMost(viewportWidth.toFloat())
                        .roundToInt()
                        .coerceAtLeast(64)
                    val source = backdrop.bitmap
                    if (source != null) {
                        // Crop the wallpaper exactly like the page background
                        // (ContentScale.Crop) so a card shows the very pixels
                        // sitting behind it, then bake the page scrim in so the
                        // card never looks brighter than the wallpaper.
                        val outWidth = targetWidth
                        val outHeight = (viewportHeight.toFloat() * outWidth / viewportWidth)
                            .roundToInt()
                            .coerceAtLeast(64)
                        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                        val canvas = AndroidCanvas(out)
                        val bitmap = source.asAndroidBitmap()
                        val scale = max(
                            outWidth.toFloat() / bitmap.width,
                            outHeight.toFloat() / bitmap.height
                        )
                        val drawWidth = bitmap.width * scale
                        val drawHeight = bitmap.height * scale
                        val left = (outWidth - drawWidth) / 2f
                        val top = (outHeight - drawHeight) / 2f
                        val paint = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG)
                        canvas.drawBitmap(
                            bitmap,
                            null,
                            RectF(left, top, left + drawWidth, top + drawHeight),
                            paint
                        )
                        if (backdrop.overlay != Color.Transparent) {
                            canvas.drawColor(backdrop.overlay.toArgb())
                        }
                        val radius = textureBlurRadius(
                            blurDp = blurStep.toFloat(),
                            density = density.density,
                            textureWidth = outWidth,
                            viewportWidth = viewportWidth
                        )
                        blurBitmap(out, radius).asImageBitmap()
                    } else {
                        val height = (viewportHeight.toFloat() * targetWidth / viewportWidth)
                            .roundToInt()
                            .coerceAtLeast(64)
                        val raster = ImageBitmap(targetWidth, height)
                        CanvasDrawScope().draw(
                            density = density,
                            layoutDirection = layoutDirection,
                            canvas = Canvas(raster),
                            size = Size(targetWidth.toFloat(), height.toFloat())
                        ) {
                            drawBackdropLayer(backdrop, Size(targetWidth.toFloat(), height.toFloat()))
                        }
                        val radius = textureBlurRadius(
                            blurDp = blurStep.toFloat(),
                            density = density.density,
                            textureWidth = targetWidth,
                            viewportWidth = viewportWidth
                        )
                        blurBitmap(raster.asAndroidBitmap(), radius).asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return image
}

private const val WINUI_TEXTURE_WIDTH = 960f

/**
 * WinUI glass, drawn with plain draw calls:
 *
 *  1. The whole card is painted with exactly the backdrop that sits behind it
 *     (optionally the pre blurred copy). Drawing it in page coordinates and
 *     only translating the canvas guarantees the same crop and aspect as the
 *     page background - no stretching, no inner rectangle.
 *  2. The 16 DIP rim is split into [WINUI_RIM_STEPS] concentric rounded rings.
 *     Each ring redraws the same backdrop scaled about the card center; the
 *     magnification grows towards the border (1.0 -> 2.6), so the band shows
 *     progressively stretched interior content, like the quartic thickness
 *     displacement of the Win2D reference.
 */
/** Rasterised page backdrop warped into the card rectangle. */
private class WinUIPatch(
    val bitmap: Bitmap,
    val columns: Int,
    val rows: Int,
    val vertices: FloatArray
)

private const val WINUI_MESH_COLUMNS = 64
private const val WINUI_MESH_ROWS = 22
private const val WINUI_DISPLACEMENT = 0.55f

private fun roundedBoxDistance(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float
): Float {
    val qx = abs(x - width / 2f) - (width / 2f - radius)
    val qy = abs(y - height / 2f) - (height / 2f - radius)
    return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - radius
}

private fun roundedBoxNormal(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float
): Offset {
    val e = 0.75f
    val dx = roundedBoxDistance(x + e, y, width, height, radius) -
        roundedBoxDistance(x - e, y, width, height, radius)
    val dy = roundedBoxDistance(x, y + e, width, height, radius) -
        roundedBoxDistance(x, y - e, width, height, radius)
    val length = hypot(dx, dy)
    return if (length < 0.0001f) Offset.Zero else Offset(dx / length, dy / length)
}

/**
 * Crops the card rectangle out of the page texture and builds a mesh whose
 * destination vertices are displaced along the local surface normal.
 *
 * This is the Android counterpart of the Win2D `DisplacementMapEffect` in the
 * reference: the displacement magnitude follows the quartic thickness curve
 * `F(d) = a * (d - t)^4 + t` (t = 16 DIP), the offset points towards the card
 * interior, and the direction is the SDF normal, so the top and bottom edges
 * only shift vertically (the previous ring version zoomed both axes around the
 * card centre, which is what made the band look horizontally stretched).
 * Skia interpolates between the mesh vertices, which makes the rim smooth
 * without any per-frame gradient work.
 */
private fun buildWinUIPatch(
    texture: ImageBitmap,
    anchor: Offset,
    cardSize: IntSize,
    viewport: IntSize,
    bandPx: Float,
    cornerPx: Float,
    strength: Float
): WinUIPatch? {
    if (cardSize.width < 8 || cardSize.height < 8) return null
    if (viewport.width <= 0 || viewport.height <= 0) return null
    val tex = runCatching { texture.asAndroidBitmap() }.getOrNull() ?: return null
    if (tex.isRecycled) return null
    val cover = max(
        tex.width.toFloat() / viewport.width,
        tex.height.toFloat() / viewport.height
    )
    val offsetX = (tex.width - viewport.width * cover) / 2f
    val offsetY = (tex.height - viewport.height * cover) / 2f
    val left = (anchor.x * cover + offsetX).roundToInt()
    val top = (anchor.y * cover + offsetY).roundToInt()
    val width = (cardSize.width * cover).roundToInt()
    val height = (cardSize.height * cover).roundToInt()
    if (width <= 0 || height <= 0) return null
    if (left < 0 || top < 0 || left + width > tex.width || top + height > tex.height) {
        return null
    }
    val crop = runCatching { Bitmap.createBitmap(tex, left, top, width, height) }
        .getOrNull() ?: return null

    val w = cardSize.width.toFloat()
    val h = cardSize.height.toFloat()
    val band = bandPx.coerceAtMost(min(w, h) * 0.45f).coerceAtLeast(1f)
    val maxDistance = min(w, h)
    val a = (maxDistance - band) / (band * band * band * band)
    val columns = WINUI_MESH_COLUMNS
    val rows = WINUI_MESH_ROWS
    val vertices = FloatArray((columns + 1) * (rows + 1) * 2)

    var index = 0
    for (row in 0..rows) {
        val sy = h * row / rows
        for (column in 0..columns) {
            val sx = w * column / columns
            var px = sx
            var py = sy
            // Inverse of the displacement: the source pixel s is drawn at the
            // destination p that samples it, i.e. s = p + normal * magnitude.
            repeat(2) {
                val d = -roundedBoxDistance(px, py, w, h, cornerPx)
                if (d > 0f && d < band) {
                    val edge = d - band
                    val f = a * edge * edge * edge * edge + band
                    val magnitude = (f - d) * strength
                    val normal = roundedBoxNormal(px, py, w, h, cornerPx)
                    px += normal.x * magnitude
                    py += normal.y * magnitude
                }
            }
            vertices[index++] = px
            vertices[index++] = py
        }
    }
    return WinUIPatch(crop, columns, rows, vertices)
}

private val winUIMeshPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
    isFilterBitmap = true
    isDither = true
}

private fun DrawScope.drawWinUIPatch(
    patch: WinUIPatch,
    shape: Shape,
    tintAlpha: Float,
    tintColor: Color
) {
    val rounded = shape.createOutline(size, layoutDirection, this) as? Outline.Rounded ?: return
    val path = Path().apply { addRoundRect(rounded.roundRect) }
    clipPath(path) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(
                patch.bitmap,
                patch.columns,
                patch.rows,
                patch.vertices,
                0,
                null,
                0,
                winUIMeshPaint
            )
        }
    }
    if (tintAlpha > 0.001f) {
        drawRect(tintColor.copy(alpha = (tintAlpha * 0.5f).coerceIn(0f, 0.35f)))
    }
}


/**
 * WinUI glass, drawn with plain page-coordinate calls.
 *
 * The backdrop is painted in page coordinates (so it lines up with the wallpaper
 * instead of being stretched to the card), then a [WINUI_RIM_BAND] wide rim is
 * rebuilt from [WINUI_RIM_RINGS] concentric copies: the outermost ring is scaled
 * the most, so the content inside the rim gets compressed like real thick glass.
 * [texture] is an optional rasterized backdrop; using it keeps the per-ring cost
 * to one bitmap draw on devices where it builds successfully.
 */
private fun DrawScope.drawWinUIRings(
    backdrop: BackdropSpec,
    texture: ImageBitmap?,
    anchor: Offset,
    viewport: Size,
    cardSize: Size,
    shape: Shape,
    bandPx: Float,
    tintAlpha: Float,
    tintColor: Color
) {
    val cardCenter = anchor + Offset(cardSize.width / 2f, cardSize.height / 2f)
    val paintBackdrop: DrawScope.() -> Unit = {
        if (texture != null) {
            drawImage(
                image = texture,
                dstSize = IntSize(viewport.width.toInt(), viewport.height.toInt())
            )
        } else {
            drawBackdropLayer(backdrop, viewport)
        }
    }

    // Flat base: the card content is exactly the wallpaper behind it, no tint,
    // no white wash, so the card never looks like a separate bright layer.
    withTransform({ translate(-anchor.x, -anchor.y) }) {
        this@drawWinUIRings.paintBackdrop()
    }

    val rect = RoundRect(
        rect = androidx.compose.ui.geometry.Rect(Offset.Zero, cardSize),
        cornerRadius = CornerRadius(22.dp.toPx())
    )
    for (i in 0 until WINUI_RIM_RINGS) {
        val start = (i.toFloat() / WINUI_RIM_RINGS).pow(1.4f)
        val end = ((i + 1).toFloat() / WINUI_RIM_RINGS).pow(1.4f)
        val ring = roundedRing(rect, bandPx * start, (bandPx * end + 1f).coerceAtMost(bandPx)) ?: continue
        val t = (start + end) / 2f
        val magnification = 1f + 0.85f * (1f - t).pow(2.6f)
        clipPath(ring) {
            withTransform({
                translate(-anchor.x, -anchor.y)
                scale(magnification, magnification, pivot = cardCenter)
            }) {
                this@drawWinUIRings.paintBackdrop()
            }
        }
    }
}

/** Cheap WinUI fallback: blurred backdrop fill only, no edge warp. */
private fun DrawScope.drawWinUIFlat(
    backdrop: BackdropSpec,
    blurred: ImageBitmap?,
    anchor: Offset,
    viewport: Size,
    tintAlpha: Float,
    tintColor: Color
) {
    if (viewport.width <= 0f || viewport.height <= 0f) return
    val painted = if (blurred != null) backdrop.copy(bitmap = blurred) else backdrop
    withTransform({ translate(-anchor.x, -anchor.y) }) {
        drawBackdropLayer(painted, viewport)
    }
    if (tintAlpha > 0.001f) {
        drawRect(tintColor.copy(alpha = (tintAlpha * 0.5f).coerceIn(0f, 0.35f)))
    }
}

// ----------------------------------------------------------------- glass card

/**
 * Rounded task card. The look is selected by [LocalRenderScheme]:
 *
 *  - [RenderScheme.Solid]: one plain fill colour at the user opacity plus a
 *    simple outline. No blur, refraction, sheen or edge light.
 *  - [RenderScheme.Liquid]: the original backdrop sampling glass (sharp 1.01x
 *    copy + refraction rim + frost + highlight).
 *  - [RenderScheme.LiquidWinUI]: the WinUI glass. The whole card is covered by
 *    a blurred copy of the backdrop, and a 16 DIP rim is built from concentric
 *    scaled copies of the sharp backdrop (reference: quartic thickness
 *    displacement). Pure drawing calls, identical on every Android version.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    completed: Boolean = false,
    tint: GlassTint = GlassTint.Light,
    shape: Shape = RoundedCornerShape(22.dp),
    scheme: RenderScheme = LocalRenderScheme.current,
    dimmed: Boolean = false,
    renderOffset: () -> Offset = { Offset.Zero },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    val viewport = LocalBackdropViewport.current
    val density = LocalDensity.current
    val useDarkGlass = tint == GlassTint.Dark || backdrop.dark

    val glassFillAlpha = LocalGlassFillAlpha.current.coerceIn(0f, 1f)
    val solidFillAlpha = LocalSolidFillAlpha.current.coerceIn(0f, 1f)
    val solidFillColor = LocalSolidFillColor.current
    val solidStrokeWidth = LocalSolidStrokeWidth.current
    val winuiFillAlpha = LocalWinUIFillAlpha.current.coerceIn(0f, 1f)
    val winuiBlur = LocalWinUIBlur.current

    var position by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val canSampleBackdrop = viewport.width > 0 &&
        viewport.height > 0 &&
        cardSize.width > 0 &&
        cardSize.height > 0

    val isSolid = scheme == RenderScheme.Solid
    val isLiquid = scheme == RenderScheme.Liquid
    val isWinUI = scheme == RenderScheme.LiquidWinUI
    val texture = if (isWinUI) LocalGlassTexture.current else null

    val winuiPerformance = LocalWinUIPerformanceMode.current
    val renderedWinUI = isWinUI && canSampleBackdrop
    val sampledGlass = isLiquid && canSampleBackdrop

    val matteGlass = !isSolid && !sampledGlass && !renderedWinUI

    val tintColor = if (useDarkGlass) Color.Black else Color.White
    val viewportSize = Size(viewport.width.toFloat(), viewport.height.toFloat())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (completed) 0.5f else 1f }
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
            .clip(shape)
            .onGloballyPositioned { coordinates ->
                val newPosition = coordinates.positionInRoot()
                if (newPosition != position) position = newPosition
                if (coordinates.size != cardSize) cardSize = coordinates.size
            }
    ) {
        if (renderedWinUI) {
            val cardSizePx = Size(cardSize.width.toFloat(), cardSize.height.toFloat())
            val bandPx = with(density) { WINUI_RIM_BAND.toPx() }
            Canvas(modifier = Modifier.matchParentSize()) {
                if (winuiPerformance) {
                    drawWinUIFlat(
                        backdrop = backdrop,
                        blurred = texture,
                        anchor = position + renderOffset(),
                        viewport = viewportSize,
                        tintAlpha = winuiFillAlpha,
                        tintColor = tintColor
                    )
                } else {
                    drawWinUIRings(
                        backdrop = backdrop,
                        texture = texture,
                        anchor = position + renderOffset(),
                        viewport = viewportSize,
                        cardSize = cardSizePx,
                        shape = shape,
                        bandPx = bandPx,
                        tintAlpha = WINUI_TINT_ALPHA,
                        tintColor = tintColor
                    )
                }
            }
        }

        // Original liquid glass: sharp 1.01x backdrop copy plus the rim.
        if (sampledGlass) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val anchor = position + renderOffset()
                val cardCenter = anchor + Offset(
                    cardSize.width / 2f,
                    cardSize.height / 2f
                )
                withTransform({
                    translate(-anchor.x, -anchor.y)
                    scale(
                        scaleX = GLASS_MAGNIFICATION,
                        scaleY = GLASS_MAGNIFICATION,
                        pivot = cardCenter
                    )
                }) {
                    drawBackdropLayer(
                        backdrop = backdrop,
                        canvasSize = Size(
                            viewport.width.toFloat(),
                            viewport.height.toFloat()
                        )
                    )
                }
            }

            val rimWidthPx = with(density) { 20.dp.toPx() }
            val rimInflatePx = with(density) { 40.dp.toPx() }
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRefractionRim(
                    backdrop = backdrop,
                    viewport = Size(viewport.width.toFloat(), viewport.height.toFloat()),
                    position = position + renderOffset(),
                    shape = shape,
                    bandPx = rimWidthPx,
                    inflatePx = rimInflatePx
                )
            }
        }

        // Fill layers.
        if (isSolid) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(solidFillColor.copy(alpha = solidFillAlpha))
            )
        } else if (renderedWinUI) {
            // The WinUI layer paints its own base and rim; nothing extra here.
        } else {
            // Liquid glass: the tint gradient sits on top of the sampled
            // backdrop, so its alpha must react to the transparency slider.
            val fillOpacity = if (isWinUI) winuiFillAlpha else glassFillAlpha
            var frostTopAlpha = fillOpacity * 0.82f
            var frostBottomAlpha = (fillOpacity * 1.12f).coerceAtMost(1f)
            if (!sampledGlass) {
                frostTopAlpha = max(fillOpacity, if (useDarkGlass) 0.52f else 0.72f)
                frostBottomAlpha = max(fillOpacity, if (useDarkGlass) 0.60f else 0.62f)
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                tintColor.copy(alpha = frostTopAlpha),
                                tintColor.copy(alpha = frostBottomAlpha)
                            )
                        )
                    )
            )
        }

        // Diagonal sheen. The WinUI shader paints its own glare bands, the solid
        // scheme must stay a clean flat colour.
        if (matteGlass || sampledGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (useDarkGlass) 0.06f else 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 620f
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    if (isSolid) {
                        drawSolidOutline(shape, solidStrokeWidth)
                    } else if (!isWinUI) {
                        drawGlassHighlight(
                            shape = shape,
                            darkGlass = useDarkGlass,
                            strokeWidth = 1.5.dp
                        )
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )

        // Whole-card dimming (used while the card is being dragged): it is a
        // single layer over the complete rounded rect, never an inner box.
        if (dimmed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.22f))
            )
        }
    }
}

/**
 * Paints the bent-glass rim: the same backdrop redrawn slightly scaled down
 * around the card center and clipped to concentric rounded rings, with the
 * opacity fading from the border inwards. It mirrors the reference shader
 * (sample towards the center, displacement growing non linearly towards the
 * edge) using plain drawing commands only - no blend modes, no shader - so the
 * rim can never erase the card behind it and it also runs below Android 13.
 */
private fun DrawScope.drawRefractionRim(
    backdrop: BackdropSpec,
    viewport: Size,
    position: Offset,
    shape: Shape,
    bandPx: Float,
    inflatePx: Float
) {
    val rounded = shape.createOutline(size, layoutDirection, this) as? Outline.Rounded ?: return
    val rect = rounded.roundRect
    if (rect.width <= 0f || rect.height <= 0f) return

    val cardCenter = Offset(size.width / 2f, size.height / 2f)
    for (i in 0 until REFRACT_STEPS) {
        val t0 = i / REFRACT_STEPS.toFloat()          // 0 = border, 1 = inner edge
        val t1 = (i + 1) / REFRACT_STEPS.toFloat()
        val t = (t0 + t1) / 2f
        val falloff = (1f - t) * (1f - t) * (1f - t)
        val alpha = 0.85f * falloff
        if (alpha < 0.02f) continue
        val ring = roundedRing(rect, t0 * bandPx, t1 * bandPx) ?: continue
        clipPath(ring, ClipOp.Intersect) {
            withTransform({
                translate(-position.x, -position.y)
                scale(REFRACT_SCALE, REFRACT_SCALE, pivot = position + cardCenter)
            }) {
                drawBackdropLayer(backdrop, viewport, alpha = alpha, inflate = inflatePx)
            }
        }
    }
}

/** Filled ring between two insets of [rect]; null when the insets collapse it. */
private fun roundedRing(rect: RoundRect, outerInset: Float, innerInset: Float): Path? {
    val outer = insetRoundedRect(rect, outerInset) ?: return null
    val inner = insetRoundedRect(rect, innerInset) ?: return null
    return Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(outer)
        addRoundRect(inner)
    }
}

private fun insetRoundedRect(rect: RoundRect, inset: Float): RoundRect? {
    val width = rect.width - inset * 2f
    val height = rect.height - inset * 2f
    if (width <= 0f || height <= 0f) return null
    val radius = (rect.topLeftCornerRadius.x - inset).coerceAtLeast(0f)
    return RoundRect(
        left = rect.left + inset,
        top = rect.top + inset,
        right = rect.right - inset,
        bottom = rect.bottom - inset,
        radiusX = radius,
        radiusY = radius
    )
}

/**
 * Plain outline of the solid scheme. No gradient, no inner sheen: the solid card
 * is intentionally a clean flat fill with an optional outline.
 */
private fun DrawScope.drawSolidOutline(shape: Shape, strokeWidth: Dp) {
    val width = strokeWidth.toPx()
    if (width <= 0.01f) return
    val rounded = shape.createOutline(size, layoutDirection, this) as? Outline.Rounded ?: return
    val rect = rounded.roundRect
    drawRoundRect(
        color = Color.White.copy(alpha = 0.55f),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(rect.topLeftCornerRadius.x, rect.topLeftCornerRadius.y),
        style = Stroke(width = width)
    )
}

/**
 * Edge highlight that follows the rounded outline, plus an inner sheen band on
 * the upper half, approximating the reflection of a real glass pane.
 *
 * Only [Outline.Rounded] shapes (which is what every card in this app uses) can
 * be stroked here; any other shape simply skips the highlight, which keeps the
 * component safe to reuse.
 */
private fun DrawScope.drawGlassHighlight(
    shape: Shape,
    darkGlass: Boolean,
    strokeWidth: Dp
) {
    val rounded = shape.createOutline(size, layoutDirection, this) as? Outline.Rounded ?: return
    val rect = rounded.roundRect

    val edgeWidth = strokeWidth.toPx().coerceAtLeast(0f)
    if (edgeWidth > 0.01f) {
        val edgeBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (darkGlass) 0.60f else 0.90f),
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = if (darkGlass) 0.42f else 0.60f)
            ),
            start = Offset(rect.left, rect.top),
            end = Offset(rect.right, rect.bottom)
        )
        drawRoundRect(
            brush = edgeBrush,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(rect.topLeftCornerRadius.x, rect.topLeftCornerRadius.y),
            style = Stroke(width = edgeWidth)
        )
    }

    val inset = 2.dp.toPx()
    if (rect.width > inset * 2 && rect.height > inset * 2) {
        val innerTop = rect.top + inset
        val innerLeft = rect.left + inset
        val innerSize = Size(rect.width - inset * 2, rect.height - inset * 2)
        val innerRadius = CornerRadius(
            (rect.topLeftCornerRadius.x - inset).coerceAtLeast(0f),
            (rect.topLeftCornerRadius.y - inset).coerceAtLeast(0f)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (darkGlass) 0.16f else 0.26f),
                    Color.Transparent
                ),
                startY = innerTop,
                endY = innerTop + innerSize.height * 0.55f
            ),
            topLeft = Offset(innerLeft, innerTop),
            size = innerSize,
            cornerRadius = innerRadius,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
