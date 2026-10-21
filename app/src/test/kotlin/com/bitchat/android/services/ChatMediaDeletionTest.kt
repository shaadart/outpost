package com.bitchat.android.services

import android.os.Build
import com.bitchat.android.features.file.FileUtils
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.BitchatMessageType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], manifest = Config.NONE)
class ChatMediaDeletionTest {
    @Test fun `clear deletes owned media but preserves a file referenced in another chat`() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.filesDir, "files/outgoing").apply { mkdirs() }
        val deleted = File(dir, "delete.txt").apply { writeText("secret") }
        val shared = File(dir, "shared.txt").apply { writeText("keep") }
        val messages = listOf(deleted, shared).map { BitchatMessage(sender = "Alice", timestamp = java.util.Date(1000), content = it.path, type = BitchatMessageType.File) }
        FileUtils.deleteMessageFiles(context, messages, setOf(shared.path))
        assertFalse(deleted.exists())
        assertTrue(shared.exists())
    }

    @Test fun `untrusted paths and text that resembles a path cannot delete other app files`() {
        val context = RuntimeEnvironment.getApplication()
        val protected = File(context.filesDir, "identity-key").apply { writeText("key") }
        val media = File(context.filesDir, "files/outgoing/text.txt").apply { parentFile!!.mkdirs(); writeText("file") }
        val messages = listOf(
            BitchatMessage(sender = "Alice", timestamp = java.util.Date(1000), content = protected.path, type = BitchatMessageType.File),
            BitchatMessage(sender = "Alice", timestamp = java.util.Date(1000), content = media.path)
        )
        FileUtils.deleteMessageFiles(context, messages, emptySet())
        assertTrue(protected.exists())
        assertTrue(media.exists())
    }
    @Test fun `retention removes old orphans but preserves referenced and fresh media`() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.filesDir, "images/outgoing").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val old = now - 48L * 60 * 60 * 1000
        val abandoned = File(dir, "abandoned.jpg").apply { writeText("old preparation"); setLastModified(old) }
        val retained = File(dir, "retained.jpg").apply { writeText("history reference"); setLastModified(old) }
        val fresh = File(dir, "fresh.jpg").apply { writeText("in flight") }
        val identity = File(context.filesDir, "qa-identity").apply { writeText("outside media roots"); setLastModified(old) }
        FileUtils.pruneUnreferencedMedia(context, setOf(retained.path), now)
        assertFalse(abandoned.exists())
        assertTrue(retained.exists())
        assertTrue(fresh.exists())
        assertTrue(identity.exists())
    }

}
