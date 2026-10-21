package com.bitchat.android.mesh

import android.os.Build
import com.bitchat.android.model.IdentityAnnouncement
import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import com.bitchat.android.services.meshgraph.MeshGraphService
import com.bitchat.android.util.AppConstants
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], manifest = Config.NONE)
class MessageHandlerTest {
    private lateinit var handler: MessageHandler
    private lateinit var delegate: MessageHandlerDelegate

    private val myPeerID = "1111222233334444"
    private val peerID = "aaaabbbbccccdddd"
    private val nickname = "peer"
    private val noiseKey = ByteArray(32) { 0x0B }
    private val signingKey = ByteArray(32) { 0x0A }
    private val signature = ByteArray(64) { 1 }
    private val announceClockSkewToleranceMs = 10 * 60 * 1000L

    @Before
    fun setup() {
        MeshGraphService.resetForTesting()
        com.bitchat.android.services.AppStateStore.clear()
        handler = MessageHandler(myPeerID, RuntimeEnvironment.getApplication())
        delegate = mock()
        handler.delegate = delegate

        whenever(delegate.getPeerInfo(peerID)).thenReturn(null)
        whenever(delegate.verifyEd25519Signature(any(), any(), any())).thenReturn(true)
        whenever(delegate.updatePeerInfo(any(), any(), any(), any(), any())).thenReturn(true)
    }

    @After
    fun tearDown() {
        MeshGraphService.resetForTesting()
    }

    @Test
    fun `handleAnnounce accepts announce within clock skew tolerance for identity binding`() = runBlocking {
        val packet = announcePacket(ageMs = AppConstants.Mesh.STALE_PEER_TIMEOUT_MS + 1_000)

        val result = handler.handleAnnounce(RoutedPacket(packet, peerID, "direct-link"))

        assertTrue("Announce within clock skew tolerance should still store peer identity", result)
        verify(delegate).updatePeerInfo(eq(peerID), eq(nickname), any(), any(), eq(true))
        verify(delegate).updatePeerIDBinding(eq(peerID), eq(nickname), any(), isNull())
    }

    @Test
    fun `handleAnnounce accepts future announce within clock skew tolerance`() = runBlocking {
        val packet = announcePacket(ageMs = -(AppConstants.Mesh.STALE_PEER_TIMEOUT_MS + 1_000))

        val result = handler.handleAnnounce(RoutedPacket(packet, peerID, "direct-link"))

        assertTrue("Future announce within clock skew tolerance should still store peer identity", result)
        verify(delegate).updatePeerInfo(eq(peerID), eq(nickname), any(), any(), eq(true))
        Unit
    }

    @Test
    fun `handleAnnounce rejects announce older than clock skew tolerance`() = runBlocking {
        val packet = announcePacket(ageMs = announceClockSkewToleranceMs + 1_000)

        val result = handler.handleAnnounce(RoutedPacket(packet, peerID, "relay-link"))

        assertFalse("Announce older than clock skew tolerance should not store peer identity", result)
        verify(delegate, never()).updatePeerInfo(any(), any(), any(), any(), any())
        verify(delegate, never()).updatePeerIDBinding(any(), any(), any(), any())
    }


    @Test
    fun `encrypted group text is routed only to its group`() = runBlocking {
        val invite = com.bitchat.android.groups.GroupInvite.create("Friends")
        handler = MessageHandler(myPeerID, RuntimeEnvironment.getApplication()) { invite.takeIf { group -> group.id == it } }
        handler.delegate = delegate
        whenever(delegate.getPeerInfo(peerID)).thenReturn(PeerInfo(peerID, nickname, true, true,
            noiseKey, signingKey, true, System.currentTimeMillis()))
        whenever(delegate.getBroadcastRecipient()).thenReturn(SpecialRecipients.BROADCAST)
        whenever(delegate.getPeerNickname(peerID)).thenReturn(nickname)
        val payload = com.bitchat.android.groups.GroupMessageCodec.encrypt(invite.id, invite.keyBytes(), "group secret".toByteArray())
        val packet = BitchatPacket(version = 2u, type = MessageType.MESSAGE.value,
            senderID = peerID.hexToBytes(), recipientID = SpecialRecipients.BROADCAST,
            timestamp = System.currentTimeMillis().toULong(), payload = payload, signature = signature, ttl = AppConstants.MESSAGE_TTL_HOPS)
        handler.handleMessage(RoutedPacket(packet, peerID, "direct-link"))
        val captured = org.mockito.kotlin.argumentCaptor<com.bitchat.android.model.BitchatMessage>()
        verify(delegate).onMessageReceived(captured.capture())
        org.junit.Assert.assertEquals(invite.id, captured.firstValue.channel)
        org.junit.Assert.assertEquals("group secret", captured.firstValue.content)
        assertTrue(captured.firstValue.isEncrypted)
    }

    @Test
    fun `unknown encrypted group never falls back to public text`() = runBlocking {
        val invite = com.bitchat.android.groups.GroupInvite.create("Unknown")
        handler = MessageHandler(myPeerID, RuntimeEnvironment.getApplication()) { null }
        handler.delegate = delegate
        whenever(delegate.getPeerInfo(peerID)).thenReturn(PeerInfo(peerID, nickname, true, true,
            noiseKey, signingKey, true, System.currentTimeMillis()))
        whenever(delegate.getBroadcastRecipient()).thenReturn(SpecialRecipients.BROADCAST)
        val payload = com.bitchat.android.groups.GroupMessageCodec.encrypt(invite.id, invite.keyBytes(), "secret".toByteArray())
        val packet = BitchatPacket(version = 2u, type = MessageType.MESSAGE.value,
            senderID = peerID.hexToBytes(), recipientID = SpecialRecipients.BROADCAST,
            timestamp = System.currentTimeMillis().toULong(), payload = payload, signature = signature, ttl = AppConstants.MESSAGE_TTL_HOPS)
        handler.handleMessage(RoutedPacket(packet, peerID, "direct-link"))
        verify(delegate, never()).onMessageReceived(any())
    }


    @Test
    fun `repeated encrypted group attachment does not leave duplicate media`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val invite = com.bitchat.android.groups.GroupInvite.create("Files")
        handler = MessageHandler(myPeerID, context) { invite.takeIf { group -> group.id == it } }
        handler.delegate = delegate
        whenever(delegate.getPeerInfo(peerID)).thenReturn(PeerInfo(peerID, nickname, true, true,
            noiseKey, signingKey, true, System.currentTimeMillis()))
        whenever(delegate.getBroadcastRecipient()).thenReturn(SpecialRecipients.BROADCAST)
        org.mockito.kotlin.doAnswer { call ->
            val msg = call.getArgument<com.bitchat.android.model.BitchatMessage>(0)
            com.bitchat.android.services.AppStateStore.addChannelMessage(invite.id, msg)
            null
        }.whenever(delegate).onMessageReceived(any())
        val file = com.bitchat.android.model.BitchatFilePacket("a.txt", 6, "text/plain", "secret".toByteArray())
        val payload = com.bitchat.android.groups.GroupMessageCodec.encrypt(invite.id, invite.keyBytes(), file.encode()!!, true)
        val packet = BitchatPacket(version = 2u, type = MessageType.FILE_TRANSFER.value,
            senderID = peerID.hexToBytes(), recipientID = SpecialRecipients.BROADCAST,
            timestamp = System.currentTimeMillis().toULong(), payload = payload, signature = signature, ttl = AppConstants.MESSAGE_TTL_HOPS)
        val dir = java.io.File(context.filesDir, "files/incoming")
        val before = dir.listFiles()?.size ?: 0
        repeat(2) { handler.handleMessage(RoutedPacket(packet, peerID, "direct-link")) }
        verify(delegate, org.mockito.kotlin.times(1)).onMessageReceived(any())
        org.junit.Assert.assertEquals(before + 1, dir.listFiles()?.size)
        org.junit.Assert.assertEquals(1, com.bitchat.android.services.AppStateStore.channelMessages.value[invite.id]?.size)
    }

    private fun announcePacket(
        ageMs: Long,
        ttl: UByte = (AppConstants.MESSAGE_TTL_HOPS.toInt() - 1).toUByte()
    ): BitchatPacket {
        val announcement = IdentityAnnouncement(
            nickname = nickname,
            noisePublicKey = noiseKey,
            signingPublicKey = signingKey
        )
        return BitchatPacket(
            version = 1u,
            type = MessageType.ANNOUNCE.value,
            senderID = peerID.hexToBytes(),
            recipientID = SpecialRecipients.BROADCAST,
            timestamp = (System.currentTimeMillis() - ageMs).toULong(),
            payload = announcement.encode()!!,
            signature = signature,
            ttl = ttl
        )
    }

    private fun String.hexToBytes(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
