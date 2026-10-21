package com.bitchat.android.services

import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Process-wide in-memory state store that survives Activity recreation.
 * The foreground Mesh service updates this store; UI subscribes/hydrates from it.
 */
object AppStateStore {
    private const val MAX_MESSAGES_PER_CHAT = 500
    private val persistenceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private var history: ChatHistoryStore? = null
    private var initializationStarted = false
    private var historyVersion = 0L
    private val _historyReady = MutableStateFlow(false)
    val historyReady: StateFlow<Boolean> = _historyReady.asStateFlow()
    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()
    private val deletedBefore = mutableMapOf<String, Long>()
    private var deletionPrefs: android.content.SharedPreferences? = null
    private var lastMediaCleanupAt = 0L

    /** Only hashed conversation IDs and deletion times are persisted, never message content. */
    fun initialize(context: android.content.Context) {
        synchronized(this) {
            if (initializationStarted) return
            initializationStarted = true
        }
        persistenceScope.launch {
            try {
                val prefs = context.getSharedPreferences("outpost_deletions", android.content.Context.MODE_PRIVATE)
                val cutoffs = prefs.all
                synchronized(this@AppStateStore) {
                    cutoffs.forEach { (id, time) -> if (time is Long) deletedBefore[id] = time }
                    deletionPrefs = prefs
                }
                val store = ChatHistoryStore(context.applicationContext)
                val entries = store.load()
                synchronized(this@AppStateStore) {
                    entries.forEach { entry ->
                        val msg = entry.message()
                        when {
                            entry.privateChat && entry.conversation != null -> addPrivateMessage(entry.conversation, msg)
                            entry.conversation != null -> addChannelMessage(entry.conversation, msg)
                            else -> addPublicMessage(msg)
                        }
                    }
                    history = store
                }
                pruneMediaIfDue(context, entries)
            } catch (e: Exception) {
                _historyError.value = "Could not open encrypted chat history. Restart Outpost or reset app data."
            } finally { _historyReady.value = history != null }
            for (ignored in saveRequests) {
                try {
                    val snapshot = synchronized(this@AppStateStore) { historyVersion to historySnapshot() }
                    history?.save(snapshot.first, snapshot.second)
                    pruneMediaIfDue(context, snapshot.second)
                } catch (e: Exception) { _historyError.value = "Could not save chats. Check available device storage." }
            }
        }
    }

    private fun pruneMediaIfDue(context: android.content.Context, entries: List<ChatHistoryStore.Entry>) {
        val now = System.currentTimeMillis()
        if (lastMediaCleanupAt != 0L && now - lastMediaCleanupAt < 60L * 60 * 1000) return
        try {
            com.bitchat.android.features.file.FileUtils.pruneUnreferencedMedia(context,
                entries.filter { it.type != "Message" }.map { it.content }.toSet(), now)
            lastMediaCleanupAt = now
        } catch (_: Exception) { /* Cleanup failure must not turn a successful history save into a failure. */ }
    }

    private fun historySnapshot(): List<ChatHistoryStore.Entry> {
        fun entry(msg: BitchatMessage, conversation: String?, privateChat: Boolean) = ChatHistoryStore.Entry(
            conversation, privateChat, msg.id, msg.sender, msg.content, msg.type.name,
            msg.timestamp.time, msg.senderPeerID, msg.isEncrypted, ChatHistoryStore.Status.from(msg.deliveryStatus)
        )
        return _publicMessages.value.map { entry(it, null, false) } +
            _privateMessages.value.flatMap { (peer, msgs) -> msgs.map { entry(it, peer, true) } } +
            _channelMessages.value.flatMap { (channel, msgs) -> msgs.map { entry(it, channel, false) } }
    }

    private fun scheduleSave() {
        if (seenMessageIds.size > 10_000 || seenPublicMessageKeys.size > 1_000) {
            seenMessageIds.clear()
            (_publicMessages.value + _privateMessages.value.values.flatten() + _channelMessages.value.values.flatten())
                .forEach { seenMessageIds.add(it.id) }
            seenPublicMessageKeys.clear()
            _publicMessages.value.forEach { seenPublicMessageKeys.add(publicMessageKey(it)) }
        }
        historyVersion++
        saveRequests.trySend(Unit)
    }

    private fun conversationKey(peer: String?, channel: String?): String {
        val value = when {
            peer != null -> "private:$peer"
            channel != null -> "channel:$channel"
            else -> "public"
        }
        return java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun accepts(timestamp: Long, peer: String? = null, channel: String? = null): Boolean = synchronized(this) {
        timestamp > (deletedBefore[conversationKey(peer, channel)] ?: Long.MIN_VALUE)
    }

    fun accepts(message: BitchatMessage): Boolean = accepts(
        message.timestamp.time, if (message.isPrivate) message.senderPeerID else null, message.channel
    )

    /** Keep a cutoff so gossip/history replay cannot restore a cleared conversation. */
    fun clearConversation(peer: String? = null, channel: String? = null): List<BitchatMessage> = synchronized(this) {
        val removed = when {
            peer != null -> _privateMessages.value[peer].orEmpty()
            channel != null -> _channelMessages.value[channel].orEmpty()
            else -> _publicMessages.value
        }
        val key = conversationKey(peer, channel)
        val cutoff = maxOf(System.currentTimeMillis(), removed.maxOfOrNull { it.timestamp.time } ?: 0L,
            deletedBefore[key] ?: 0L)
        // Commit before publishing the empty snapshot. A failed write must not claim successful deletion.
        check(deletionPrefs?.edit()?.putLong(key, cutoff)?.commit() != false) { "Could not save chat deletion" }
        deletedBefore[key] = cutoff
        removed.forEach { seenMessageIds.remove(it.id); seenPublicMessageKeys.remove(publicMessageKey(it)) }
        when {
            peer != null -> _privateMessages.value = _privateMessages.value - peer
            channel != null -> _channelMessages.value = _channelMessages.value - channel
            else -> _publicMessages.value = emptyList()
        }
        historyVersion++
        history?.save(historyVersion, historySnapshot())
        saveRequests.trySend(Unit)
        removed
    }
    // Global de-dup set by message id to avoid duplicate keys in Compose lists
    private val seenMessageIds = mutableSetOf<String>()
    private val seenPublicMessageKeys = mutableSetOf<String>()
    private val peerIdsByTransport = mutableMapOf<String, Set<String>>()
    // Direct (single-hop) peer IDs per transport, used to gossip a unified neighbor set.
    private val directPeerIdsByTransport = mutableMapOf<String, Set<String>>()
    // Connected peer IDs (mesh ephemeral IDs)
    private val _peers = MutableStateFlow<List<String>>(emptyList())
    val peers: StateFlow<List<String>> = _peers.asStateFlow()

    // Public mesh timeline messages (non-channel)
    private val _publicMessages = MutableStateFlow<List<BitchatMessage>>(emptyList())
    val publicMessages: StateFlow<List<BitchatMessage>> = _publicMessages.asStateFlow()

    // Private messages by peerID
    private val _privateMessages = MutableStateFlow<Map<String, List<BitchatMessage>>>(emptyMap())
    val privateMessages: StateFlow<Map<String, List<BitchatMessage>>> = _privateMessages.asStateFlow()

    // Channel messages by channel name
    private val _channelMessages = MutableStateFlow<Map<String, List<BitchatMessage>>>(emptyMap())
    val channelMessages: StateFlow<Map<String, List<BitchatMessage>>> = _channelMessages.asStateFlow()

    fun containsMessage(id: String): Boolean = synchronized(this) { seenMessageIds.contains(id) }

    fun setPeers(ids: List<String>) {
        synchronized(this) {
            _peers.value = ids.distinct()
        }
    }

    fun setTransportPeers(transportId: String, ids: List<String>) {
        synchronized(this) {
            peerIdsByTransport[transportId] = ids.toSet()
            publishTransportPeersLocked()
        }
    }

    fun clearTransportPeers(transportId: String) {
        synchronized(this) {
            peerIdsByTransport.remove(transportId)
            publishTransportPeersLocked()
        }
    }

    private fun publishTransportPeersLocked() {
        _peers.value = peerIdsByTransport.values
            .asSequence()
            .flatten()
            .distinct()
            .toList()
    }

    /**
     * Record the set of direct (single-hop) peers reachable over a given transport. Each transport
     * (BLE, Wi-Fi Aware, ...) only knows its own direct peers; [getDirectPeers] unions them so every
     * transport can gossip the same complete neighbor list under our shared node identity.
     */
    fun setTransportDirectPeers(transportId: String, ids: Collection<String>) {
        synchronized(this) {
            directPeerIdsByTransport[transportId] = ids.toSet()
        }
    }

    fun clearTransportDirectPeers(transportId: String) {
        synchronized(this) {
            directPeerIdsByTransport.remove(transportId)
        }
    }

    /** Union of direct peers across all transports. */
    fun getDirectPeers(): Set<String> {
        synchronized(this) {
            return directPeerIdsByTransport.values.flatten().toSet()
        }
    }

    fun addPublicMessage(msg: BitchatMessage) {
        synchronized(this) {
            if (!accepts(msg.timestamp.time)) return
            val publicKey = publicMessageKey(msg)
            if (seenMessageIds.contains(msg.id) || seenPublicMessageKeys.contains(publicKey)) return
            seenMessageIds.add(msg.id)
            seenPublicMessageKeys.add(publicKey)
            _publicMessages.value = (_publicMessages.value + msg).takeLast(MAX_MESSAGES_PER_CHAT)
            scheduleSave()
        }
    }

    fun addPrivateMessage(peerID: String, msg: BitchatMessage) {
        synchronized(this) {
            if (!accepts(msg.timestamp.time, peer = peerID)) return
            if (seenMessageIds.contains(msg.id)) return
            seenMessageIds.add(msg.id)
            val map = _privateMessages.value.toMutableMap()
            val list = ((map[peerID] ?: emptyList()) + msg).takeLast(MAX_MESSAGES_PER_CHAT)
            map[peerID] = list
            _privateMessages.value = map
            scheduleSave()
        }
    }

    private fun statusPriority(status: DeliveryStatus?): Int = when (status) {
        null -> 0
        is DeliveryStatus.Sending -> 1
        is DeliveryStatus.Sent -> 2
        is DeliveryStatus.PartiallyDelivered -> 3
        is DeliveryStatus.Delivered -> 4
        is DeliveryStatus.Read -> 5
        is DeliveryStatus.Failed -> 0
    }

    fun updatePrivateMessageStatus(messageID: String, status: DeliveryStatus) {
        synchronized(this) {
            val map = _privateMessages.value.toMutableMap()
            var changed = false
            map.keys.toList().forEach { peer ->
                val list = map[peer]?.toMutableList() ?: mutableListOf()
                val idx = list.indexOfFirst { it.id == messageID }
                if (idx >= 0) {
                    val current = list[idx].deliveryStatus
                    // Do not downgrade (e.g., Read -> Delivered)
                    val failedUnconfirmed = status is DeliveryStatus.Failed && current !is DeliveryStatus.Delivered && current !is DeliveryStatus.Read
                    if (failedUnconfirmed || statusPriority(status) >= statusPriority(current)) {
                        list[idx] = list[idx].copy(deliveryStatus = status)
                        map[peer] = list
                        changed = true
                    }
                }
            }
            if (changed) {
                _privateMessages.value = map
                scheduleSave()
            }
        }
    }

    fun addChannelMessage(channel: String, msg: BitchatMessage) {
        synchronized(this) {
            if (!accepts(msg.timestamp.time, channel = channel)) return
            if (seenMessageIds.contains(msg.id)) return
            seenMessageIds.add(msg.id)
            val map = _channelMessages.value.toMutableMap()
            val list = ((map[channel] ?: emptyList()) + msg).takeLast(MAX_MESSAGES_PER_CHAT)
            map[channel] = list
            _channelMessages.value = map
            scheduleSave()
        }
    }

    fun flushHistory() {
        val snapshot = synchronized(this) { historyVersion to historySnapshot() }
        history?.save(snapshot.first, snapshot.second)
    }

    // Clear all in-memory state (used for full app shutdown)
    fun clear() {
        synchronized(this) {
            deletedBefore.clear()
            deletionPrefs?.all?.forEach { (id, time) -> if (time is Long) deletedBefore[id] = time }
            seenMessageIds.clear()
            seenPublicMessageKeys.clear()
            peerIdsByTransport.clear()
            directPeerIdsByTransport.clear()
            _peers.value = emptyList()
            _publicMessages.value = emptyList()
            _privateMessages.value = emptyMap()
            _channelMessages.value = emptyMap()
        }
    }

    private fun publicMessageKey(msg: BitchatMessage): String {
        val sender = msg.senderPeerID ?: msg.sender
        return listOf(
            sender,
            msg.timestamp.time.toString(),
            msg.type.name,
            msg.channel ?: "",
            msg.content
        ).joinToString("\u001F")
    }
}
