package com.bitchat.android.services

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.BitchatMessageType
import com.bitchat.android.model.DeliveryStatus
import com.google.gson.Gson
import java.util.Date

/** Encrypted local history. Called on IO workers; versions prevent stale saves undoing deletion. */
class ChatHistoryStore internal constructor(private val prefs: android.content.SharedPreferences) {
    constructor(context: Context) : this(EncryptedSharedPreferences.create(
        context, "outpost_chat_history",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    ))
    private val gson = Gson()
    private var savedVersion = -1L

    @androidx.annotation.Keep
    data class Status(val kind: String, val peer: String? = null, val at: Long? = null,
                      val reason: String? = null, val reached: Int? = null, val total: Int? = null) {
        fun restore(): DeliveryStatus? = when (kind) {
            "sending" -> DeliveryStatus.Sending
            "sent" -> DeliveryStatus.Sent
            "delivered" -> DeliveryStatus.Delivered(peer.orEmpty(), Date(at ?: 0L))
            "read" -> DeliveryStatus.Read(peer.orEmpty(), Date(at ?: 0L))
            "failed" -> DeliveryStatus.Failed(reason ?: "Send failed")
            // A radio transfer cannot continue across process death. Do not restore a progress spinner.
            "partial" -> DeliveryStatus.Failed("Transfer interrupted. Please attach the file again.")
            else -> null
        }

        companion object {
            fun from(status: DeliveryStatus?): Status? = when (status) {
                null -> null
                DeliveryStatus.Sending -> Status("sending")
                DeliveryStatus.Sent -> Status("sent")
                is DeliveryStatus.Delivered -> Status("delivered", status.to, status.at.time)
                is DeliveryStatus.Read -> Status("read", status.by, status.at.time)
                is DeliveryStatus.Failed -> Status("failed", reason = status.reason)
                is DeliveryStatus.PartiallyDelivered -> Status("partial", reached = status.reached, total = status.total)
            }
        }
    }

    @androidx.annotation.Keep
    data class Entry(
        val conversation: String?, val privateChat: Boolean,
        val id: String, val sender: String, val content: String, val type: String,
        val timestamp: Long, val senderPeer: String?, val encrypted: Boolean,
        val status: Status? = null
    ) {
        fun message() = BitchatMessage(id = id, sender = sender, content = content,
            type = BitchatMessageType.valueOf(type), timestamp = Date(timestamp),
            senderPeerID = senderPeer, isPrivate = privateChat,
            channel = if (privateChat) null else conversation, isEncrypted = encrypted,
            deliveryStatus = status?.restore())
    }

    fun load(): List<Entry> {
        val json = prefs.getString("messages", null) ?: return emptyList()
        return gson.fromJson(json, Array<Entry>::class.java).toList()
    }

    @Synchronized
    fun save(version: Long, entries: List<Entry>) {
        if (version <= savedVersion) return
        check(prefs.edit().putString("messages", gson.toJson(entries)).commit()) { "Could not save chat history" }
        savedVersion = version
    }
}
