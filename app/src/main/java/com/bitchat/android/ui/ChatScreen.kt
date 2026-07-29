package com.bitchat.android.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.geohash.ChannelID
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.services.VerificationService
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

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
    val selectedLocationChannel by viewModel.selectedLocationChannel.collectAsStateWithLifecycle()

    var currentView by remember { mutableStateOf(ChatViewState.INBOX) }
    var selectedConvId by remember { mutableStateOf<String?>(null) }
    var selectedConvIsPrivate by remember { mutableStateOf(false) }
    var isOfficialUpdates by remember { mutableStateOf(false) }
    var isBroadcastConv by remember { mutableStateOf(false) }
    var showPanicWipe by remember { mutableStateOf(false) }
    var showLocationChannelsSheet by remember { mutableStateOf(false) }
    var showProfileSettingsSheet by remember { mutableStateOf(false) }
    var showActionMenuSheet by remember { mutableStateOf(false) }
    var showBroadcastCreationDialog by remember { mutableStateOf(false) }
    var showBroadcastQRSheet by remember { mutableStateOf(false) }
    var showScannerPanel by remember { mutableStateOf(false) }
    var newBroadcastChannelId by remember { mutableStateOf("") }
    var newBroadcastChannelName by remember { mutableStateOf("") }
    var broadcastQRBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var messageText by remember { mutableStateOf(TextFieldValue("")) }

    val inboxItems =
            remember(
                    publicMessages,
                    privateChats,
                    channelMessages,
                    joinedChannels,
                    unreadChannelMessages,
                    unreadPrivateMessages,
                    peerNicknames
            ) {
                val items = mutableListOf<InboxItem>()

                if ("official_updates" in joinedChannels) {
                    val msgs = channelMessages["official_updates"] ?: emptyList()
                    val last = msgs.lastOrNull()
                    items.add(
                            InboxItem(
                                    id = "official_updates",
                                    title = "\uD83D\uDCE2 CJP Official Updates",
                                    subtitle = last?.content ?: "No messages yet",
                                    timestamp = last?.let { timeFormatter.format(it.timestamp) }
                                                    ?: "",
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
                                timestamp = lastPublic?.let { timeFormatter.format(it.timestamp) }
                                                ?: "",
                                unreadCount = 0,
                                isPinned = false,
                                isPrivate = false
                        )
                )

                joinedChannels.forEach { channelId ->
                    if (channelId.startsWith("#broadcast_")) {
                        val msgs = channelMessages[channelId] ?: emptyList()
                        val last = msgs.lastOrNull()
                        val title =
                                channelId
                                        .removePrefix("#broadcast_")
                                        .split("_")
                                        .drop(1)
                                        .joinToString(" ")
                                        .replaceFirstChar { it.uppercase() }
                                        .ifEmpty { "Broadcast Channel" }
                        items.add(
                                InboxItem(
                                        id = channelId,
                                        title = "\uD83D\uDCE2 $title",
                                        subtitle =
                                                last?.let {
                                                    if (it.senderPeerID == viewModel.myPeerID ||
                                                                    it.sender == nickname
                                                    )
                                                            "You: ${it.content}"
                                                    else it.content
                                                }
                                                        ?: "No messages yet",
                                        timestamp = last?.let { timeFormatter.format(it.timestamp) }
                                                        ?: "",
                                        unreadCount = unreadChannelMessages[channelId] ?: 0,
                                        isPinned = false,
                                        isPrivate = false
                                )
                        )
                    }
                }

                privateChats.forEach { (peerId, msgs) ->
                    val last = msgs.lastOrNull()
                    val displayName = peerNicknames[peerId] ?: peerId
                    items.add(
                            InboxItem(
                                    id = peerId,
                                    title = displayName,
                                    subtitle =
                                            last?.let {
                                                if (it.senderPeerID == viewModel.myPeerID ||
                                                                it.sender == nickname
                                                )
                                                        "You: ${it.content}"
                                                else it.content
                                            }
                                                    ?: "No messages yet",
                                    timestamp = last?.let { timeFormatter.format(it.timestamp) }
                                                    ?: "",
                                    unreadCount = if (peerId in unreadPrivateMessages) 1 else 0,
                                    isPinned = false,
                                    isPrivate = true
                            )
                    )
                }
                items.toList()
            }

    val displayMessages: List<BitchatMessage> =
            when {
                selectedConvId == null -> emptyList()
                isOfficialUpdates -> channelMessages["official_updates"] ?: emptyList()
                isBroadcastConv && selectedConvId != null -> channelMessages[selectedConvId]
                                ?: emptyList()
                selectedConvIsPrivate -> privateChats[selectedConvId] ?: emptyList()
                selectedLocationChannel is ChannelID.Location -> {
                    val geoKey =
                            "geo:${(selectedLocationChannel as ChannelID.Location).channel.geohash}"
                    channelMessages[geoKey] ?: emptyList()
                }
                else -> publicMessages
            }

    val isReadOnly =
            isOfficialUpdates ||
                    (isBroadcastConv &&
                            selectedConvId != null &&
                            !viewModel.isChannelCreator(selectedConvId!!))

    BackHandler(enabled = currentView == ChatViewState.CHAT) {
        if (selectedConvIsPrivate && selectedConvId != null) {
            viewModel.endPrivateChat()
        }
        if (isOfficialUpdates || isBroadcastConv) {
            viewModel.switchToChannel(null)
        }
        if (selectedLocationChannel is ChannelID.Location) {
            viewModel.selectLocationChannel(ChannelID.Mesh)
        }
        currentView = ChatViewState.INBOX
        selectedConvId = null
    }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(colorScheme.background)
                            .windowInsetsPadding(WindowInsets.ime)
    ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            val headerTitle =
                    when {
                        selectedConvId == null -> "Outpost"
                        isOfficialUpdates -> "\uD83D\uDCE2 CJP Official Updates"
                        isBroadcastConv && selectedConvId != null -> {
                            selectedConvId!!
                                    .removePrefix("#broadcast_")
                                    .split("_")
                                    .drop(1)
                                    .joinToString(" ")
                                    .replaceFirstChar { it.uppercase() }
                                    .ifEmpty { "Broadcast Channel" }
                        }
                        selectedConvIsPrivate -> peerNicknames[selectedConvId]
                                        ?: selectedConvId ?: "Chat"
                        selectedLocationChannel is ChannelID.Location -> {
                            val loc = selectedLocationChannel as ChannelID.Location
                            loc.channel.displayName
                        }
                        else -> "\uD83D\uDCCD Local Radius Chatter"
                    }

            MeshChatHeader(
                    showBack = currentView == ChatViewState.CHAT,
                    title = headerTitle,
                    onBack = {
                        if (selectedConvIsPrivate && selectedConvId != null) {
                            viewModel.endPrivateChat()
                        }
                        if (isOfficialUpdates || isBroadcastConv) {
                            viewModel.switchToChannel(null)
                        }
                        if (selectedLocationChannel is ChannelID.Location) {
                            viewModel.selectLocationChannel(ChannelID.Mesh)
                        }
                        currentView = ChatViewState.INBOX
                        selectedConvId = null
                    },
                    onTripleShieldClick = { showPanicWipe = true },
                    peerCount = connectedPeers.size
            )

            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))

            when (currentView) {
                ChatViewState.INBOX -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(items = inboxItems, key = { it.id }) { item ->
                                InboxRow(
                                        item = item,
                                        onClick = {
                                            selectedConvId = item.id
                                            selectedConvIsPrivate = item.isPrivate
                                            isOfficialUpdates = item.id == "official_updates"
                                            isBroadcastConv = item.id.startsWith("#broadcast_")
                                            if (item.isPrivate) {
                                                viewModel.startPrivateChat(item.id)
                                            }
                                            if (item.id == "official_updates" ||
                                                            item.id.startsWith("#broadcast_")
                                            ) {
                                                viewModel.switchToChannel(item.id)
                                            }
                                            currentView = ChatViewState.CHAT
                                        }
                                )
                            }

                            item(key = "discover_zones") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                        onClick = { showLocationChannelsSheet = true },
                                        color = Color(0xFF1A2330),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 4.dp
                                                        )
                                ) {
                                    Row(
                                            modifier =
                                                    Modifier.fillMaxWidth()
                                                            .padding(
                                                                    horizontal = 16.dp,
                                                                    vertical = 14.dp
                                                            ),
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "\uD83D\uDCCD", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                    text = "Discover Local Zones",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF3B82F6)
                                            )
                                            Text(
                                                    text =
                                                            "Join location-based chat zones near you",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color =
                                                            MaterialTheme.colorScheme.onSurface
                                                                    .copy(alpha = 0.6f)
                                            )
                                        }
                                        Text(
                                                text = "\u2192",
                                                color = Color(0xFF3B82F6),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        FloatingActionButton(
                                onClick = { showActionMenuSheet = true },
                                containerColor = Color(0xFF3B82F6),
                                contentColor = Color.White,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        ) { Text(text = "+", fontSize = 28.sp, fontWeight = FontWeight.Light) }
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

        ProfileSettingsSheet(
                isPresented = showProfileSettingsSheet,
                onDismiss = { showProfileSettingsSheet = false },
                viewModel = viewModel
        )

        LocationChannelsSheet(
                isPresented = showLocationChannelsSheet,
                onDismiss = { showLocationChannelsSheet = false },
                viewModel = viewModel
        )

        ActionMenuSheet(
                isPresented = showActionMenuSheet,
                onDismiss = { showActionMenuSheet = false },
                onOpenProfile = { showProfileSettingsSheet = true },
                onScanQR = { showScannerPanel = true },
                onCreateBroadcast = { showBroadcastCreationDialog = true },
                onShareApp = {
                    coroutineScope.launch {
                        com.bitchat.android.utils.AppShareUtils.shareApkOffline(context)
                    }
                }
        )

        // Broadcast channel name prompt
        BroadcastCreationDialog(
                isPresented = showBroadcastCreationDialog,
                onDismiss = { showBroadcastCreationDialog = false },
                onCreate = { name ->
                    showBroadcastCreationDialog = false
                    val channelId = viewModel.createBroadcastChannel(name)
                    if (channelId != null) {
                        newBroadcastChannelId = channelId
                        newBroadcastChannelName = name
                        broadcastQRBitmap = generateBroadcastQR(channelId, name)
                        showBroadcastQRSheet = true
                    }
                }
        )

        // Broadcast channel QR share sheet
        BroadcastQRSheet(
                isPresented = showBroadcastQRSheet,
                onDismiss = { showBroadcastQRSheet = false },
                channelId = newBroadcastChannelId,
                channelName = newBroadcastChannelName,
                qrBitmap = broadcastQRBitmap
        )

        // Scanner panel (for "Scan QR" action)
        if (showScannerPanel) {
            Dialog(
                    onDismissRequest = { showScannerPanel = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
            ) {
                Box(
                        modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF111B21))
                                .systemBarsPadding()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top bar
                        Row(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showScannerPanel = false }) {
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                )
                            }
                            Text(
                                    text = "Scan QR Code",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                            )
                        }

                        // Camera preview with overlay
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            ScannerPanel(
                                    isActive = showScannerPanel,
                                    onScanResult = { code ->
                                        val invite = parseBroadcastInvite(code)
                                        if (invite != null) {
                                            val (channelId, channelName) = invite
                                            viewModel.joinBroadcastChannel(channelId, channelName)
                                            showScannerPanel = false
                                            selectedConvId = channelId
                                            selectedConvIsPrivate = false
                                            isOfficialUpdates = false
                                            isBroadcastConv = true
                                            viewModel.switchToChannel(channelId)
                                            currentView = ChatViewState.CHAT
                                        } else {
                                            val qr = VerificationService.verifyScannedQR(code)
                                            if (qr != null) {
                                                val success = viewModel.beginQRVerification(qr)
                                                if (success) {
                                                    viewModel.showPrivateChatSheet(qr.nickname)
                                                    showScannerPanel = false
                                                } else {
                                                    Log.w(
                                                            "ChatScreen",
                                                            "QR verification failed for ${qr.nickname}"
                                                    )
                                                }
                                            } else {
                                                Log.w("ChatScreen", "Invalid QR code scanned")
                                            }
                                        }
                                    },
                                    onCancel = { showScannerPanel = false },
                                    modifier = Modifier.fillMaxSize()
                            )

                            // Viewfinder overlay
                            Canvas(
                                    modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(alpha = 0.99f)
                            ) {
                                val w = size.width
                                val h = size.height
                                val vfSize = minOf(w, h) * 0.65f
                                val vfLeft = (w - vfSize) / 2f
                                val vfTop = (h - vfSize) / 2f
                                val radius = 24.dp.toPx()

                                drawRect(Color(0x99000000))
                                drawRoundRect(
                                        Color.Transparent,
                                        topLeft = Offset(vfLeft, vfTop),
                                        size = Size(vfSize, vfSize),
                                        cornerRadius = CornerRadius(radius),
                                        blendMode = BlendMode.Clear
                                )

                                val cornerLen = 24.dp.toPx()
                                val strokeW = 3.dp.toPx()
                                val accentColor = Color.White
                                val r = vfLeft + vfSize
                                val b = vfTop + vfSize

                                drawLine(accentColor, Offset(vfLeft, vfTop + cornerLen), Offset(vfLeft, vfTop), strokeW)
                                drawLine(accentColor, Offset(vfLeft, vfTop), Offset(vfLeft + cornerLen, vfTop), strokeW)
                                drawLine(accentColor, Offset(r - cornerLen, vfTop), Offset(r, vfTop), strokeW)
                                drawLine(accentColor, Offset(r, vfTop), Offset(r, vfTop + cornerLen), strokeW)
                                drawLine(accentColor, Offset(vfLeft, b - cornerLen), Offset(vfLeft, b), strokeW)
                                drawLine(accentColor, Offset(vfLeft, b), Offset(vfLeft + cornerLen, b), strokeW)
                                drawLine(accentColor, Offset(r - cornerLen, b), Offset(r, b), strokeW)
                                drawLine(accentColor, Offset(r, b), Offset(r, b - cornerLen), strokeW)
                            }
                        }

                        // Instruction text
                        Text(
                                text = stringResource(R.string.scan_qr_instruction),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }

        // When a location channel is selected via the sheet, dismiss sheet + navigate to chat view
        LaunchedEffect(selectedLocationChannel) {
            if (selectedLocationChannel is ChannelID.Location &&
                            selectedLocationChannel != ChannelID.Mesh
            ) {
                showLocationChannelsSheet = false
                selectedConvId = "local_zone"
                selectedConvIsPrivate = false
                isOfficialUpdates = false
                currentView = ChatViewState.CHAT
            }
        }
    }
}

@Composable
private fun InboxRow(item: InboxItem, onClick: () -> Unit) {
    val bgColor = if (item.isPinned) Color(0xFF1A2330) else Color.Transparent
    val accentColor = if (item.isPinned) Color(0xFF60A5FA) else Color(0xFF3B82F6)

    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = bgColor) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color =
                                if (item.isPinned) Color(0xFF60A5FA)
                                else MaterialTheme.colorScheme.onSurface,
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
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF3B82F6)) {
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

    Surface(modifier = modifier.fillMaxWidth(), color = colorScheme.background) {
        Column {
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .windowInsetsPadding(WindowInsets.ime)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF131A22),
                        modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                            value = messageText,
                            onValueChange = onMessageTextChange,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            textStyle =
                                    MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFFF3F4F6),
                                            fontSize = 16.sp
                                    ),
                            cursorBrush = SolidColor(Color(0xFF3B82F6)),
                            singleLine = false,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (messageText.text.isEmpty()) {
                                        Text(
                                                text = "Message",
                                                color = Color(0xFF94A3B8),
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
                        color = Color(0xFF3B82F6),
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
    Surface(modifier = Modifier.fillMaxWidth(), color = colorScheme.background) {
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
