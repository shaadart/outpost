#!/bin/bash
cat << 'INNEREOF' > patch_vars.diff
--- app/src/main/java/com/bitchat/android/ui/ChatScreen.kt
+++ app/src/main/java/com/bitchat/android/ui/ChatScreen.kt
@@ -122,12 +122,6 @@
         currentChannel != null -> true
         else -> selectedLocationChannel !is com.bitchat.android.geohash.ChannelID.Location
     }
-    
-    val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
-    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
-    val unreadChannelMessages by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
-    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
-    val peerRSSI by viewModel.peerRSSI.collectAsStateWithLifecycle()
 
     // Use WindowInsets to handle keyboard properly
     Box(
@@ -174,7 +168,7 @@
                     item {
                         PeopleSection(
                             modifier = Modifier.padding(top = 16.dp),
-                            connectedPeers = connectedPeers.keys.toList(),
+                            connectedPeers = connectedPeers,
                             peerNicknames = peerNicknames,
                             peerRSSI = peerRSSI,
                             colorScheme = colorScheme,
INNEREOF
patch -p0 < patch_vars.diff
