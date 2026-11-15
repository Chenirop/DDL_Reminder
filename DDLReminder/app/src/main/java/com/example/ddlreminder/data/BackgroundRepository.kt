package com.example.ddlreminder.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The whole background state of the app: the persisted uri picked by the user
 * and the already decoded bitmap that the glass cards sample from.
 */
data class BackgroundState(
    val uri: Uri?,
    val bitmap: ImageBitmap?,
    val loading: Boolean = false
)

/**
 * Owns the user background picture. The uri is persisted in [AppPreferences]
 * (with a persisted read grant), the bitmap is decoded off the main thread and
 * published through [state] so the UI and the glass cards can react together.
 */
class BackgroundRepository(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = AppPreferences(appContext)

    private val _state = MutableStateFlow(
        BackgroundState(uri = preferences.backgroundUri?.let(Uri::parse), bitmap = null)
    )
    val state: StateFlow<BackgroundState> = _state.asStateFlow()

    /** Decodes the persisted picture after a cold start. */
    suspend fun loadStored() {
        val uri = _state.value.uri ?: return
        _state.value = _state.value.copy(loading = true)
        val bitmap = withContext(Dispatchers.IO) { decode(uri) }
        _state.value = BackgroundState(uri = uri, bitmap = bitmap)
    }

    /** Persists and decodes [uri]. Returns true when the picture is usable. */
    suspend fun setBackground(uri: Uri): Boolean {
        _state.value = _state.value.copy(loading = true)
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            decode(uri)
        }
        return if (bitmap != null) {
            preferences.backgroundUri = uri.toString()
            _state.value = BackgroundState(uri = uri, bitmap = bitmap)
            true
        } else {
            _state.value = _state.value.copy(loading = false)
            false
        }
    }

    /** Removes the custom picture and falls back to the default gradient. */
    fun clear() {
        preferences.backgroundUri = null
        _state.value = BackgroundState(uri = null, bitmap = null)
    }

    private fun decode(uri: Uri): ImageBitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(uri)
            } else {
                decodeWithBitmapFactory(uri)
            }
        }.getOrNull()
    }

    private fun decodeWithImageDecoder(uri: Uri): ImageBitmap {
        val metrics = appContext.resources.displayMetrics
        val screenWidth = metrics.widthPixels.coerceAtLeast(1)
        val screenHeight = metrics.heightPixels.coerceAtLeast(1)
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM

            val width = info.size.width
            val height = info.size.height
            if (width > 0 && height > 0) {
                // Enough pixels to cover the screen, but never upscale.
                val cover = max(
                    screenWidth.toFloat() / width,
                    screenHeight.toFloat() / height
                )
                var scale = min(1f, cover)
                val coveredPixels = width * scale * height * scale
                if (coveredPixels > MAX_DECODED_PIXELS) {
                    scale *= sqrt(MAX_DECODED_PIXELS / coveredPixels)
                }
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceIn(1, width),
                    (height * scale).roundToInt().coerceIn(1, height)
                )
            }
        }.asImageBitmap()
    }

    private fun decodeWithBitmapFactory(uri: Uri): ImageBitmap? {
        val metrics = appContext.resources.displayMetrics
        val screenWidth = metrics.widthPixels.coerceAtLeast(1)
        val screenHeight = metrics.heightPixels.coerceAtLeast(1)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        var sampleSize = 1
        while (width / (sampleSize * 2) >= screenWidth &&
            height / (sampleSize * 2) >= screenHeight
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }?.asImageBitmap()
    }

    private companion object {
        const val MAX_DECODED_PIXELS = 8_000_000f
    }
}
