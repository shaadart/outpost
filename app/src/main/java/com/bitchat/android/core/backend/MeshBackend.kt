package com.bitchat.android.core.backend

import com.bitchat.android.core.backend.model.Conversation
import com.bitchat.android.core.backend.model.Message
import com.bitchat.android.core.backend.model.Peer
import kotlinx.coroutines.flow.StateFlow

interface MeshBackend {
    val messages: StateFlow<List<Message>>
    val peers: StateFlow<List<Peer>>
    val conversations: StateFlow<List<Conversation>>
    
    val myPeerId: String

    fun sendMessage(content: String, targetPeerId: String? = null, targetChannel: String? = null, mentions: List<String> = emptyList())
    fun sendVoiceNote(filePath: String, targetPeerId: String? = null, targetChannel: String? = null)
    fun sendFileNote(filePath: String, targetPeerId: String? = null, targetChannel: String? = null)
    fun sendImageNote(filePath: String, targetPeerId: String? = null, targetChannel: String? = null)
    
    fun joinChannel(channel: String, password: String? = null): Boolean
    fun leaveChannel(channel: String)
    fun startPrivateChat(peerId: String)
}
