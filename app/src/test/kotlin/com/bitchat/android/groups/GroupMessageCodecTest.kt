package com.bitchat.android.groups

import org.junit.Assert.*
import org.junit.Test

class GroupMessageCodecTest {
    private val group = GroupInvite.create("Friends")

    @Test fun `text and binary attachments round trip without exposing plaintext`() {
        listOf(false, true).forEach { file ->
            val body = if (file) ByteArray(70_000) { (it % 256).toByte() } else "hello friends नमस्ते".toByteArray()
            val encoded = GroupMessageCodec.encrypt(group.id, group.keyBytes(), body, file)
            val decoded = GroupMessageCodec.decrypt(encoded, group.keyBytes())!!
            assertEquals(group.id, decoded.channel)
            assertEquals(file, decoded.isFile)
            assertArrayEquals(body, decoded.body)
            if (!file) assertFalse(String(encoded).contains("hello friends"))
        }
    }

    @Test fun `missing key wrong key truncated and altered packets fail closed`() {
        val encoded = GroupMessageCodec.encrypt(group.id, group.keyBytes(), "secret".toByteArray())
        assertNull(GroupMessageCodec.decrypt(encoded, ByteArray(32)))
        assertNull(GroupMessageCodec.decrypt(encoded, ByteArray(16)))
        listOf(0, 15, encoded.size - 1).forEach { length ->
            assertNull(GroupMessageCodec.decrypt(encoded.copyOf(length), group.keyBytes()))
        }
        val tampered = encoded.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertNull(GroupMessageCodec.decrypt(tampered, group.keyBytes()))
        val otherGroup = GroupInvite.create("Another")
        val changedHeader = encoded.clone().also {
            otherGroup.id.toByteArray().copyInto(it, "OUTPOST_GROUP_V1\u0000".length)
        }
        assertNull(GroupMessageCodec.decrypt(changedHeader, group.keyBytes()))
    }

    @Test fun `every send uses a fresh nonce including identical content`() {
        val first = GroupMessageCodec.encrypt(group.id, group.keyBytes(), "hello".toByteArray())
        val second = GroupMessageCodec.encrypt(group.id, group.keyBytes(), "hello".toByteArray())
        assertFalse(first.contentEquals(second))
    }

    @Test fun `ordinary BitChat messages are not mistaken for groups`() {
        assertFalse(GroupMessageCodec.isGroupPayload("hello".toByteArray()))
        assertNull(GroupMessageCodec.channel("OUTPOST_GROUP_V1".toByteArray()))
    }
}
