package com.bitchat.android.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AppShareUtils {
    private const val TAG = "AppShareUtils"

    /**
     * Extracts the base APK and shares it via the native Android Share Sheet.
     */
    suspend fun shareApkOffline(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Locate the running APK
                val apkPath = context.applicationInfo.sourceDir
                val originalApk = File(apkPath)

                // 2. Define the destination file in cacheDir
                val copiedApk = File(File(context.cacheDir, "shared_apks").apply { mkdirs() }, "outpost-offline.apk")

                // 3. Copy the APK to ensure FileProvider has seamless access
                FileInputStream(originalApk).use { input ->
                    FileOutputStream(copiedApk).use { output ->
                        input.copyTo(output)
                    }
                }

                // 5. Generate content URI using FileProvider
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    copiedApk
                )

                // 6. Launch the intent
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, apkUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooserIntent = Intent.createChooser(shareIntent, "Share Outpost offline")
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooserIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share APK: ${e.message}", e)
            }
        }
    }
}
