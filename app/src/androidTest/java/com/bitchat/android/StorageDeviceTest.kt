package com.bitchat.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bitchat.android.features.file.FileUtils
import com.bitchat.android.features.media.ImageUtils
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.services.ChatHistoryStore
import com.bitchat.android.util.AppConstants
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses only dedicated QA preferences and files; never clears the installed profile or chats. */
@RunWith(AndroidJUnit4::class)
class StorageDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedHistorySurvivesStoreRecreationWithoutPlaintextOnDisk() {
        val name = "outpost_instrumented_history"
        fun prefs() = EncryptedSharedPreferences.create(context, name,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val p = prefs()
        try {
            val entry = ChatHistoryStore.Entry("qa-peer", true, "qa-id", "QA", "qa-secret-do-not-store-plaintext",
                "Message", 1234L, "qa-self", true, ChatHistoryStore.Status.from(DeliveryStatus.Sending))
            ChatHistoryStore(p).save(1, listOf(entry))
            val restored = ChatHistoryStore(prefs()).load().single()
            assertEquals(entry, restored)
            assertEquals(DeliveryStatus.Sending, restored.message().deliveryStatus)
            val xml = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
            assertFalse(xml.contains(entry.content))
            assertFalse(xml.contains("qa-peer"))
            ChatHistoryStore(prefs()).save(2, emptyList())
            assertTrue(ChatHistoryStore(prefs()).load().isEmpty())
        } finally { p.edit().clear().commit(); context.deleteSharedPreferences(name) }
    }

    @Test fun oversizedAttachmentIsRejectedAndTemporaryCopyRemoved() {
        val input = File.createTempFile("qa_oversize_", ".bin", context.cacheDir)
        val outgoing = File(context.filesDir, "files/outgoing")
        val before = outgoing.listFiles()?.map { it.name }?.toSet().orEmpty()
        try {
            input.outputStream().use { out -> out.write(ByteArray(AppConstants.Media.MAX_FILE_SIZE_BYTES.toInt() + 1)) }
            assertNull(FileUtils.copyFileForSending(context, Uri.fromFile(input), "qa.bin"))
            assertEquals(before, outgoing.listFiles()?.map { it.name }?.toSet().orEmpty())
        } finally { input.delete() }
    }

    @Test fun largeImageIsSampledAndCompressedWithinMeshBudget() {
        val input = File.createTempFile("qa_image_", ".png", context.cacheDir)
        var output: File? = null
        val original = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(2400 * 1600) { index -> 0xff000000.toInt() or ((index * 1103515245 + 12345) and 0xffffff) }
            original.setPixels(pixels, 0, 2400, 0, 0, 2400, 1600)
            input.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            output = File(requireNotNull(ImageUtils.downscalePathAndSaveToAppFiles(context, input.path)))
            assertTrue(output.length() in 1..AppConstants.Media.MAX_FILE_SIZE_BYTES)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.path, bounds)
            assertTrue(bounds.outWidth in 1..512)
            assertTrue(bounds.outHeight in 1..512)
        } finally { original.recycle(); input.delete(); output?.delete() }
    }
}
