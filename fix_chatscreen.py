import sys

file_path = "app/src/main/java/com/bitchat/android/ui/ChatScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# I need to find from "    // Use WindowInsets to handle keyboard properly" to "        // Floating header - positioned absolutely at top, ignores keyboard"
start_str = "    // Use WindowInsets to handle keyboard properly"
end_str = "        // Floating header - positioned absolutely at top, ignores keyboard"

start_idx = content.find(start_str)
end_idx = content.find(end_str)

replacement = """    val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val unreadChannelMessages by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val peerRSSI by viewModel.peerRSSI.collectAsStateWithLifecycle()

    // Use WindowInsets to handle keyboard properly
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background) // Extend background to fill entire screen including status bar
    ) {
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

            if (currentView == ChatViewState.INBOX) {
                // Inbox View - List of channels
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp)
                ) {
                    items(
                        items = joinedChannels.toList().sortedBy { if (it == "official_updates") 0 else 1 },
                        key = { "channel_$it" }
                    ) { channel ->
                        val unreadCount = unreadChannelMessages[channel] ?: 0
                        ChannelRow(
                            channel = channel,
                            isSelected = false,
                            unreadCount = unreadCount,
                            colorScheme = colorScheme,
                            onChannelClick = {
                                viewModel.switchToChannel(channel)
                                currentView = ChatViewState.CHAT
                            },
                            onLeaveChannel = { viewModel.leaveChannel(channel) }
                        )
                    }
                    item {
                        PeopleSection(
                            modifier = Modifier.padding(top = 16.dp),
                            connectedPeers = connectedPeers,
                            colorScheme = colorScheme,
                            viewModel = viewModel,
                            onTapPerson = { peerID ->
                                viewModel.showPrivateChatSheet(peerID)
                            },
                            onToggleFavorite = { peerID ->
                                viewModel.toggleFavorite(peerID)
                            },
                            peerNicknames = peerNicknames,
                            peerRSSI = peerRSSI,
                            nickname = nickname,
                            selectedPrivatePeer = selectedPrivatePeer,
                            onPrivateChatStart = { peerID -> viewModel.showPrivateChatSheet(peerID) }
                        )
                    }
                }
            } else {
                // Messages area - takes up available space, will compress when keyboard appears
                MessagesList(
                    messages = displayMessages,
                    currentUserNickname = nickname,
                    meshService = viewModel.meshServiceFacade,
                    modifier = Modifier.weight(1f),
                    forceScrollToBottom = forceScrollToBottom,
                    onScrolledUpChanged = { isUp -> isScrolledUp = isUp },
                    onNicknameClick = { fullSenderName ->
                        // Single click - mention user in text input
                        val currentText = messageText.text
                        val (baseName, hashSuffix) = splitSuffix(fullSenderName)
                        val selectedLocationChannel = viewModel.selectedLocationChannel.value
                        val mentionText = if (selectedLocationChannel is com.bitchat.android.geohash.ChannelID.Location && hashSuffix.isNotEmpty()) {
                            "@$baseName$hashSuffix"
                        } else {
                            "@$baseName"
                        }
                        
                        val newText = when {
                            currentText.isEmpty() -> "$mentionText "
                            currentText.endsWith(" ") -> "$currentText$mentionText "
                            else -> "$currentText $mentionText "
                        }
                        
                        messageText = TextFieldValue(
                            text = newText,
                            selection = TextRange(newText.length)
                        )
                    },
                    onMessageLongPress = { message ->
                        val (baseName, _) = splitSuffix(message.sender)
                        selectedUserForSheet = baseName
                        selectedMessageForSheet = message
                        showUserSheet = true
                    },
                    onCancelTransfer = { msg ->
                        viewModel.cancelMediaSend(msg.id)
                    },
                    onImageClick = { currentPath, allImagePaths, initialIndex ->
                        viewerImagePaths = allImagePaths
                        initialViewerIndex = initialIndex
                        showFullScreenImageViewer = true
                    }
                )

                // Bridge file share from lower-level input to ViewModel
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    com.bitchat.android.ui.events.FileShareDispatcher.setHandler { peer, channel, path ->
                        viewModel.sendFileNote(peer, channel, path)
                    }
                }

                if (currentChannel != "official_updates") {
                    ChatInputSection(
                        messageText = messageText,
                        onMessageTextChange = { newText: TextFieldValue ->
                            messageText = newText
                            viewModel.updateMentionSuggestions(newText.text)
                        },
                        onSend = {
                            if (messageText.text.trim().isNotEmpty()) {
                                viewModel.sendMessage(messageText.text.trim())
                                messageText = TextFieldValue("")
                                forceScrollToBottom = !forceScrollToBottom // Toggle to trigger scroll
                            }
                        },
                        onSendVoiceNote = { peer, onionOrChannel, path ->
                            viewModel.sendVoiceNote(peer, onionOrChannel, path)
                        },
                        onSendImageNote = { peer, onionOrChannel, path ->
                            viewModel.sendImageNote(peer, onionOrChannel, path)
                        },
                        onSendFileNote = { peer, onionOrChannel, path ->
                            viewModel.sendFileNote(peer, onionOrChannel, path)
                        },
                        
                        showMentionSuggestions = showMentionSuggestions,
                        mentionSuggestions = mentionSuggestions,
                        onMentionSuggestionClick = { mention: String ->
                            val mentionText = viewModel.selectMentionSuggestion(mention, messageText.text)
                            messageText = TextFieldValue(
                                text = mentionText,
                                selection = TextRange(mentionText.length)
                            )
                        },
                        selectedPrivatePeer = null,
                        currentChannel = currentChannel,
                        nickname = nickname,
                        colorScheme = colorScheme,
                        showMediaButtons = showMediaButtons
                    )
                }
            }
        }

"""

new_content = content[:start_idx] + replacement + content[end_idx:]

with open(file_path, "w") as f:
    f.write(new_content)

