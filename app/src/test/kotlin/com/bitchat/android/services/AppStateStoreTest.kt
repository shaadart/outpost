package com.bitchat.android.services

import com.bitchat.android.model.BitchatMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class AppStateStoreTest {
    @Before
    fun setUp() {
        AppStateStore.clear()
    }

    @After
    fun tearDown() {
        AppStateStore.clear()
    }

    @Test
    fun `public timeline collapses request sync replay even when android message ids differ`() {
        val timestamp = Date(1_700_000_000_000L)
        val originalDelivery = BitchatMessage(
            id = "random-id-from-first-delivery",
            sender = "alice",
            content = "hello from sync",
            timestamp = timestamp,
            senderPeerID = "1122334455667788"
        )
        val requestSyncReplay = originalDelivery.copy(id = "different-random-id-from-replay")

        AppStateStore.addPublicMessage(originalDelivery)
        AppStateStore.addPublicMessage(requestSyncReplay)

        assertEquals(listOf(originalDelivery), AppStateStore.publicMessages.value)
    }

    @Test
    fun `public timeline still keeps same content sent at different packet timestamps`() {
        val first = BitchatMessage(
            id = "first-packet-id",
            sender = "alice",
            content = "same text",
            timestamp = Date(1_700_000_000_000L),
            senderPeerID = "1122334455667788"
        )
        val second = first.copy(
            id = "second-packet-id",
            timestamp = Date(first.timestamp.time + 1_000L)
        )

        AppStateStore.addPublicMessage(first)
        AppStateStore.addPublicMessage(second)

        assertEquals(listOf(first, second), AppStateStore.publicMessages.value)
    }

    @Test
    fun `peer list merges transport updates instead of overwriting`() {
        AppStateStore.setTransportPeers("WIFI", listOf("wifi-peer"))
        AppStateStore.setTransportPeers("BLE", emptyList())

        assertEquals(listOf("wifi-peer"), AppStateStore.peers.value)

        AppStateStore.setTransportPeers("BLE", listOf("ble-peer"))

        assertEquals(listOf("wifi-peer", "ble-peer"), AppStateStore.peers.value)
    }

    @Test
    fun `direct peers union across transports`() {
        AppStateStore.setTransportDirectPeers("BLE", listOf("ble-1", "shared"))
        AppStateStore.setTransportDirectPeers("WIFI", listOf("wifi-1", "shared"))

        assertEquals(
            setOf("ble-1", "wifi-1", "shared"),
            AppStateStore.getDirectPeers()
        )
    }

    @Test
    fun `clearing one transport keeps the other transport direct peers`() {
        AppStateStore.setTransportDirectPeers("BLE", listOf("ble-1"))
        AppStateStore.setTransportDirectPeers("WIFI", listOf("wifi-1"))

        AppStateStore.clearTransportDirectPeers("WIFI")

        assertEquals(setOf("ble-1"), AppStateStore.getDirectPeers())
    }

    @Test
    fun `latest direct peer set replaces previous set for same transport`() {
        AppStateStore.setTransportDirectPeers("WIFI", listOf("wifi-1", "wifi-2"))
        AppStateStore.setTransportDirectPeers("WIFI", listOf("wifi-3"))

        assertEquals(setOf("wifi-3"), AppStateStore.getDirectPeers())
    }

    @Test
    fun `clearing private chat removes background snapshot and rejects replay without touching other chats`() {
        val old = BitchatMessage(id = "old", sender = "Alice", content = "secret", isPrivate = true,
            senderPeerID = "alice", timestamp = Date(100))
        val other = old.copy(id = "other", senderPeerID = "bob")
        AppStateStore.addPrivateMessage("alice", old)
        AppStateStore.addPrivateMessage("bob", other)
        assertEquals(listOf(old), AppStateStore.clearConversation(peer = "alice"))
        AppStateStore.addPrivateMessage("alice", old.copy(id = "replayed"))
        assertEquals(null, AppStateStore.privateMessages.value["alice"])
        assertEquals(listOf(other), AppStateStore.privateMessages.value["bob"])
        val fresh = old.copy(id = "new", timestamp = Date(System.currentTimeMillis() + 1000))
        AppStateStore.addPrivateMessage("alice", fresh)
        assertEquals(listOf(fresh), AppStateStore.privateMessages.value["alice"])
    }

    @Test
    fun `clearing a group prevents sync restoring old messages and keeps unrelated groups`() {
        val old = BitchatMessage(id = "old", sender = "Alice", content = "secret", timestamp = Date(100), channel = "#group")
        AppStateStore.addChannelMessage("#group", old)
        AppStateStore.addChannelMessage("#other", old.copy(id = "other", channel = "#other"))
        AppStateStore.clearConversation(channel = "#group")
        AppStateStore.addChannelMessage("#group", old.copy(id = "replay"))
        assertEquals(null, AppStateStore.channelMessages.value["#group"])
        assertEquals(1, AppStateStore.channelMessages.value["#other"]?.size)
    }

    @Test
    fun `clearing public timeline does not clear private chats`() {
        val old = BitchatMessage(id = "old", sender = "Alice", content = "secret", timestamp = Date(100))
        AppStateStore.addPublicMessage(old)
        AppStateStore.addPrivateMessage("alice", old.copy(id = "private", isPrivate = true))
        AppStateStore.clearConversation()
        AppStateStore.addPublicMessage(old.copy(id = "replay"))
        assertEquals(emptyList<BitchatMessage>(), AppStateStore.publicMessages.value)
        assertEquals(1, AppStateStore.privateMessages.value["alice"]?.size)
    }

    @Test
    fun `chat history has a bounded memory footprint`() {
        repeat(510) { index -> AppStateStore.addPrivateMessage("alice", BitchatMessage(
            id = "id-$index", sender = "Alice", content = "message $index", timestamp = Date(index.toLong())
        )) }
        assertEquals(500, AppStateStore.privateMessages.value["alice"]?.size)
        assertEquals("id-10", AppStateStore.privateMessages.value["alice"]?.first()?.id)
    }
}
