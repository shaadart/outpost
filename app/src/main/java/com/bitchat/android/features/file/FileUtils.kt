package com.bitchat.android.features.file

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {

    private const val TAG = "FileUtils"

    /** Remove abandoned preparations and evicted attachments after a grace period for in-flight work. */
    fun pruneUnreferencedMedia(context: Context, retained: Set<String>, nowMs: Long = System.currentTimeMillis()): Int {
        val cutoff = nowMs - 24L * 60 * 60 * 1000
        var removed = 0
        for (base in listOf(context.filesDir, context.cacheDir)) {
            for (name in listOf("files", "images", "voicenotes")) {
                val root = File(base, name).canonicalFile
                val prefix = root.path + File.separator
                if (!root.isDirectory) continue
                root.walkTopDown().onEnter { it.canonicalPath == root.path || it.canonicalPath.startsWith(prefix) }
                    .filter { it.isFile && it.lastModified() < cutoff }.forEach { file ->
                        val canonical = file.canonicalPath
                        if (canonical.startsWith(prefix) && file.path !in retained && canonical !in retained && file.delete()) removed++
                    }
            }
        }
        return removed
    }

    /** Delete only app-owned media, and keep files still referenced by another conversation. */
    fun deleteMessageFiles(context: Context, messages: List<com.bitchat.android.model.BitchatMessage>, retained: Set<String>) {
        val roots = listOf(context.filesDir, context.cacheDir).flatMap { root ->
            listOf("files", "images", "voicenotes").map { File(root, it).canonicalPath + File.separator }
        }
        messages.filter { it.type != com.bitchat.android.model.BitchatMessageType.Message }
            .map { it.content }.distinct().filterNot { it in retained }.forEach { path ->
                val file = File(path).canonicalFile
                if (roots.any { file.path.startsWith(it) } && file.isFile) {
                    check(file.delete()) { "Could not delete attachment" }
                }
            }
    }

    /**
     * Save a file from URI to app's file directory with unique filename
     */
    fun saveFileFromUri(
        context: Context,
        uri: Uri,
        originalName: String? = null
    ): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "❌ Failed to open input stream for URI: $uri")
                return null
            }
            Log.d(TAG, "📂 Opened input stream successfully")

            // Determine file extension
            val extension = originalName?.substringAfterLast(".") ?: "bin"
            val fileName = "file_${System.currentTimeMillis()}.$extension"

            // Create incoming dir if needed
            val incomingDir = File(context.filesDir, "files/incoming").apply {
                if (!exists()) mkdirs()
            }

            val file = File(incomingDir, fileName)

            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Saved file to: ${file.absolutePath}")
            file.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file from URI", e)
            null
        }
    }

    /**
     * Copy file to app's outgoing directory for sending
     */
    fun copyFileForSending(context: Context, uri: Uri, originalName: String? = null): String? {
        var target: File? = null
        return try {
            val displayName = originalName ?: context.contentResolver.query(uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "file.bin"
            val base = displayName.substringBeforeLast('.', displayName).take(64)
                .replace(Regex("[^A-Za-z0-9._-]"), "_").takeIf { it.isNotBlank() } ?: "file"
            val ext = displayName.substringAfterLast('.', "bin").filter { it.isLetterOrDigit() }.take(12).ifEmpty { "bin" }
            val dir = File(context.filesDir, "files/outgoing").apply { check(isDirectory || mkdirs()) }
            target = File.createTempFile("${base}_".padEnd(3, '_'), ".$ext", dir)
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open file")
            input.use { source -> target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= com.bitchat.android.util.AppConstants.Media.MAX_FILE_SIZE_BYTES) { "File too large" }
                    output.write(buffer, 0, count)
                }
            } }
            target.absolutePath
        } catch (e: Exception) {
            target?.delete()
            Log.w(TAG, "Could not attach selected file", e)
            null
        }
    }

    /**
     * Get MIME type for a file based on extension
     */
    fun getMimeTypeFromExtension(fileName: String): String {
        return when (fileName.substringAfterLast(".", "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            else -> "application/octet-stream"
        }
    }

    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024.0
            unitIndex++
        }
        return "%.1f %s".format(size, units[unitIndex])
    }

    /**
     * Check if file is viewable in system viewer
     */
    fun isFileViewable(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf(
            "pdf", "txt", "json", "xml", "html", "htm", "csv",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
        )
    }

    /**
     * Save an incoming file packet to app storage and return absolute path.
     * Mirrors existing behavior used in MessageHandler (preserves names and folders).
     */
    fun saveIncomingFile(
        context: Context,
        file: com.bitchat.android.model.BitchatFilePacket
    ): String {
        require(file.content.size <= com.bitchat.android.util.AppConstants.Media.MAX_FILE_SIZE_BYTES)
        val mediaDirs = listOf("files", "images", "voicenotes").map { File(context.filesDir, it) }
        val used = mediaDirs.sumOf { root -> if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L }
        check(used + file.content.size <= 200L * 1024 * 1024) { "Attachment storage is full; clear an old chat" }
        val isImage = file.mimeType.startsWith("image/", ignoreCase = true)
        val dir = File(context.filesDir, if (isImage) "images/incoming" else "files/incoming")
            .apply { check(isDirectory || mkdirs()) }
        val safeName = file.fileName.take(100).replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.')
        val base = safeName.substringBeforeLast('.', safeName).ifBlank { "attachment" }
        val extension = safeName.substringAfterLast('.', "bin").take(12).filter { it.isLetterOrDigit() }.ifBlank { "bin" }
        val out = File.createTempFile("${base}_".padEnd(3, '_'), ".$extension", dir)
        try { out.outputStream().use { it.write(file.content) } }
        catch (e: Exception) { out.delete(); throw e }
        return out.absolutePath
    }

    /**
     * Classify BitchatMessageType from MIME string used in file messages.
     */
    fun messageTypeForMime(mime: String): com.bitchat.android.model.BitchatMessageType {
        val lower = mime.lowercase()
        return when {
            lower.startsWith("image/") -> com.bitchat.android.model.BitchatMessageType.Image
            lower.startsWith("audio/") -> com.bitchat.android.model.BitchatMessageType.Audio
            else -> com.bitchat.android.model.BitchatMessageType.File
        }
    }

    /**
     * Recursively delete all media files (incoming and outgoing)
     * Used for Panic Mode cleanup
     */
    fun clearAllMedia(context: Context) {
        try {
            // Clear files dir subdirectories (legacy storage and outgoing)
            val filesDir = context.filesDir
            val dirsToClear = listOf(
                "files/incoming",
                "files/outgoing",
                "images/incoming",
                "images/outgoing",
                "voicenotes"
            )

            dirsToClear.forEach { subDir ->
                val dir = File(filesDir, subDir)
                if (dir.exists()) {
                    dir.deleteRecursively()
                    Log.d(TAG, "Deleted media directory from filesDir: $subDir")
                }
            }
            
            // Clear cache dir subdirectories (new incoming storage)
            // Note: cacheDir.deleteRecursively() below would handle this, but being explicit ensures these
            // specific media folders are targeted even if full cache clear fails or is modified later.
            val cacheDir = context.cacheDir
            val cacheDirsToClear = listOf(
                "files/incoming",
                "images/incoming"
            )
            
            cacheDirsToClear.forEach { subDir ->
                val dir = File(cacheDir, subDir)
                if (dir.exists()) {
                    dir.deleteRecursively()
                    Log.d(TAG, "Deleted media directory from cacheDir: $subDir")
                }
            }
            
            // Also clear entire cache dir as a catch-all
            context.cacheDir.deleteRecursively()
            Log.d(TAG, "Cleared entire cache directory")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear media files", e)
        }
    }
}
