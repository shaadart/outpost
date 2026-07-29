package com.bitchat.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

enum class ChatViewState {
    INBOX,
    CHAT
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val conversations = remember { MockDataProvider.conversations }
    var currentView by remember { mutableStateOf(ChatViewState.INBOX) }
    var selectedConversation by remember { mutableStateOf<MockConversation?>(null) }
    var showPanicWipe by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf(TextFieldValue("")) }

    BackHandler(enabled = currentView == ChatViewState.CHAT) {
        currentView = ChatViewState.INBOX
        selectedConversation = null
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
            MeshChatHeader(
                showBack = currentView == ChatViewState.CHAT,
                onBack = {
                    currentView = ChatViewState.INBOX
                    selectedConversation = null
                },
                onShareClick = {
                    coroutineScope.launch {
                        com.bitchat.android.utils.AppShareUtils.shareApkOffline(context)
                    }
                },
                onTripleShieldClick = { showPanicWipe = true }
            )

            HorizontalDivider(
                color = colorScheme.outline.copy(alpha = 0.3f)
            )

            when (currentView) {
                ChatViewState.INBOX -> {
                    InboxContent(
                        conversations = conversations,
                        onConversationClick = { conversation ->
                            selectedConversation = conversation
                            currentView = ChatViewState.CHAT
                        }
                    )
                }
                ChatViewState.CHAT -> {
                    selectedConversation?.let { conversation ->
                        ChatContent(
                            conversation = conversation,
                            messageText = messageText,
                            onMessageTextChange = { messageText = it },
                            onSend = {
                                if (messageText.text.trim().isNotEmpty()) {
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
                    selectedConversation = null
                },
                modifier = Modifier.zIndex(10f)
            )
        }
    }
}

@Composable
private fun InboxContent(
    conversations: List<MockConversation>,
    onConversationClick: (MockConversation) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        conversations.forEach { conversation ->
            item(key = conversation.id) {
                InboxRow(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation) }
                )
            }
        }
    }
}

@Composable
private fun InboxRow(
    conversation: MockConversation,
    onClick: () -> Unit
) {
    val isPinned = conversation.type == ConversationType.VERIFIED_BROADCAST
    val bgColor = if (isPinned) Color(0xFF182229) else Color.Transparent
    val accentColor = when (conversation.type) {
        ConversationType.VERIFIED_BROADCAST -> Color(0xFF53BDEC)
        ConversationType.LOCAL_RADIUS -> Color(0xFF25D366)
        ConversationType.DIRECT_MESSAGE -> Color(0xFF25D366)
    }

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
                    text = conversation.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPinned) Color(0xFF53BDEC) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = conversation.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = conversation.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF25D366)
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
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
private fun ChatContent(
    conversation: MockConversation,
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit
) {
    val isReadOnly = conversation.type == ConversationType.VERIFIED_BROADCAST

    Column(modifier = Modifier.fillMaxSize()) {
        MessagesView(
            messages = conversation.messages,
            modifier = Modifier.weight(1f)
        )

        if (!isReadOnly) {
            ChatInputBar(
                messageText = messageText,
                onMessageTextChange = onMessageTextChange,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun MessagesView(
    messages: List<MockMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = messages,
            key = { "${it.sender}_${it.timestamp}_${it.content.hashCode()}" }
        ) { message ->
            MessageBubble(message = message)
        }
    }
}

@Composable
private fun MessageBubble(message: MockMessage) {
    val isMine = message.isMine
    val isVerified = message.isVerified

    val bubbleColor = when {
        isVerified -> Color(0xFF0A2B3D)
        isMine -> Color(0xFF25D366)
        else -> Color(0xFF202C33)
    }
    val textColor = when {
        isMine -> Color.White
        else -> Color(0xFFE9EDEF)
    }
    val cornerShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = if (isMine) 12.dp else 2.dp,
        bottomEnd = if (isMine) 2.dp else 12.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = cornerShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (isVerified) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "\uD83D\uDD12",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Verified",
                            color = Color(0xFF53BDEC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!isMine && !isVerified) {
                    Text(
                        text = message.sender,
                        color = Color(0xFF53BDEC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.timestamp,
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    if (isMine && message.deliveryStatus != null) {
                        Spacer(modifier = Modifier.width(3.dp))
                        val (icon, iconColor) = when (message.deliveryStatus) {
                            DeliveryStatus.SENT -> Icons.Filled.Check to textColor.copy(alpha = 0.5f)
                            DeliveryStatus.DELIVERED -> Icons.Filled.DoneAll to Color(0xFF53BDEC)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
