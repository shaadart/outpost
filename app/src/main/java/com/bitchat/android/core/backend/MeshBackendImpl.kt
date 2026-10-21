package com.bitchat.android.core.backend

import com.bitchat.android.core.backend.model.*
import com.bitchat.android.mesh.MeshService
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.BitchatMessageType
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.ui.ChannelManager
import com.bitchat.android.ui.ChatState
import com.bitchat.android.ui.MediaSendingManager
import com.bitchat.android.ui.PrivateChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MeshBackendImpl(
    private val meshService: MeshService,
    private val chatState: ChatState,
    private val channelManager: ChannelManager,
    private val privateChatManager: PrivateChatManager,
    private val mediaSendingManager: MediaSendingManager,
    private val scope: CoroutineScope
) : MeshBackend {

    override val myPeerId: String
        get() = meshService.myPeerID

    override val messages: StateFlow<List<Message>> = chatState.messages.map { list ->
        list.map { it.toBackendModel() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val peers: StateFlow<List<Peer>> = combine(
        chatState.connectedPeers,
        chatState.peerNicknames,
        chatState.peerRSSI
    ) { connected, nicknames, rssi ->
        connected.map { id ->
            Peer(
                id = id,
                nickname = nicknames[id] ?: id,
                isConnected = true,
                rssi = rssi[id],
                lastSeenTimestamp = System.currentTimeMillis() // Simplification
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val conversations: StateFlow<List<Conversation>> = combine(
        chatState.privateChats,
        chatState.channelMessages,
        chatState.unreadPrivateMessages,
        chatState.unreadChannelMessages
    ) { privateChats, channelMessages, unreadPrivate, unreadChannels ->
        val list = mutableListOf<Conversation>()
        privateChats.forEach { (peerId, msgs) ->
            list.add(
                Conversation(
                    id = peerId,
                    isPrivate = true,
                    peerId = peerId,
                    unreadCount = if (unreadPrivate.contains(peerId)) 1 else 0,
                    lastMessage = msgs.lastOrNull()?.toBackendModel()
                )
            )
        }
        channelMessages.forEach { (channelName, msgs) ->
            list.add(
                Conversation(
                    id = channelName,
                    isPrivate = false,
                    channelName = channelName,
                    unreadCount = unreadChannels[channelName] ?: 0,
                    lastMessage = msgs.lastOrNull()?.toBackendModel()
                )
            )
        }
        list
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun sendMessage(
        content: String,
        targetPeerId: String?,
        targetChannel: String?,
        mentions: List<String>
    ) {
        if (targetPeerId != null) {
            val recipientNickname = chatState.peerNicknames.value[targetPeerId] ?: targetPeerId
            privateChatManager.sendPrivateMessage(
                content,
                targetPeerId,
                recipientNickname,
                chatState.getNicknameValue(),
                myPeerId
            ) { messageContent, peerID, recipientNicknameParam, messageId ->
                meshService.sendPrivateMessage(messageContent, peerID, recipientNicknameParam, messageId)
            }
        } else if (targetChannel != null) {
            meshService.sendMessage(content, mentions, targetChannel)
        } else {
            meshService.sendMessage(content, mentions, null)
        }
    }

    override fun sendVoiceNote(filePath: String, targetPeerId: String?, targetChannel: String?) {
        mediaSendingManager.sendVoiceNote(targetPeerId, targetChannel, filePath)
    }

    override fun sendFileNote(filePath: String, targetPeerId: String?, targetChannel: String?) {
        mediaSendingManager.sendFileNote(targetPeerId, targetChannel, filePath)
    }

    override fun sendImageNote(filePath: String, targetPeerId: String?, targetChannel: String?) {
        mediaSendingManager.sendImageNote(targetPeerId, targetChannel, filePath)
    }

    override fun joinChannel(channel: String, password: String?): Boolean {
        return channelManager.joinChannel(channel, password, myPeerId)
    }

    override fun leaveChannel(channel: String) {
        channelManager.leaveChannel(channel)
        meshService.sendMessage("left $channel", emptyList(), null)
    }

    override fun startPrivateChat(peerId: String) {
        privateChatManager.startPrivateChat(peerId, meshService)
    }

    private fun BitchatMessage.toBackendModel(): Message {
        val mappedType = when (type) {
            BitchatMessageType.Message -> MessageType.TEXT
            BitchatMessageType.Audio -> MessageType.AUDIO
            BitchatMessageType.Image -> MessageType.IMAGE
            BitchatMessageType.File -> MessageType.FILE
        }

        val mappedState = when (deliveryStatus) {
            is DeliveryStatus.Sending -> DeliveryState.SENDING
            is DeliveryStatus.Sent -> DeliveryState.SENT
            is DeliveryStatus.Delivered -> DeliveryState.DELIVERED
            is DeliveryStatus.Read -> DeliveryState.READ
            is DeliveryStatus.Failed -> DeliveryState.FAILED
            is DeliveryStatus.PartiallyDelivered -> DeliveryState.PARTIALLY_DELIVERED
            null -> DeliveryState.SENT
        }

        return Message(
            id = id,
            senderId = senderPeerID ?: sender,
            senderNickname = sender,
            content = content,
            type = mappedType,
            timestamp = timestamp.time,
            isPrivate = isPrivate,
            channel = channel,
            deliveryState = mappedState,
            encryptedContent = encryptedContent,
            isEncrypted = isEncrypted,
            isVerified = isVerified
        )
    }
}
