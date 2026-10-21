package com.bitchat.android.noise

import com.bitchat.android.mesh.FragmentManager
import com.bitchat.android.noise.southernstorm.protocol.Noise
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Two independent real Noise peers; only radio delivery is replaced with serialized datagrams. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NoisePeerIntegrationTest {
    private class Peer(val id: String) {
        val publicKey: ByteArray
        val sessions: NoiseSessionManager
        init {
            val dh = Noise.createDH("25519")
            dh.generateKeyPair()
            val privateKey = ByteArray(32)
            publicKey = ByteArray(32)
            dh.getPrivateKey(privateKey, 0)
            dh.getPublicKey(publicKey, 0)
            dh.destroy()
            sessions = NoiseSessionManager(privateKey, publicKey, id)
        }
    }
    private val alice = Peer("1111222233334444")
    private val bob = Peer("aaaabbbbccccdddd")

    @After fun close() { alice.sessions.shutdown(); bob.sessions.shutdown() }

    private fun connect() {
        val first = requireNotNull(alice.sessions.initiateHandshake(bob.id))
        val second = requireNotNull(bob.sessions.processHandshakeMessage(alice.id, first))
        val third = requireNotNull(alice.sessions.processHandshakeMessage(bob.id, second))
        assertNull(bob.sessions.processHandshakeMessage(alice.id, third))
        assertTrue(alice.sessions.hasEstablishedSession(bob.id))
        assertTrue(bob.sessions.hasEstablishedSession(alice.id))
        assertArrayEquals(bob.publicKey, alice.sessions.getRemoteStaticKey(bob.id))
        assertArrayEquals(alice.publicKey, bob.sessions.getRemoteStaticKey(alice.id))
        assertArrayEquals(alice.sessions.getHandshakeHash(bob.id), bob.sessions.getHandshakeHash(alice.id))
    }

    private fun rejects(block: () -> Unit) {
        assertNotNull("Expected an unauthenticated, replayed or stale message to be rejected", runCatching(block).exceptionOrNull())
    }

    @Test fun `independent peers handshake and exchange unicode in both directions`() {
        connect()
        for ((from, to) in listOf(alice to bob, bob to alice)) {
            val text = "Hello नमस्ते مرحبا 👋".toByteArray()
            val encrypted = from.sessions.encrypt(text, to.id)
            assertFalse(encrypted.contentEquals(text))
            assertArrayEquals(text, to.sessions.decrypt(encrypted, from.id))
        }
    }

    @Test fun `simultaneous initiation converges to a shared session`() {
        val a1 = requireNotNull(alice.sessions.initiateHandshake(bob.id))
        val b1 = requireNotNull(bob.sessions.initiateHandshake(alice.id))
        assertNull(alice.sessions.processHandshakeMessage(bob.id, b1))
        val b2 = requireNotNull(bob.sessions.processHandshakeMessage(alice.id, a1))
        val a3 = requireNotNull(alice.sessions.processHandshakeMessage(bob.id, b2))
        bob.sessions.processHandshakeMessage(alice.id, a3)
        val text = "collision recovered".toByteArray()
        assertArrayEquals(text, bob.sessions.decrypt(alice.sessions.encrypt(text, bob.id), alice.id))
    }

    @Test fun `replay stays rejected after the next message and byte boundary`() {
        connect()
        val packets = (0..18).map { alice.sessions.encrypt("message $it".toByteArray(), bob.id) }
        packets.forEachIndexed { index, bytes ->
            assertArrayEquals("message $index".toByteArray(), bob.sessions.decrypt(bytes, alice.id))
        }
        packets.forEach { bytes -> rejects { bob.sessions.decrypt(bytes, alice.id) } }
    }

    @Test fun `out of order packets accepted once and duplicates rejected`() {
        connect()
        val packets = (0..15).map { alice.sessions.encrypt(byteArrayOf(it.toByte()), bob.id) }
        for (index in listOf(8, 0, 7, 2, 15, 1, 9, 14, 3, 10, 4, 13, 5, 12, 6, 11)) {
            assertArrayEquals(byteArrayOf(index.toByte()), bob.sessions.decrypt(packets[index], alice.id))
        }
        packets.forEach { bytes -> rejects { bob.sessions.decrypt(bytes, alice.id) } }
    }

    @Test fun `nonce jump beyond replay window cannot make old packets valid`() {
        connect()
        val first = alice.sessions.encrypt("old".toByteArray(), bob.id)
        repeat(1030) { alice.sessions.encrypt(byteArrayOf(1), bob.id) }
        val latest = alice.sessions.encrypt("latest".toByteArray(), bob.id)
        assertArrayEquals("latest".toByteArray(), bob.sessions.decrypt(latest, alice.id))
        rejects { bob.sessions.decrypt(first, alice.id) }
    }

    @Test fun `forged high nonce and tampering do not poison valid traffic`() {
        connect()
        val valid = alice.sessions.encrypt("authentic".toByteArray(), bob.id)
        val forged = valid.copyOf().also { repeat(4) { index -> it[index] = 0xff.toByte() } }
        rejects { bob.sessions.decrypt(forged, alice.id) }
        val corrupted = valid.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        rejects { bob.sessions.decrypt(corrupted, alice.id) }
        assertArrayEquals("authentic".toByteArray(), bob.sessions.decrypt(valid, alice.id))
        rejects { bob.sessions.decrypt(valid, alice.id) }
    }

    @Test fun `reconnect replaces transport keys and rejects old session ciphertext`() {
        connect()
        val old = alice.sessions.encrypt("old session".toByteArray(), bob.id)
        alice.sessions.removeSession(bob.id)
        bob.sessions.removeSession(alice.id)
        connect()
        rejects { bob.sessions.decrypt(old, alice.id) }
        val fresh = alice.sessions.encrypt("new session".toByteArray(), bob.id)
        assertArrayEquals("new session".toByteArray(), bob.sessions.decrypt(fresh, alice.id))
    }

    @Test fun `concurrent sends use unique nonces and decrypt out of order`() {
        connect()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll((0..63).map { index -> Callable {
                index to alice.sessions.encrypt("parallel $index".toByteArray(), bob.id)
            } }).map { it.get() }
            assertEquals(results.size, results.map { it.second.take(4) }.toSet().size)
            results.reversed().forEach { (index, bytes) ->
                assertArrayEquals("parallel $index".toByteArray(), bob.sessions.decrypt(bytes, alice.id))
            }
        } finally { pool.shutdownNow() }
    }

    @Test fun `encrypted attachment crosses serialized fragmented transport with loss and reordering`() {
        connect()
        val content = ByteArray(24_000) { (it * 31).toByte() }
        val sender = FragmentManager()
        val receiver = FragmentManager()
        val packet = BitchatPacket(version = 1u, type = MessageType.NOISE_ENCRYPTED.value,
            senderID = alice.id.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            recipientID = bob.id.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            timestamp = System.currentTimeMillis().toULong(), payload = alice.sessions.encrypt(content, bob.id), ttl = 7u)
        try {
            val frames = sender.createFragments(packet).map { requireNotNull(it.toBinaryData()) }
            assertTrue(frames.size > 2)
            assertTrue(frames.all { it.size <= 512 })
            // Hold one datagram back: incomplete transfers must never publish a partial file.
            frames.drop(1).reversed().forEach { frame ->
                assertNull(receiver.handleFragment(requireNotNull(BitchatPacket.fromBinaryData(frame))))
            }
            assertNull(receiver.handleFragment(requireNotNull(BitchatPacket.fromBinaryData(frames.last()))))
            val assembled = requireNotNull(receiver.handleFragment(requireNotNull(BitchatPacket.fromBinaryData(frames.first()))))
            assertArrayEquals(content, bob.sessions.decrypt(assembled.payload, alice.id))
        } finally { sender.shutdown(); receiver.shutdown() }
    }
}
