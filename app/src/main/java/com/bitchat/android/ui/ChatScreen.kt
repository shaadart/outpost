package com.bitchat.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.groups.GroupInvite
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.BitchatMessageType
import com.bitchat.android.services.VerificationService
import com.bitchat.android.util.hexEncodedString
import kotlinx.coroutines.launch
import java.text.DateFormat

private data class InboxItem(
    val id: String,
    val title: String,
    val messages: List<BitchatMessage>,
    val unread: Int = 0,
    val isPrivate: Boolean = false
)

@Composable
fun ChatScreen(viewModel: ChatViewModel, initialGroupLink: String? = null, onGroupLinkHandled: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val historyReady by com.bitchat.android.services.AppStateStore.historyReady.collectAsStateWithLifecycle()
    val historyError by com.bitchat.android.services.AppStateStore.historyError.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val publicMessages by viewModel.messages.collectAsStateWithLifecycle()
    val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
    val channelMessages by viewModel.channelMessages.collectAsStateWithLifecycle()
    val groupNames by viewModel.groupNames.collectAsStateWithLifecycle()
    val unreadPrivate by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val unreadChannels by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
    val peerNames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val peers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val selectedPeer by viewModel.selectedPrivateChatPeer.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    var publicChatOpen by rememberSaveable { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var inviteInput by remember { mutableStateOf("") }
    var shareInvite by remember { mutableStateOf<GroupInvite?>(null) }
    var messageText by remember(selectedPeer, selectedChannel, publicChatOpen) { mutableStateOf(TextFieldValue("")) }
    val snackbar = remember { SnackbarHostState() }
    val inChat = selectedPeer != null || selectedChannel != null || publicChatOpen
    val title = selectedPeer?.let { peerNames[it] ?: it }
        ?: selectedChannel?.let { groupNames[it] ?: it.removePrefix("#") }
        ?: if (publicChatOpen) "Nearby chat" else "Outpost"
    val displayed = when {
        selectedPeer != null -> privateChats[selectedPeer].orEmpty()
        selectedChannel != null -> channelMessages[selectedChannel].orEmpty()
        publicChatOpen -> publicMessages
        else -> emptyList()
    }

    fun closeChat() {
        viewModel.endPrivateChat()
        viewModel.switchToChannel(null)
        publicChatOpen = false
    }

    fun openPrivate(id: String) {
        publicChatOpen = false
        viewModel.switchToChannel(null)
        viewModel.startPrivateChat(id)
    }

    BackHandler(enabled = inChat) { closeChat() }
    LaunchedEffect(initialGroupLink) {
        if (initialGroupLink != null) {
            inviteInput = initialGroupLink
            showJoin = true
            onGroupLinkHandled()
        }
    }
    LaunchedEffect(operationError) {
        operationError?.let { snackbar.showSnackbar(it); viewModel.clearOperationError() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MeshChatHeader(showBack = inChat, title = title, onBack = { closeChat() },
                        onTripleShieldClick = { confirmWipe = true }, peerCount = peers.size,
                        modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Profile") }, onClick = { showMenu = false; showProfile = true })
                            if (selectedChannel?.let(GroupInvite::isGroup) == true) {
                                DropdownMenuItem(text = { Text("Invite people") }, onClick = {
                                    showMenu = false
                                    val channel = selectedChannel ?: return@DropdownMenuItem
                                    scope.launch { shareInvite = viewModel.groupInvitation(channel) }
                                })
                                DropdownMenuItem(text = { Text("Leave group") }, onClick = { showMenu = false; confirmLeave = true })
                            }
                            if (inChat && historyReady) DropdownMenuItem(text = { Text("Clear chat") }, onClick = { showMenu = false; confirmClear = true })
                            DropdownMenuItem(text = { Text("Notifications") }, onClick = {
                                showMenu = false
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                            })
                            DropdownMenuItem(text = { Text("Connection permissions") }, onClick = {
                                showMenu = false
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)))
                            })
                            DropdownMenuItem(text = { Text("Erase account and all data") }, onClick = { showMenu = false; confirmWipe = true })
                        }
                    }
                }
                HorizontalDivider()
            }
        },
        floatingActionButton = {
            if (!inChat) FloatingActionButton(onClick = { showActions = true }) { Icon(Icons.Default.Add, "New chat or group") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!historyReady) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (historyError == null) CircularProgressIndicator()
                    else Text(historyError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            } else if (inChat) {
                historyError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                Text(
                    if (selectedPeer != null) "Private conversation · history stored encrypted on this device"
                    else if (selectedChannel != null) "Encrypted group · invitation holders share access"
                    else "Public nearby chat · visible to everyone on the mesh",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (peers.isEmpty()) Text("No nearby connection. Delivery may be delayed.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                key(selectedPeer, selectedChannel, publicChatOpen) {
                MessagesList(messages = displayed, currentUserNickname = nickname,
                    meshService = viewModel.meshServiceFacade, modifier = Modifier.weight(1f),
                    onCancelTransfer = { viewModel.cancelMediaSend(it.id) })
                }
                MessageInput(value = messageText, onValueChange = { messageText = it },
                    onSend = {
                        val text = messageText.text.trim()
                        if (text.isNotBlank() && viewModel.sendMessage(text)) messageText = TextFieldValue("")
                    },
                    onSendVoiceNote = viewModel::sendVoiceNote,
                    onSendImageNote = viewModel::sendImageNote,
                    onSendFileNote = viewModel::sendFileNote,
                    selectedPrivatePeer = selectedPeer, currentChannel = selectedChannel,
                    nickname = nickname, showMediaButtons = true)
            } else {
                val chats = privateChats.map { (peer, messages) ->
                    InboxItem(peer, peerNames[peer] ?: peer, messages, if (peer in unreadPrivate) 1 else 0, true)
                } + groupNames.map { (id, name) -> InboxItem(id, name, channelMessages[id].orEmpty(), unreadChannels[id] ?: 0) }
                val sorted = chats.sortedByDescending { it.messages.lastOrNull()?.timestamp?.time ?: 0 }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    item("nearby") {
                        historyError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                        ListItem(headlineContent = { Text("People nearby") },
                            supportingContent = { Text(if (peers.isEmpty()) "Keep Bluetooth on to discover people" else "${peers.size} people available to chat") },
                            modifier = Modifier.clickable { showPeople = true })
                        ListItem(headlineContent = { Text("Nearby chat") },
                            supportingContent = { Text("Public conversation with the local mesh") },
                            modifier = Modifier.clickable { closeChat(); publicChatOpen = true })
                        HorizontalDivider()
                    }
                    if (sorted.isEmpty()) item("empty") {
                        Text("Start a private chat or create a group. Your profile needs no phone number or email.",
                            Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                    items(sorted, key = { if (it.isPrivate) "private:${it.id}" else "group:${it.id}" }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(item.messages.lastOrNull()?.let(::messagePreview) ?: "No messages yet", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    item.messages.lastOrNull()?.let { Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(it.timestamp), style = MaterialTheme.typography.labelSmall) }
                                    if (item.unread > 0) Badge { Text(item.unread.toString()) }
                                }
                            },
                            modifier = Modifier.clickable {
                                if (item.isPrivate) openPrivate(item.id) else {
                                    viewModel.endPrivateChat(); publicChatOpen = false; viewModel.switchToChannel(item.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    ActionMenuSheet(isPresented = showActions, onDismiss = { showActions = false },
        onOpenProfile = { showProfile = true }, onScanQR = { showScanner = true },
        onCreateGroup = { showCreate = true }, onJoinGroup = { inviteInput = ""; showJoin = true },
        onNewChat = { showPeople = true },
        onShareApp = { scope.launch { com.bitchat.android.utils.AppShareUtils.shareApkOffline(context) } })
    ProfileSettingsSheet(isPresented = showProfile, onDismiss = { showProfile = false }, viewModel = viewModel)

    if (showCreate) CreateGroupDialog(onDismiss = { showCreate = false }, busy = busy, onCreate = { name ->
        scope.launch {
            busy = true
            try { viewModel.createGroup(name)?.let { showCreate = false; publicChatOpen = false; shareInvite = it } }
            finally { busy = false }
        }
    })
    if (showJoin) JoinGroupDialog(initialLink = inviteInput, onDismiss = { showJoin = false }, busy = busy, onJoin = { invite ->
        scope.launch {
            busy = true
            try { if (viewModel.joinGroup(invite)) { showJoin = false; publicChatOpen = false } }
            finally { busy = false }
        }
    })
    shareInvite?.let { GroupInvitationDialog(it, onDismiss = { shareInvite = null }) }
    if (showPeople) AlertDialog(onDismissRequest = { showPeople = false }, title = { Text("People nearby") },
        text = {
            LazyColumn {
                if (peers.isEmpty()) item { Text("No one nearby yet. Both devices need Outpost open with Bluetooth and nearby permissions enabled.") }
                items(peers, key = { it }) { peer ->
                    ListItem(headlineContent = { Text(peerNames[peer] ?: peer) },
                        modifier = Modifier.clickable { showPeople = false; openPrivate(peer) })
                }
            }
        }, confirmButton = { TextButton(onClick = { showPeople = false }) { Text("Done") } })
    if (showScanner) Dialog(onDismissRequest = { showScanner = false }) {
        Surface {
            ScannerPanel(isActive = true, modifier = Modifier.fillMaxWidth().height(480.dp),
                onCancel = { showScanner = false }, onScanResult = { code ->
                    val invite = GroupInvite.parse(code)
                    if (invite != null) { showScanner = false; inviteInput = code; showJoin = true }
                    else {
                        val qr = VerificationService.verifyScannedQR(code)
                        val peer = qr?.let { data -> peers.firstOrNull { id ->
                            viewModel.getMeshPeerInfo(id)?.noisePublicKey?.hexEncodedString()?.equals(data.noiseKeyHex, true) == true
                        } }
                        if (qr != null && peer != null && viewModel.beginQRVerification(qr)) {
                            showScanner = false; openPrivate(peer)
                        } else scope.launch { snackbar.showSnackbar("Use a group invitation or the profile QR of someone nearby.") }
                    }
                })
        }
    }
    if (confirmClear || confirmLeave) {
        val leaving = confirmLeave
        // Capture the conversation being confirmed; navigation cannot retarget this action.
        val peer = remember { selectedPeer }
        val channel = remember { selectedChannel }
        AlertDialog(onDismissRequest = { confirmClear = false; confirmLeave = false },
            title = { Text(if (leaving) "Leave group?" else "Clear this chat?") },
            text = { Text("Messages and attachments in this chat will be removed from this device. Other people keep their copies." +
                if (leaving) " You will need an invitation to rejoin." else " New messages can still arrive.") },
            confirmButton = { TextButton(onClick = {
                viewModel.clearConversation(peer, channel, leaving)
                confirmClear = false; confirmLeave = false
                if (leaving) closeChat()
            }) { Text(if (leaving) "Leave and clear" else "Clear chat") } },
            dismissButton = { TextButton(onClick = { confirmClear = false; confirmLeave = false }) { Text("Cancel") } })
    }
    if (confirmWipe) AlertDialog(onDismissRequest = { confirmWipe = false },
        title = { Text("Erase account and all app data?") },
        text = { Text("This removes your profile, keys, groups, chats, and app attachments from this device and closes Outpost. You will create a new profile next time. Copies on other devices and files you exported are unaffected.") },
        confirmButton = { TextButton(onClick = { confirmWipe = false; viewModel.panicClearAllData() }) { Text("Erase everything") } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } })
}

private fun messagePreview(message: BitchatMessage): String = when (message.type) {
    BitchatMessageType.Message -> message.content
    BitchatMessageType.Audio -> "Voice message"
    BitchatMessageType.Image -> "Photo"
    BitchatMessageType.File -> "File"
}
