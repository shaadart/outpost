#!/bin/bash
cat << 'INNEREOF' > patch_chatscreen.diff
--- app/src/main/java/com/bitchat/android/ui/ChatScreen.kt
+++ app/src/main/java/com/bitchat/android/ui/ChatScreen.kt
@@ -28,6 +28,12 @@
 import com.bitchat.android.model.BitchatMessage
 import com.bitchat.android.ui.media.FullScreenImageViewer
 
+enum class ChatViewState {
+    INBOX,
+    CHAT
+}
+
 /**
  * Main ChatScreen - REFACTORED to use component-based architecture
  * This is now a coordinator that orchestrates the following UI components:
@@ -62,6 +68,7 @@
     var showSecurityVerificationSheet by viewModel.showSecurityVerificationSheet.collectAsStateWithLifecycle()
     var showProfileSettingsSheet by remember { mutableStateOf(false) }
 
+    var currentView by remember { mutableStateOf(ChatViewState.INBOX) }
     var messageText by remember { mutableStateOf(TextFieldValue("")) }
     var showPasswordPrompt by remember { mutableStateOf(false) }
     var showPasswordDialog by remember { mutableStateOf(false) }
@@ -88,6 +95,11 @@
     // Get location channel info for timeline switching
     val selectedLocationChannel by viewModel.selectedLocationChannel.collectAsStateWithLifecycle()
 
+    // Handle back button to return to Inbox
+    androidx.activity.compose.BackHandler(enabled = currentView == ChatViewState.CHAT) {
+        currentView = ChatViewState.INBOX
+    }
+
     // Determine what messages to show based on current context (unified timelines)
     // Legacy private chat timeline removed - private chats now exclusively use PrivateChatSheet
     val displayMessages = when {
@@ -118,85 +130,102 @@
         val headerHeight = 42.dp
         
         // Main content area that responds to keyboard/window insets
         Column(
             modifier = Modifier
                 .fillMaxSize()
                 .windowInsetsPadding(WindowInsets.ime) // This handles keyboard insets
                 .windowInsetsPadding(WindowInsets.navigationBars) // Add bottom padding when keyboard is not expanded
         ) {
             // Header spacer - creates exact space for the floating header (status bar + compact header)
             Spacer(
                 modifier = Modifier
                     .windowInsetsPadding(WindowInsets.statusBars)
                     .height(headerHeight)
             )
 
-            // Messages area - takes up available space, will compress when keyboard appears
-            MessagesList(
-                messages = displayMessages,
-                currentUserNickname = nickname,
-                meshService = viewModel.meshServiceFacade,
-                modifier = Modifier.weight(1f),
-                forceScrollToBottom = forceScrollToBottom,
-                onScrolledUpChanged = { isUp -> isScrolledUp = isUp },
-                onNicknameClick = { fullSenderName ->
-                    // Single click - mention user in text input
-                    val currentText = messageText.text
-                    
-                    // Extract base nickname and hash suffix from full sender name
-                    val (baseName, hashSuffix) = splitSuffix(fullSenderName)
-                    
-                    // Check if we're in a geohash channel to include hash suffix
-                    val selectedLocationChannel = viewModel.selectedLocationChannel.value
-                    val mentionText = if (selectedLocationChannel is com.bitchat.android.geohash.ChannelID.Location && hashSuffix.isNotEmpty()) {
-                        // In geohash chat - include the hash suffix from the full display name
-                        "@$baseName$hashSuffix"
-                    } else {
-                        // Regular chat - just the base nickname
-                        "@$baseName"
-                    }
-                    
-                    val newText = when {
-                        currentText.isEmpty() -> "$mentionText "
-                        currentText.endsWith(" ") -> "$currentText$mentionText "
-                        else -> "$currentText $mentionText "
-                    }
-                    
-                    messageText = TextFieldValue(
-                        text = newText,
-                        selection = TextRange(newText.length)
-                    )
-                },
-                onMessageLongPress = { message ->
-                    // Message long press - open user action sheet with message context
-                    // Extract base nickname from message sender (contains all necessary info)
-                    val (baseName, _) = splitSuffix(message.sender)
-                    selectedUserForSheet = baseName
-                    selectedMessageForSheet = message
-                    showUserSheet = true
-                },
-                onCancelTransfer = { msg ->
-                    viewModel.cancelMediaSend(msg.id)
-                },
-                onImageClick = { currentPath, allImagePaths, initialIndex ->
-                    viewerImagePaths = allImagePaths
-                    initialViewerIndex = initialIndex
-                    showFullScreenImageViewer = true
-                }
-            )
-            // Input area - stays at bottom
-        // Bridge file share from lower-level input to ViewModel
-    androidx.compose.runtime.LaunchedEffect(Unit) {
-        com.bitchat.android.ui.events.FileShareDispatcher.setHandler { peer, channel, path ->
-            viewModel.sendFileNote(peer, channel, path)
+            if (currentView == ChatViewState.INBOX) {
+                // Inbox View - List of channels
+                LazyColumn(
+                    modifier = Modifier.weight(1f).fillMaxWidth(),
+                    contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp)
+                ) {
+                    items(
+                        items = joinedChannels.toList().sortedBy { if (it == "official_updates") 0 else 1 },
+                        key = { "channel_$it" }
+                    ) { channel ->
+                        val unreadCount = viewModel.unreadChannelMessages.value[channel] ?: 0
+                        ChannelRow(
+                            channel = channel,
+                            isSelected = false,
+                            unreadCount = unreadCount,
+                            colorScheme = colorScheme,
+                            onChannelClick = {
+                                viewModel.switchToChannel(channel)
+                                currentView = ChatViewState.CHAT
+                            },
+                            onLeaveChannel = { viewModel.leaveChannel(channel) }
+                        )
+                    }
+                    item {
+                        PeopleSection(
+                            modifier = Modifier.padding(top = 16.dp),
+                            connectedPeers = connectedPeers.values.toList(),
+                            colorScheme = colorScheme,
+                            viewModel = viewModel,
+                            onTapPerson = { peerID ->
+                                viewModel.showPrivateChatSheet(peerID)
+                            },
+                            onToggleFavorite = { peerID ->
+                                viewModel.toggleFavorite(peerID)
+                            }
+                        )
+                    }
+                }
+            } else {
+                // Chat View - Messages and Input
+                MessagesList(
+                    messages = displayMessages,
+                    currentUserNickname = nickname,
+                    meshService = viewModel.meshServiceFacade,
+                    modifier = Modifier.weight(1f),
+                    forceScrollToBottom = forceScrollToBottom,
+                    onScrolledUpChanged = { isUp -> isScrolledUp = isUp },
+                    onNicknameClick = { fullSenderName ->
+                        val currentText = messageText.text
+                        val (baseName, hashSuffix) = splitSuffix(fullSenderName)
+                        val selectedLocationChannel = viewModel.selectedLocationChannel.value
+                        val mentionText = if (selectedLocationChannel is com.bitchat.android.geohash.ChannelID.Location && hashSuffix.isNotEmpty()) "@$baseName$hashSuffix" else "@$baseName"
+                        val newText = when {
+                            currentText.isEmpty() -> "$mentionText "
+                            currentText.endsWith(" ") -> "$currentText$mentionText "
+                            else -> "$currentText $mentionText "
+                        }
+                        messageText = TextFieldValue(text = newText, selection = TextRange(newText.length))
+                    },
+                    onMessageLongPress = { message ->
+                        val (baseName, _) = splitSuffix(message.sender)
+                        selectedUserForSheet = baseName
+                        selectedMessageForSheet = message
+                        showUserSheet = true
+                    },
+                    onCancelTransfer = { msg -> viewModel.cancelMediaSend(msg.id) },
+                    onImageClick = { currentPath, allImagePaths, initialIndex ->
+                        viewerImagePaths = allImagePaths
+                        initialViewerIndex = initialIndex
+                        showFullScreenImageViewer = true
+                    }
+                )
+
+                // Bridge file share from lower-level input to ViewModel
+                androidx.compose.runtime.LaunchedEffect(Unit) {
+                    com.bitchat.android.ui.events.FileShareDispatcher.setHandler { peer, channel, path ->
+                        viewModel.sendFileNote(peer, channel, path)
+                    }
+                }
+
+                if (currentChannel != "official_updates") {
+                    ChatInputSection(
+                        messageText = messageText,
+                        onMessageTextChange = { newText: TextFieldValue ->
+                            messageText = newText
+                            viewModel.updateMentionSuggestions(newText.text)
INNEREOF
patch -p0 < patch_chatscreen.diff
