package com.bitchat.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.model.BitchatMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ChatViewState {
    INBOX,
    CHAT
}

private data class InboxItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isPrivate: Boolean
)

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val publicMessages by viewModel.messages.collectAsStateWithLifecycle()
    val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
    val channelMessages by viewModel.channelMessages.collectAsStateWithLifecycle()
    val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
    val unreadPrivateMessages by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val unreadChannelMessages by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()

    var currentView by remember { mutableStateOf(ChatViewState.INBOX) }
    var selectedConvId by remember { mutableStateOf<String?>(null) }
    var selectedConvIsPrivate by remember { mutableStateOf(false) }
    var isOfficialUpdates by remember { mutableStateOf(false) }
    var showPanicWipe by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf(TextFieldValue("")) }

    val inboxItems = remember(publicMessages, privateChats, channelMessages, joinedChannels, unreadChannelMessages, unreadPrivateMessages, peerNicknames) {
        val items = mutableListOf<InboxItem>()

        if ("official_updates" in joinedChannels) {
            val msgs = channelMessages["official_updates"] ?: emptyList()
            val last = msgs.lastOrNull()
            items.add(
                InboxItem(
                    id = "official_updates",
                    title = "\uD83D\uDCE2 CJP Official Updates",
                    subtitle = last?.content ?: "No messages yet",
                    timestamp = last?.let { timeFormatter.format(it.timestamp) } ?: "",
                    unreadCount = unreadChannelMessages["official_updates"] ?: 0,
                    isPinned = true,
                    isPrivate = false
                )
            )
        }

        val lastPublic = publicMessages.lastOrNull()
        items.add(
            InboxItem(
                id = "local_radius",
                title = "\uD83D\uDCCD Local Radius Chatter",
                subtitle = lastPublic?.content ?: "No messages yet",
                timestamp = lastPublic?.let { timeFormatter.format(it.timestamp) } ?: "",
                unreadCount = 0,
                isPinned = false,
                isPrivate = false
            )
        )

        privateChats.forEach { (peerId, msgs) ->
            val last = msgs.lastOrNull()
            val displayName = peerNicknames[peerId] ?: peerId
            items.add(
                InboxItem(
                    id = peerId,
                    title = displayName,
                    subtitle = last?.let {
                        if (it.senderPeerID == viewModel.myPeerID || it.sender == nickname) "You: ${it.content}"
                        else it.content
                    } ?: "No messages yet",
                    timestamp = last?.let { timeFormatter.format(it.timestamp) } ?: "",
                    unreadCount = if (peerId in unreadPrivateMessages) 1 else 0,
                    isPinned = false,
                    isPrivate = true
                )
            )
        }
        items.toList()
    }

    val displayMessages: List<BitchatMessage> = when {
        selectedConvId == null -> emptyList()
        isOfficialUpdates -> channelMessages["official_updates"] ?: emptyList()
        selectedConvIsPrivate -> privateChats[selectedConvId] ?: emptyList()
        else -> publicMessages
    }

    val isReadOnly = isOfficialUpdates

    BackHandler(enabled = currentView == ChatViewState.CHAT) {
        if (selectedConvIsPrivate && selectedConvId != null) {
            viewModel.endPrivateChat()
        }
        if (isOfficialUpdates) {
            viewModel.switchToChannel(null)
        }
        currentView = ChatViewState.INBOX
        selectedConvId = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            val headerTitle = when {
                selectedConvId == null -> "MeshChat"
                isOfficialUpdates -> "\uD83D\uDCE2 CJP Official Updates"
                selectedConvIsPrivate -> peerNicknames[selectedConvId] ?: selectedConvId ?: "Chat"
                else -> "\uD83D\uDCCD Local Radius Chatter"
            }

            MeshChatHeader(
                showBack = currentView == ChatViewState.CHAT,
                title = headerTitle,
                onBack = {
                    if (selectedConvIsPrivate && selectedConvId != null) {
                        viewModel.endPrivateChat()
                    }
                    if (isOfficialUpdates) {
                        viewModel.switchToChannel(null)
                    }
                    currentView = ChatViewState.INBOX
                    selectedConvId = null
                },
                onShareClick = {
                    coroutineScope.launch { com.bitchat.android.utils.AppShareUtils.shareApkOffline(context) }
                },
                onTripleShieldClick = { showPanicWipe = true },
                peerCount = connectedPeers.size
            )

            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))

            when (currentView) {
                ChatViewState.INBOX -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(items = inboxItems, key = { it.id }) { item ->
                            InboxRow(item = item, onClick = {
                                selectedConvId = item.id
                                selectedConvIsPrivate = item.isPrivate
                                isOfficialUpdates = item.id == "official_updates"
                                if (item.isPrivate) {
                                    viewModel.startPrivateChat(item.id)
                                }
                                if (item.id == "official_updates") {
                                    viewModel.switchToChannel("official_updates")
                                }
                                currentView = ChatViewState.CHAT
                            })
                        }
                    }
                }
                ChatViewState.CHAT -> {
                    MessagesList(
                        messages = displayMessages,
                        currentUserNickname = nickname,
                        meshService = viewModel.meshServiceFacade,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isReadOnly) {
                        ChatInputBar(
                            messageText = messageText,
                            onMessageTextChange = { messageText = it },
                            onSend = {
                                if (messageText.text.trim().isNotEmpty()) {
                                    if (isOfficialUpdates) return@ChatInputBar
                                    viewModel.sendMessage(messageText.text.trim())
                                    messageText = TextFieldValue("")
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showPanicWipe) {
            PanicWipeOverlay(
                onWipeComplete = {
                    showPanicWipe = false
                    viewModel.panicClearAllData()
                    currentView = ChatViewState.INBOX
                    selectedConvId = null
                },
                modifier = Modifier.zIndex(10f)
            )
        }
    }
}

@Composable
private fun InboxRow(
    item: InboxItem,
    onClick: () -> Unit
) {
    val bgColor = if (item.isPinned) Color(0xFF182229) else Color.Transparent
    val accentColor = if (item.isPinned) Color(0xFF53BDEC) else Color(0xFF25D366)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isPinned) Color(0xFF53BDEC) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                if (item.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF25D366)
                    ) {
                        Text(
                            text = item.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
fun ChatInputBar(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.background
    ) {
        Column {
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF202C33),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = messageText,
                        onValueChange = onMessageTextChange,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFE9EDEF),
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF25D366)),
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        decorationBox = { innerTextField ->
                            Box {
                                if (messageText.text.isEmpty()) {
                                    Text(
                                        text = "Message",
                                        color = Color(0xFF8696A0),
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF25D366),
                    modifier = Modifier.size(42.dp),
                    onClick = onSend
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputSection(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendVoiceNote: (String?, String?, String) -> Unit,
    onSendImageNote: (String?, String?, String) -> Unit,
    onSendFileNote: (String?, String?, String) -> Unit,
    showMentionSuggestions: Boolean,
    mentionSuggestions: List<String>,
    onMentionSuggestionClick: (String) -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    colorScheme: ColorScheme,
    showMediaButtons: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.background
    ) {
        Column {
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
            if (showMentionSuggestions && mentionSuggestions.isNotEmpty()) {
                MentionSuggestionsBox(
                    suggestions = mentionSuggestions,
                    onSuggestionClick = onMentionSuggestionClick,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
            }
            MessageInput(
                value = messageText,
                onValueChange = onMessageTextChange,
                onSend = onSend,
                onSendVoiceNote = onSendVoiceNote,
                onSendImageNote = onSendImageNote,
                onSendFileNote = onSendFileNote,
                selectedPrivatePeer = selectedPrivatePeer,
                currentChannel = currentChannel,
                nickname = nickname,
                showMediaButtons = showMediaButtons,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
