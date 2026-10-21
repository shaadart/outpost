package com.bitchat.android.services

import android.content.Context
import android.util.Log
import com.bitchat.android.mesh.MeshService
import com.bitchat.android.model.ReadReceipt
import com.bitchat.android.nostr.NostrTransport
import com.bitchat.android.model.BitchatMessageType
import com.bitchat.android.model.DeliveryStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Routes messages between local mesh transports and Nostr, matching iOS behavior.
 */
class MessageRouter private constructor(
    private val context: Context,
    private var mesh: MeshService,
    private val nostr: NostrTransport
) {
    companion object {
        private const val TAG = "MessageRouter"
        @Volatile private var INSTANCE: MessageRouter? = null
        fun tryGetInstance(): MessageRouter? = INSTANCE
        fun getInstance(context: Context, mesh: MeshService): MessageRouter {
            val instance = INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val nostr = NostrTransport.getInstance(context)
                    MessageRouter(context.applicationContext, mesh, nostr).also { instance ->
                        // Register for favorites changes to flush outbox
                        try {
                            com.bitchat.android.favorites.FavoritesPersistenceService.shared.addListener(instance.favoriteListener)
                        } catch (_: Exception) {}
                        INSTANCE = instance
                        instance.restoreQueuedMessages()
                    }
                }
            }
            // Always update mesh reference and sync peer ID
            instance.mesh = mesh
            instance.nostr.senderPeerID = mesh.myPeerID
            return instance
        }
    }

    // Outbox: peerID -> queued (content, nickname, messageID)
    private val outbox = mutableMapOf<String, MutableList<Triple<String, String, String>>>()
    private val routingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Pending text lives in encrypted history; no second plaintext outbox is created. */
    private fun restoreQueuedMessages() {
        routingScope.launch {
            AppStateStore.historyReady.first { it }
            while (isActive) {
                synchronized(this@MessageRouter) {
                    AppStateStore.privateMessages.value.forEach { (peer, messages) ->
                        messages.filter { it.type == BitchatMessageType.Message && it.deliveryStatus is DeliveryStatus.Sending }
                            .forEach { message ->
                                val queue = outbox.getOrPut(peer) { mutableListOf() }
                                if (queue.none { it.third == message.id }) {
                                    queue.add(Triple(message.content, mesh.getPeerNicknames()[peer] ?: peer, message.id))
                                }
                            }
                    }
                    // Commit before attempting dispatch, including the process-death recovery case.
                    try {
                        if (outbox.isNotEmpty()) {
                            AppStateStore.flushHistory()
                            flushAllOutbox()
                        }
                    } catch (e: Exception) { Log.w(TAG, "Could not persist pending messages", e) }
                }
                delay(5_000)
            }
        }
    }

    // Listener for favorites changes to flush outbox when npub mapping appears/changes
    private val favoriteListener = object: com.bitchat.android.favorites.FavoritesChangeListener {

        override fun onFavoriteChanged(noiseKeyHex: String) {
            flushOutboxFor(noiseKeyHex)
            // Also try 16-hex short id commonly used in UI if any client used that
            val shortId = noiseKeyHex.take(16)
            flushOutboxFor(shortId)
        }
        override fun onAllCleared() {
            clearOutbox()
        }
    }

    @Synchronized
    fun clearOutbox(peerID: String? = null) {
        if (peerID == null) outbox.clear() else outbox.remove(peerID)
    }

    fun sendPrivate(content: String, toPeerID: String, recipientNickname: String, messageID: String) {
        routingScope.launch {
            AppStateStore.historyReady.first { it }
            synchronized(this@MessageRouter) {
                if (!AppStateStore.containsMessage(messageID)) return@synchronized
                try {
                    AppStateStore.flushHistory()
                    routePrivate(content, toPeerID, recipientNickname, messageID)
                } catch (e: Exception) {
                    AppStateStore.updatePrivateMessageStatus(messageID, DeliveryStatus.Failed("Could not save or send message."))
                    Log.w(TAG, "Private message was not dispatched", e)
                }
            }
        }
    }

    private fun markDispatched(messageID: String) {
        // Sent means handed to a transport; only a receiver acknowledgement means Delivered.
        AppStateStore.updatePrivateMessageStatus(messageID, DeliveryStatus.Sent)
    }

    private fun routePrivate(content: String, toPeerID: String, recipientNickname: String, messageID: String) {
        // First: if this is a geohash DM alias (nostr_<pub16>), route via Nostr using global registry
        if (com.bitchat.android.nostr.GeohashAliasRegistry.contains(toPeerID)) {
            Log.d(TAG, "Routing PM via Nostr (geohash) to alias ${toPeerID.take(12)}… id=${messageID.take(8)}…")
            val recipientHex = com.bitchat.android.nostr.GeohashAliasRegistry.get(toPeerID)
            if (recipientHex != null) {
                // Resolve the conversation's source geohash, so we can send from anywhere
                val sourceGeohash = com.bitchat.android.nostr.GeohashConversationRegistry.get(toPeerID)

                // If repository knows the source geohash, pass it so NostrTransport derives the correct identity
                nostr.sendPrivateMessageGeohash(content, recipientHex, messageID, sourceGeohash)
                markDispatched(messageID)
                return
            }
        }

        val hasMesh = isConnected(mesh, toPeerID)
        if (isReady(mesh, toPeerID)) {
            Log.d(TAG, "Routing PM via mesh to ${toPeerID} msg_id=${messageID.take(8)}…")
            mesh.sendPrivateMessage(content, toPeerID, recipientNickname, messageID)
            markDispatched(messageID)
        } else if (canSendViaNostr(toPeerID)) {
            Log.d(TAG, "Routing PM via Nostr to ${toPeerID.take(32)}… msg_id=${messageID.take(8)}…")
            nostr.sendPrivateMessage(content, toPeerID, recipientNickname, messageID)
            markDispatched(messageID)
        } else {
            Log.d(TAG, "Queued PM for ${toPeerID} (no mesh, no Nostr mapping) msg_id=${messageID.take(8)}…")
            val q = outbox.getOrPut(toPeerID) { mutableListOf() }
            if (q.none { it.third == messageID }) q.add(Triple(content, recipientNickname, messageID))
            Log.d(TAG, "Initiating noise handshake after queueing PM for ${toPeerID.take(8)}…")
            if (hasMesh) mesh.initiateNoiseHandshake(toPeerID)
        }
    }

    fun sendReadReceipt(receipt: ReadReceipt, toPeerID: String) {
        if (isReady(mesh, toPeerID)) {
            Log.d(TAG, "Routing READ via mesh to ${toPeerID.take(8)}… id=${receipt.originalMessageID.take(8)}…")
            mesh.sendReadReceipt(receipt.originalMessageID, toPeerID, mesh.getPeerNicknames()[toPeerID] ?: mesh.myPeerID)
        } else {
            Log.d(TAG, "Routing READ via Nostr to ${toPeerID.take(8)}… id=${receipt.originalMessageID.take(8)}…")
            nostr.sendReadReceipt(receipt, toPeerID)
        }
    }

    fun sendDeliveryAck(messageID: String, toPeerID: String) {
        // Mesh delivery ACKs are sent by the receiver automatically.
        // Only route via Nostr when mesh path isn't available or when this is a geohash alias
        if (com.bitchat.android.nostr.GeohashAliasRegistry.contains(toPeerID)) {
            val recipientHex = com.bitchat.android.nostr.GeohashAliasRegistry.get(toPeerID)
            if (recipientHex != null) {
                nostr.sendDeliveryAckGeohash(messageID, recipientHex, try { com.bitchat.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(context)!! } catch (_: Exception) { return })
                return
            }
        }
        if (!((mesh.getPeerInfo(toPeerID)?.isConnected == true) && mesh.hasEstablishedSession(toPeerID))) {
            nostr.sendDeliveryAck(messageID, toPeerID)
        }
    }

    fun sendFavoriteNotification(toPeerID: String, isFavorite: Boolean) {
        if (mesh.getPeerInfo(toPeerID)?.isConnected == true && mesh.hasEstablishedSession(toPeerID)) {
            val myNpub = try { com.bitchat.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(context)?.npub } catch (_: Exception) { null }
            val content = if (isFavorite) "[FAVORITED]:${myNpub ?: ""}" else "[UNFAVORITED]:${myNpub ?: ""}"
            val nickname = mesh.getPeerNicknames()[toPeerID] ?: toPeerID
            mesh.sendPrivateMessage(content, toPeerID, nickname, null)
        } else {
            nostr.sendFavoriteNotification(toPeerID, isFavorite)
        }
    }

    // Flush any queued messages for a specific peerID
    @Synchronized
    fun flushOutboxFor(peerID: String) {
        val queued = outbox[peerID] ?: return
        if (queued.isEmpty()) return
        if (isConnected(mesh, peerID) && !isReady(mesh, peerID)) mesh.initiateNoiseHandshake(peerID)
        Log.d(TAG, "Flushing outbox for ${peerID.take(8)}… count=${queued.size}")
        val iterator = queued.iterator()
        while (iterator.hasNext()) {
            val (content, nickname, messageID) = iterator.next()
            if (!AppStateStore.containsMessage(messageID)) { iterator.remove(); continue }
            val hasMesh = isReady(mesh, peerID)
            // If this is a noiseHex key, see if there is a connected mesh peer for this identity
            if (!hasMesh && peerID.length == 64 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                val meshPeer = resolvePeerForNoiseHex(peerID, mesh)
                if (meshPeer != null && isReady(mesh, meshPeer)) {
                    mesh.sendPrivateMessage(content, meshPeer, nickname, messageID)
                    markDispatched(messageID)
                    iterator.remove()
                    continue
                }
            }
            val canNostr = canSendViaNostr(peerID)
            if (hasMesh) {
                mesh.sendPrivateMessage(content, peerID, nickname, messageID)
                markDispatched(messageID)
                iterator.remove()
            } else if (canNostr) {
                nostr.sendPrivateMessage(content, peerID, nickname, messageID)
                markDispatched(messageID)
                iterator.remove()
            }
        }
        if (queued.isEmpty()) {
            outbox.remove(peerID)
        }
    }

    // Flush everything (rarely used)
    @Synchronized
    fun flushAllOutbox() {
        outbox.keys.toList().forEach { flushOutboxFor(it) }
    }

    private fun canSendViaNostr(peerID: String): Boolean {
        return try {
            // Full Noise key hex
            if (peerID.length == 64 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                val noiseKey = hexToBytes(peerID)
                val fav = com.bitchat.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(noiseKey)
                fav?.isMutual == true && fav.peerNostrPublicKey != null
            } else if (peerID.length == 16 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                // Ephemeral 16-hex mesh ID: resolve via prefix match in favorites
                val fav = com.bitchat.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(peerID)
                fav?.isMutual == true && fav.peerNostrPublicKey != null
            } else {
                false
            }
        } catch (_: Exception) { false }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 == 0) hex else "0$hex"
        return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun isConnected(service: MeshService, peerID: String): Boolean {
        return try {
            service.getPeerInfo(peerID)?.isConnected == true
        } catch (_: Exception) {
            false
        }
    }

    private fun isReady(service: MeshService, peerID: String): Boolean {
        return try {
            service.getPeerInfo(peerID)?.isConnected == true &&
                service.hasEstablishedSession(peerID)
        } catch (_: Exception) {
            false
        }
    }

    private fun resolvePeerForNoiseHex(noiseHex: String, service: MeshService): String? {
        return try {
            service.getPeerNicknames().keys.firstOrNull { pid ->
                val info = service.getPeerInfo(pid)
                val keyHex = info?.noisePublicKey?.joinToString("") { b -> "%02x".format(b) }
                keyHex != null && keyHex.equals(noiseHex, ignoreCase = true)
            }
        } catch (_: Exception) { null }
    }

    // Called when mesh peer list changes; attempt to flush any matching outbox entries
    fun onPeersUpdated(peers: List<String>) {
        peers.forEach { pid ->
            flushOutboxFor(pid)
            val noiseHex = try {
                mesh.getPeerInfo(pid)?.noisePublicKey?.joinToString("") { b -> "%02x".format(b) }
            } catch (_: Exception) { null }
            noiseHex?.let { flushOutboxFor(it) }
        }
    }

    // Called when a Noise session becomes established; flush both the mesh peerID and its noiseHex alias
    fun onSessionEstablished(peerID: String) {
        flushOutboxFor(peerID)
        val noiseHex = try {
            mesh.getPeerInfo(peerID)?.noisePublicKey?.joinToString("") { b -> "%02x".format(b) }
        } catch (_: Exception) { null }
        noiseHex?.let { flushOutboxFor(it) }
    }
}
