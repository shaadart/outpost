package com.bitchat.android.ui.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.bitchat.android.R
import com.bitchat.android.features.file.FileUtils

@Composable
fun FilePickerButton(
    modifier: Modifier = Modifier,
    onFileReady: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Use SAF - supports all file types
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { FileUtils.copyFileForSending(context, uri) }
                if (!path.isNullOrBlank()) onFileReady(path)
                else android.widget.Toast.makeText(context, "Could not attach file. Maximum size is 96 KB.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    IconButton(
        onClick = {
            // Allow any MIME type; user asked to choose between image or file at higher level UI
            filePicker.launch(arrayOf("*/*"))
        },
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Attachment,
            contentDescription = stringResource(R.string.cd_pick_file),
            tint = Color.Gray,
            modifier = Modifier.size(20.dp).rotate(90f)
        )
    }
}
