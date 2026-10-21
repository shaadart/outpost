package com.bitchat.android.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.bitchat.android.groups.GroupInvite
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit, busy: Boolean) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Invite people to chat over the nearby mesh. Anyone with the invitation can join and invite others.")
                OutlinedTextField(value = name, onValueChange = { name = it.take(50) },
                    label = { Text("Group name") }, singleLine = true, enabled = !busy)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim()) },
            enabled = !busy && GroupInvite.validName(name.trim())) { Text(if (busy) "Creating…" else "Create") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
fun JoinGroupDialog(initialLink: String, onDismiss: () -> Unit, onJoin: (GroupInvite) -> Unit, busy: Boolean) {
    var link by remember(initialLink) { mutableStateOf(initialLink) }
    val invite = remember(link) { GroupInvite.parse(link) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(invite?.let { "Join ${it.name}?" } ?: "Join group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = link, onValueChange = { link = it.take(1024) },
                    label = { Text("Invitation link") }, enabled = !busy,
                    isError = link.isNotBlank() && invite == null,
                    supportingText = { Text(if (link.isNotBlank() && invite == null) "Enter a valid Outpost group invitation." else "Only join invitations from people you trust.") })
                Text("Group members share an encryption key. Anyone holding this link can read and send messages. You need a nearby mesh connection to exchange messages.")
            }
        },
        confirmButton = { TextButton(onClick = { invite?.let(onJoin) }, enabled = invite != null && !busy) {
            Text(if (busy) "Joining…" else "Join group")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
fun GroupInvitationDialog(invite: GroupInvite, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, invite) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val matrix = QRCodeWriter().encode(invite.encode(), BarcodeFormat.QR_CODE, 512, 512)
                val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                android.graphics.Bitmap.createBitmap(pixels, 512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Invite to ${invite.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                bitmap?.let { Image(it.asImageBitmap(), "Group invitation QR", Modifier.fillMaxWidth().height(220.dp)) }
                Text("Keep this invitation private. Anyone with it can read and send group messages.")
                TextButton(onClick = { clipboard.setText(AnnotatedString(invite.encode())) }) { Text("Copy invitation") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, invite.encode())
                }, "Invite to group"))
            }) { Text("Share invitation") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
