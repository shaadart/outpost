package com.bitchat.android.groups

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** An invitation is a bearer credential: anyone holding it can read and send group messages. */
data class GroupInvite(val id: String, val name: String, val key: String) {
    fun encode(): String = "outpost://group?v=1&id=${URLEncoder.encode(id, "UTF-8")}&name=${URLEncoder.encode(name, "UTF-8")}#key=$key"

    fun keyBytes(): ByteArray = Base64.getUrlDecoder().decode(key)

    companion object {
        private val idPattern = Regex("#group_[a-f0-9]{32}")

        fun isGroup(id: String): Boolean = idPattern.matches(id)

        fun create(name: String): GroupInvite {
            require(validName(name))
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return GroupInvite(
                "#group_${UUID.randomUUID().toString().replace("-", "")}",
                name,
                Base64.getUrlEncoder().withoutPadding().encodeToString(key)
            )
        }

        fun validName(name: String): Boolean = name.isNotBlank() && name.length <= 50 &&
            name == name.trim() && name.none { it.isISOControl() }

        fun parse(value: String): GroupInvite? = runCatching {
            if (value.length > 1024) return null
            val uri = URI(value.trim())
            if (uri.scheme != "outpost" || uri.host != "group" ||
                uri.rawUserInfo != null || uri.port != -1 || !uri.path.isNullOrEmpty()) return null
            val pairs = uri.rawQuery?.split('&')?.map {
                val pair = it.split('=', limit = 2)
                if (pair.size != 2) return null
                pair[0] to URLDecoder.decode(pair[1], "UTF-8")
            } ?: return null
            if (pairs.size != 3 || pairs.map { it.first }.toSet() != setOf("v", "id", "name")) return null
            val fields = pairs.toMap()
            val id = fields["id"] ?: return null
            val name = fields["name"] ?: return null
            val fragment = uri.rawFragment ?: return null
            if (!fragment.startsWith("key=")) return null
            val key = fragment.removePrefix("key=")
            if (fields["v"] != "1" || !isGroup(id) || !validName(name) ||
                !Regex("[A-Za-z0-9_-]{43}").matches(key)) return null
            val invite = GroupInvite(id, name, key)
            if (invite.keyBytes().size != 32 ||
                Base64.getUrlEncoder().withoutPadding().encodeToString(invite.keyBytes()) != key) return null
            invite
        }.getOrNull()
    }
}
