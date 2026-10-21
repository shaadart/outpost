package com.bitchat.android.screenshots

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.MainActivity
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.service.MeshServiceHolder
import com.bitchat.android.services.AppStateStore
import com.bitchat.android.ui.ChatScreen
import com.bitchat.android.ui.ChatState
import com.bitchat.android.ui.ChatViewModel
import com.bitchat.android.ui.theme.OutpostTheme
import java.io.File
import java.util.Date

/**
 * Explicitly invoked documentation fixture, never a release entry point or a Bluetooth test.
 * Run only on a disposable emulator with -e demo true. No message is sent over a transport.
 */
class DemoScreenshotInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        check(arguments?.getString("demo") == "true") { "Pass -e demo true for documentation capture" }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Use a disposable emulator, never a personal phone" }
        start()
    }

    override fun onStart() {
        try {
            check(!targetContext.getSharedPreferences("bitchat_prefs", 0)
                .getBoolean("onboarding_complete", false)) { "Use a fresh disposable emulator profile" }
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val deadline = System.currentTimeMillis() + 20_000
            while (!AppStateStore.historyReady.value && System.currentTimeMillis() < deadline) Thread.sleep(100)
            check(AppStateStore.historyReady.value) { "Chat history did not initialize" }
            check(AppStateStore.privateMessages.value.isEmpty() && AppStateStore.publicMessages.value.isEmpty() &&
                AppStateStore.channelMessages.value.isEmpty()) { "Use empty disposable chat storage" }

            lateinit var state: ChatState
            runOnMainSync {
                // The real production screen is rendered with sample state. No transport starts.
                val mesh = MeshServiceHolder.getOrCreate(targetContext)
                val model = ChatViewModel(activity.application, mesh, MeshServiceHolder.getUnifiedOrCreate(targetContext))
                state = ChatViewModel::class.java.getDeclaredField("state").let {
                    it.isAccessible = true
                    it.get(model) as ChatState
                }
                val maya = "1111222233334444"
                val jon = "aaaabbbbccccdddd"
                val now = System.currentTimeMillis()
                fun message(sender: String, text: String, peer: String, secondsAgo: Int) = BitchatMessage(
                    sender = sender, content = text, timestamp = Date(now - secondsAgo * 1000L),
                    senderPeerID = peer, isPrivate = true, isEncrypted = true
                )
                state.setNickname("Alex")
                state.setPeerNicknames(mapOf(maya to "Maya", jon to "Jon"))
                AppStateStore.setPeers(listOf(maya, jon))
                AppStateStore.addPrivateMessage(maya, message("Maya", "Are we still meeting at ten?", maya, 180))
                AppStateStore.addPrivateMessage(maya, message("Alex", "Yes. North gate, by the big tree.", mesh.myPeerID, 140))
                AppStateStore.addPrivateMessage(maya, message("Maya", "Perfect. I will bring coffee.", maya, 100))
                AppStateStore.addPrivateMessage(maya, message("Alex", "Then I am definitely coming.", mesh.myPeerID, 60))
                AppStateStore.addPrivateMessage(jon, message("Jon", "See you both there!", jon, 210))
                activity.setContent {
                    OutpostTheme(darkTheme = true) {
                        Column(Modifier.fillMaxSize().background(Color(0xFF0B0F14)).statusBarsPadding()) {
                            Box(Modifier.fillMaxWidth().background(Color(0xFF1E3A5F)).padding(8.dp),
                                contentAlignment = Alignment.Center) {
                                Text("DEMO · sample people and messages", color = Color.White, fontSize = 12.sp)
                            }
                            Box(Modifier.weight(1f)) { ChatScreen(model) }
                        }
                    }
                }
            }

            capture("nearby-demo.png", "Maya")
            runOnMainSync { state.setSelectedPrivateChatPeer("1111222233334444") }
            capture("chat-demo.png", "Then I am definitely coming.")
            runOnMainSync { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "Demo screenshots captured; sample peers only.\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", error.stackTraceToString()) })
        }
    }

    private fun capture(name: String, expected: String) {
        val deadline = System.currentTimeMillis() + 20_000
        var ready = false
        while (System.currentTimeMillis() < deadline) {
            val root = uiAutomation.rootInActiveWindow
            fun contains(node: android.view.accessibility.AccessibilityNodeInfo?, text: String): Boolean {
                if (node == null) return false
                if (node.text?.contains(text) == true) return true
                return (0 until node.childCount).any { contains(node.getChild(it), text) }
            }
            if (contains(root, expected) && contains(root, "2 Peers Nearby") && contains(root, "DEMO")) {
                ready = true
                break
            }
            Thread.sleep(200)
        }
        check(ready) { "The labeled demo screen did not settle" }
        Thread.sleep(700)
        val bitmap = checkNotNull(uiAutomation.takeScreenshot()) { "Screenshot capture failed" }
        val file = File(targetContext.filesDir, "documentation/$name").apply { parentFile?.mkdirs() }
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        sendStatus(0, Bundle().apply { putString("stream", "Captured $name\n") })
    }
}
