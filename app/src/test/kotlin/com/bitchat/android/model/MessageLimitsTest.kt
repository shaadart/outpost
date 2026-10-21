package com.bitchat.android.model

import org.junit.Assert.*
import org.junit.Test

class MessageLimitsTest {
    @Test fun `private validation agrees with wire encoding including multibyte text`() {
        for (content in listOf("x".repeat(255), "x".repeat(256), "न".repeat(85), "न".repeat(86), "👋".repeat(63), "👋".repeat(64))) {
            assertEquals(PrivateMessagePacket("id", content).encode() != null, MessageLimits.validationError(content, true) == null)
        }
    }
    @Test fun `group limit counts encoded bytes`() {
        assertNull(MessageLimits.validationError("x".repeat(8192), false))
        assertNotNull(MessageLimits.validationError("x".repeat(8193), false))
        assertNotNull(MessageLimits.validationError("👋".repeat(2049), false))
    }
}
