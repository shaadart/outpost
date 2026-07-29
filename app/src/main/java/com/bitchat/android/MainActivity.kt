package com.bitchat.android

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.mesh.MeshService
import com.bitchat.android.ui.ChatScreen
import com.bitchat.android.ui.ChatViewModel
import com.bitchat.android.ui.OnboardingScreen
import com.bitchat.android.ui.OrientationAwareActivity
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.utils.PermissionManager
import com.bitchat.android.wifiaware.WifiAwareController
import com.bitchat.android.nostr.PoWPreferenceManager
import com.bitchat.android.services.VerificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : OrientationAwareActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var meshService: BluetoothMeshService
    private lateinit var unifiedMeshService: MeshService
    private var pendingMeshForegroundServiceStart = false
    private var isAppInitialized = false

    private val chatViewModel: ChatViewModel by viewModels { 
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, meshService, unifiedMeshService) as T
            }
        }
    }
    
    private val forceFinishReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (intent.action == com.bitchat.android.util.AppConstants.UI.ACTION_FORCE_FINISH) {
                Log.i("MainActivity", "Received force finish broadcast, closing UI")
                finishAffinity()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.setRecentsScreenshotEnabled(false)
        }
        
        val filter = android.content.IntentFilter(com.bitchat.android.util.AppConstants.UI.ACTION_FORCE_FINISH)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                forceFinishReceiver,
                filter,
                com.bitchat.android.util.AppConstants.UI.PERMISSION_FORCE_FINISH,
                null,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                forceFinishReceiver,
                filter,
                com.bitchat.android.util.AppConstants.UI.PERMISSION_FORCE_FINISH,
                null
            )
        }
        
        if (intent.getBooleanExtra("ACTION_QUIT_APP", false)) {
            Log.d("MainActivity", "Quit request received in onCreate, finishing activity")
            finish()
            return
        }

        com.bitchat.android.service.AppShutdownCoordinator.cancelPendingShutdown()
        enableEdgeToEdge()

        permissionManager = PermissionManager(this)
        startMeshForegroundServiceBestEffort()
        meshService = com.bitchat.android.service.MeshServiceHolder.getOrCreate(applicationContext)
        unifiedMeshService = com.bitchat.android.service.MeshServiceHolder.getUnifiedOrCreate(applicationContext)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val handled = chatViewModel.handleBackPressed()
                if (!handled) {
                    this.isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    this.isEnabled = true
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        
        setContent {
            val prefs: SharedPreferences = remember {
                getSharedPreferences("bitchat_prefs", Context.MODE_PRIVATE)
            }
            var onboardingComplete by remember {
                mutableStateOf(prefs.getBoolean("onboarding_complete", false))
            }

            BitchatTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    if (onboardingComplete) {
                        ChatScreen(viewModel = chatViewModel)
                    } else {
                        OnboardingScreen(
                            onComplete = { displayName ->
                                chatViewModel.completeOnboarding(displayName)
                                onboardingComplete = true
                                // Kick off mesh service + permissions now that user is onboarded
                                lifecycleScope.launch {
                                    checkAndRequestPermissions()
                                }
                            }
                        )
                    }
                }
            }

            // Request permissions after onboarding (or immediately if already complete)
            LaunchedEffect(onboardingComplete) {
                if (onboardingComplete) {
                    checkAndRequestPermissions()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WifiAwareController.running.collect { running ->
                    if (running && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        unifiedMeshService.delegate = chatViewModel
                    }
                }
            }
        }
    }
    
    private suspend fun checkAndRequestPermissions() {
        // Only request permissions if we don't have them
        if (!permissionManager.areAllPermissionsGranted()) {
            val missingPermissions = permissionManager.getMissingPermissions()
            if (missingPermissions.isNotEmpty()) {
                requestPermissions(missingPermissions.toTypedArray(), 1001)
            }
        } else {
            initializeApp()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (permissionManager.areRequiredPermissionsGranted()) {
                initializeApp()
            } else {
                Log.w("MainActivity", "Required permissions not granted")
                // Even if not fully granted, try to start with what we have
                initializeApp()
            }
        }
    }

    private fun initializeApp() {
        if (isAppInitialized) return
        
        Log.d("MainActivity", "Starting app initialization")
        lifecycleScope.launch {
            try {
                PoWPreferenceManager.init(this@MainActivity)
                com.bitchat.android.nostr.LocationNotesInitializer.initialize(this@MainActivity)
                
                unifiedMeshService.delegate = chatViewModel
                unifiedMeshService.startServices()
                startMeshForegroundServiceBestEffort()
                
                Log.d("MainActivity", "Mesh service started successfully")
                
                handleNotificationIntent(intent)
                handleVerificationIntent(intent)
                
                isAppInitialized = true
                Log.d("MainActivity", "App initialization complete")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize app", e)
            }
        }
    }

    private fun startMeshForegroundServiceBestEffort() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            pendingMeshForegroundServiceStart = true
            return
        }

        try {
            com.bitchat.android.service.MeshForegroundService.start(applicationContext)
            pendingMeshForegroundServiceStart = false
        } catch (e: Exception) {
            pendingMeshForegroundServiceStart = true
            Log.w("MainActivity", "Unable to start foreground mesh service; will retry when activity is started", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        if (intent.getBooleanExtra("ACTION_QUIT_APP", false)) {
            Log.d("MainActivity", "Quit request received, finishing activity")
            finish()
            return
        }

        com.bitchat.android.service.AppShutdownCoordinator.cancelPendingShutdown()
        
        if (isAppInitialized) {
            handleNotificationIntent(intent)
            handleVerificationIntent(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        if (pendingMeshForegroundServiceStart) {
            startMeshForegroundServiceBestEffort()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (isAppInitialized) {
            try { unifiedMeshService.delegate = chatViewModel } catch (_: Exception) { }
        }
    }

    override fun onPause() {
        super.onPause()
        // Only set background state if app is fully initialized
        if (isAppInitialized) {
            // Detach UI delegate so the foreground service can own DM notifications while UI is closed
            try { unifiedMeshService.delegate = null } catch (_: Exception) { }
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val shouldOpenPrivateChat = intent.getBooleanExtra(
            com.bitchat.android.ui.NotificationManager.EXTRA_OPEN_PRIVATE_CHAT, 
            false
        )
        
        val shouldOpenGeohashChat = intent.getBooleanExtra(
            com.bitchat.android.ui.NotificationManager.EXTRA_OPEN_GEOHASH_CHAT,
            false
        )
        
        when {
            shouldOpenPrivateChat -> {
                val peerID = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_PEER_ID)
                val senderNickname = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_SENDER_NICKNAME)
                
                if (peerID != null) {
                    Log.d("MainActivity", "Opening private chat with $senderNickname (peerID: $peerID) from notification")
                    
                    // Open the private chat sheet with this peer
                    chatViewModel.showMeshPeerList()
                    chatViewModel.showPrivateChatSheet(peerID)
                    
                    // Clear notifications for this sender since user is now viewing the chat
                    chatViewModel.clearNotificationsForSender(peerID)
                }
            }
            
            shouldOpenGeohashChat -> {
                val geohash = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_GEOHASH)
                
                if (geohash != null) {
                    Log.d("MainActivity", "Opening geohash chat #$geohash from notification")
                    
                    // Switch to the geohash channel - create appropriate geohash channel level
                    val level = when (geohash.length) {
                        7 -> com.bitchat.android.geohash.GeohashChannelLevel.BLOCK
                        6 -> com.bitchat.android.geohash.GeohashChannelLevel.NEIGHBORHOOD
                        5 -> com.bitchat.android.geohash.GeohashChannelLevel.CITY
                        4 -> com.bitchat.android.geohash.GeohashChannelLevel.PROVINCE
                        2 -> com.bitchat.android.geohash.GeohashChannelLevel.REGION
                        else -> com.bitchat.android.geohash.GeohashChannelLevel.CITY // Default fallback
                    }
                    val geohashChannel = com.bitchat.android.geohash.GeohashChannel(level, geohash)
                    val channelId = com.bitchat.android.geohash.ChannelID.Location(geohashChannel)
                    chatViewModel.selectLocationChannel(channelId)
                    
                    // Update current geohash state for notifications
                    chatViewModel.setCurrentGeohash(geohash)
                    
                    // Clear notifications for this geohash since user is now viewing it
                    chatViewModel.clearNotificationsForGeohash(geohash)
                }
            }
        }
    }
    
    private fun handleVerificationIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "bitchat" || uri.host != "verify") return

        chatViewModel.showVerificationSheet()
        val qr = VerificationService.verifyScannedQR(uri.toString())
        if (qr != null) {
            chatViewModel.beginQRVerification(qr)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(forceFinishReceiver)
        } catch (_: Exception) {
            // Receiver might not be registered
        }
    }
}
