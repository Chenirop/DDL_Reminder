package com.example.ddlreminder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.ddlreminder.data.TaskAttachmentEntity
import java.io.File
import java.util.UUID

/**
 * Stores the files the user attaches to a task in the app private storage and
 * hands them to the system's default viewer through a FileProvider.
 */
object AttachmentStore {

    private const val DIRECTORY = "attachments"
    private const val PROVIDER_SUFFIX = ".fileprovider"

    /** Copies [uri] into the private attachments folder and describes the copy. */
    fun import(context: Context, uri: Uri): TaskAttachmentEntity {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "attachment-${System.currentTimeMillis()}"
        val mimeType = resolver.getType(uri) ?: guessMimeType(displayName)

        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}-${sanitize(displayName)}")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法读取所选文件")

        return TaskAttachmentEntity(
            taskId = 0L,
            fileName = displayName,
            localPath = target.absolutePath,
            mimeType = mimeType,
            isImage = mimeType?.startsWith("image/") == true ||
                displayName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS,
            createdAt = System.currentTimeMillis()
        )
    }

    /** Opens the private copy with whatever app the device uses for its type. */
    fun open(context: Context, attachment: TaskAttachmentEntity): Boolean {
        val file = File(attachment.localPath)
        if (!file.exists()) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + PROVIDER_SUFFIX,
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachment.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                // Fall back to a generic type so at least a viewer can be offered.
                intent.setDataAndType(uri, "*/*")
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun delete(localPath: String) {
        runCatching { File(localPath).delete() }
    }

    /** Removes every stored attachment file, e.g. after a full data replace. */
    fun clearAll(context: Context) {
        runCatching {
            File(context.filesDir, DIRECTORY).listFiles()?.forEach { it.delete() }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun guessMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
}
