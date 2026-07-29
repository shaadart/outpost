package com.bitchat.android.core.backend.model

enum class MessageType {
    TEXT, AUDIO, IMAGE, FILE
}

enum class DeliveryState {
    SENDING, SENT, DELIVERED, READ, FAILED, PARTIALLY_DELIVERED
}

data class Peer(
    val id: String,
    val nickname: String,
    val isConnected: Boolean,
    val rssi: Int? = null,
    val lastSeenTimestamp: Long
)

data class Message(
    val id: String,
    val senderId: String,
    val senderNickname: String,
    val content: String,
    val type: MessageType,
    val timestamp: Long,
    val isPrivate: Boolean,
    val channel: String?,
    val deliveryState: DeliveryState,
    val encryptedContent: ByteArray? = null,
    val isEncrypted: Boolean = false
)

data class Conversation(
    val id: String,
    val isPrivate: Boolean,
    val peerId: String? = null,
    val channelName: String? = null,
    val unreadCount: Int = 0,
    val lastMessage: Message? = null
)
