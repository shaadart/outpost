package com.bitchat.android.services

import android.content.Context
import android.os.Build
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], manifest = Config.NONE)
class ChatHistoryStoreTest {
    private fun entry(content: String = "secret") = ChatHistoryStore.Entry(
        "peer", true, "message-id", "Alice", content, "Message", 1234L, "peer", true
    )

    @Test fun `history round trips through a new store instance`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("history-roundtrip", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        ChatHistoryStore(prefs).save(1, listOf(entry()))
        val loaded = ChatHistoryStore(prefs).load().single()
        assertEquals(entry(), loaded)
        assertEquals("secret", loaded.message().content)
        assertTrue(loaded.message().isPrivate)
        assertEquals(null, loaded.message().channel)
    }

    @Test fun `delayed save cannot restore history after deletion`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("history-deletion", Context.MODE_PRIVATE)
        val store = ChatHistoryStore(prefs)
        store.save(1, listOf(entry()))
        store.save(3, emptyList())
        store.save(2, listOf(entry("stale secret")))
        assertTrue(ChatHistoryStore(prefs).load().isEmpty())
    }
    @Test fun `delivery receipts and queued text survive persisted history`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("history-status", Context.MODE_PRIVATE)
        val statuses = listOf(com.bitchat.android.model.DeliveryStatus.Sending,
            com.bitchat.android.model.DeliveryStatus.Sent,
            com.bitchat.android.model.DeliveryStatus.Delivered("Bob", java.util.Date(42)),
            com.bitchat.android.model.DeliveryStatus.Read("Bob", java.util.Date(43)),
            com.bitchat.android.model.DeliveryStatus.Failed("offline"))
        val entries = statuses.mapIndexed { index, status -> entry().copy(id = "status-$index", status = ChatHistoryStore.Status.from(status)) }
        ChatHistoryStore(prefs).save(1, entries)
        assertEquals(statuses, ChatHistoryStore(prefs).load().map { it.message().deliveryStatus })
    }

    @Test fun `legacy history without status still loads`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("history-legacy", Context.MODE_PRIVATE)
        prefs.edit().putString("messages", """[{"conversation":"peer","privateChat":true,"id":"old","sender":"Alice","content":"old text","type":"Message","timestamp":1234,"encrypted":false}]""").commit()
        val restored = ChatHistoryStore(prefs).load().single().message()
        assertEquals("old text", restored.content)
        assertNull(restored.deliveryStatus)
    }

    @Test fun `interrupted file progress restores as failed instead of indefinite sending`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("history-interrupted", Context.MODE_PRIVATE)
        ChatHistoryStore(prefs).save(1, listOf(entry().copy(type = "File", status =
            ChatHistoryStore.Status.from(com.bitchat.android.model.DeliveryStatus.PartiallyDelivered(40, 100)))))
        assertTrue(ChatHistoryStore(prefs).load().single().message().deliveryStatus is com.bitchat.android.model.DeliveryStatus.Failed)
    }

}
