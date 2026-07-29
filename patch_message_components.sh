#!/bin/bash
cat << 'INNEREOF' > patch.diff
--- app/src/main/java/com/bitchat/android/ui/MessageComponents.kt
+++ app/src/main/java/com/bitchat/android/ui/MessageComponents.kt
@@ -155,27 +155,54 @@
             Row(
                 modifier = Modifier.fillMaxWidth(),
-                horizontalArrangement = Arrangement.Start,
+                horizontalArrangement = if (message.senderPeerID == meshService.myPeerID || message.sender == currentUserNickname || message.sender.startsWith("$currentUserNickname#")) Arrangement.End else Arrangement.Start,
                 verticalAlignment = Alignment.Top
             ) {
+                val isSelf = message.senderPeerID == meshService.myPeerID || message.sender == currentUserNickname || message.sender.startsWith("$currentUserNickname#")
+                val isOfficial = message.sender == "system" || message.isVerified
+                val bubbleColor = if (isSelf) Color(0xFF25D366) else Color(0xFF202C33)
+                val bubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
+                    topStart = 12.dp,
+                    topEnd = 12.dp,
+                    bottomStart = if (isSelf) 12.dp else 2.dp,
+                    bottomEnd = if (isSelf) 2.dp else 12.dp
+                )
+
                 // Provide a small end padding for own private messages so overlay doesn't cover text
                 val endPad = if (message.isPrivate && message.sender == currentUserNickname) 16.dp else 0.dp
-                // Create a custom layout that combines selectable text with clickable nickname areas
-                MessageTextWithClickableNicknames(
-                    message = message,
-                    messages = messages,
-                    currentUserNickname = currentUserNickname,
-                    meshService = meshService,
-                    colorScheme = colorScheme,
-                    timeFormatter = timeFormatter,
-                    onNicknameClick = onNicknameClick,
-                    onMessageLongPress = onMessageLongPress,
-                    onCancelTransfer = onCancelTransfer,
-                    onImageClick = onImageClick,
-                    modifier = Modifier
-                        .weight(1f, fill = false)
-                        .padding(end = endPad)
-                )
+                
+                Surface(
+                    shape = bubbleShape,
+                    color = bubbleColor,
+                    modifier = Modifier
+                        .padding(horizontal = 8.dp, vertical = 2.dp)
+                        .widthIn(max = 280.dp)
+                ) {
+                    Column(modifier = Modifier.padding(8.dp)) {
+                        if (!isSelf && !isOfficial) {
+                            Text(
+                                text = com.bitchat.android.ui.splitSuffix(message.sender).first,
+                                color = com.bitchat.android.ui.getPeerColor(message, true),
+                                fontSize = 12.sp,
+                                fontWeight = FontWeight.Bold,
+                                modifier = Modifier.padding(bottom = 2.dp)
+                            )
+                        }
+                        if (isOfficial && message.isVerified) {
+                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
+                                Text(text = "📢 Official", color = Color(0xFF53BDEC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
+                            }
+                        }
+                        MessageTextWithClickableNicknames(
+                            message = message,
+                            messages = messages,
+                            currentUserNickname = currentUserNickname,
+                            meshService = meshService,
+                            colorScheme = colorScheme,
+                            timeFormatter = timeFormatter,
+                            onNicknameClick = onNicknameClick,
+                            onMessageLongPress = onMessageLongPress,
+                            onCancelTransfer = onCancelTransfer,
+                            onImageClick = onImageClick,
+                            modifier = Modifier.padding(end = endPad)
+                        )
+                    }
+                }
             }
INNEREOF
patch -p0 < patch.diff
