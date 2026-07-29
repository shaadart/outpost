import sys

file_path = "app/src/main/java/com/bitchat/android/ui/ChatScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

inbox_code = """
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
                        val unreadCount = hasUnreadChannels[channel] ?: 0
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
                            connectedPeers = connectedPeers.values.toList(),
                            colorScheme = colorScheme,
                            viewModel = viewModel,
                            onTapPerson = { peerID ->
                                viewModel.showPrivateChatSheet(peerID)
                            },
                            onToggleFavorite = { peerID ->
                                viewModel.toggleFavorite(peerID)
                            }
                        )
                    }
                }
            } else {
"""

old_target = """            // Messages area - takes up available space, will compress when keyboard appears"""

new_content = content.replace(old_target, inbox_code + "                // Messages area - takes up available space, will compress when keyboard appears")

# We also need to close the else block after the ChatInputSection
# Let's find ChatInputSection end
old_target2 = """                }
            )
        }
    }

    // Floating header rendered on TOP of the content area"""

new_target2 = """                }
            )
        }
            }
        }
    }

    // Floating header rendered on TOP of the content area"""

new_content = new_content.replace(old_target2, new_target2)

with open(file_path, "w") as f:
    f.write(new_content)

