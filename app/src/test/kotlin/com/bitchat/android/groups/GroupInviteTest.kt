package com.bitchat.android.groups

import org.junit.Assert.*
import org.junit.Test

class GroupInviteTest {
    @Test fun `invitation round trips unicode punctuation and reserved URL characters`() {
        val invite = GroupInvite.create("Friends & family: दिल्ली #1")
        assertEquals(invite, GroupInvite.parse(invite.encode()))
        assertEquals(32, invite.keyBytes().size)
        assertFalse(invite.encode().substringBefore('#').contains(invite.key))
    }

    @Test fun `group identifiers and keys are independently random`() {
        val first = GroupInvite.create("Team")
        val second = GroupInvite.create("Team")
        assertNotEquals(first.id, second.id)
        assertNotEquals(first.key, second.key)
    }

    @Test fun `malformed and ambiguous invitations are rejected`() {
        val invite = GroupInvite.create("Team")
        val url = invite.encode()
        val invalid = listOf("", "broadcast_invite:#x:Team", url.replace("v=1", "v=2"),
            url.replace("outpost:", "https:"), url.replace("group?", "group/path?"),
            url.replace("?v=1", "?v=1&v=1"), url.replace("&name=", "&unknown=1&name="),
            url.substringBefore('#'), url.replace(invite.key, "x"),
            url.replace("name=Team", "name=%0ATeam"), url.replace("name=Team", "name="),
            url.replace("group?", "user@group?"), url.replace("group?", "group:443?"),
            url.replace("%23group_", "%23other_"), url.replace("name=Team", "name=" + "x".repeat(51)),
            url + "&extra=1", "x".repeat(1025))
        invalid.forEach { assertNull(it, GroupInvite.parse(it)) }
    }
}
