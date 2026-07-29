#!/bin/bash
cat << 'INNEREOF' > patch_vars.diff
--- app/src/main/java/com/bitchat/android/ui/ChatScreen.kt
+++ app/src/main/java/com/bitchat/android/ui/ChatScreen.kt
@@ -122,6 +122,12 @@
         currentChannel != null -> true
         else -> selectedLocationChannel !is com.bitchat.android.geohash.ChannelID.Location
     }
+    
+    val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
+    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
+    val unreadChannelMessages by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
+    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
+    val peerRSSI by viewModel.peerRSSI.collectAsStateWithLifecycle()
 
     // Use WindowInsets to handle keyboard properly
     Box(
INNEREOF
patch -p0 < patch_vars.diff
