package com.bitchat.android.ui

import android.graphics.Bitmap
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.services.VerificationService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

private const val TAG = "ActionMenuSheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionMenuSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit,
    onScanQR: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onNewChat: () -> Unit,
    onShareApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            ActionMenuItem(
                icon = Icons.Filled.Person,
                title = "New chat",
                subtitle = "Message someone nearby",
                onClick = { onDismiss(); onNewChat() }
            )
            ActionMenuItem(
                icon = Icons.Filled.Person,
                title = "Profile",
                subtitle = "Change name / View my QR",
                onClick = {
                    onDismiss()
                    onOpenProfile()
                }
            )

            ActionMenuItem(
                icon = Icons.Outlined.QrCodeScanner,
                title = "Scan QR",
                subtitle = "Verify a person or join a group",
                onClick = {
                    onDismiss()
                    onScanQR()
                }
            )

            ActionMenuItem(
                icon = Icons.Outlined.Campaign,
                title = "New group",
                subtitle = "Chat together using a private invitation",
                onClick = {
                    onDismiss()
                    onCreateGroup()
                }
            )

            ActionMenuItem(
                icon = Icons.Outlined.QrCodeScanner,
                title = "Join group",
                subtitle = "Paste an invitation link",
                onClick = { onDismiss(); onJoinGroup() }
            )
            ActionMenuItem(
                icon = Icons.Outlined.Share,
                title = "Share App Offline",
                subtitle = "Send this app to nearby devices",
                onClick = {
                    onDismiss()
                    onShareApp()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF1A2330),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerPanel(
    isActive: Boolean,
    onScanResult: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraProvider by produceState<ProcessCameraProvider?>(null) {
        cameraProviderFuture.addListener({ value = cameraProviderFuture.get() }, ContextCompat.getMainExecutor(context))
    }
    val analyzing = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var lastCode by remember { mutableStateOf<String?>(null) }
    val currentOnScanResult by rememberUpdatedState(onScanResult)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val surfaceRequests = remember { MutableStateFlow<SurfaceRequest?>(null) }
    val surfaceRequest by surfaceRequests.collectAsState(initial = null)

    Column(modifier = modifier) {
        if (cameraPermission.status.isGranted && cameraProvider != null) {
            val executor = remember { Executors.newSingleThreadExecutor() }

            DisposableEffect(cameraProvider) {
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val barcodeScanner = BarcodeScanning.getClient(options)

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider { request -> surfaceRequests.value = request }
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                    @androidx.camera.core.ExperimentalGetImage
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && analyzing.compareAndSet(false, true)) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                                if (value != null && value != lastCode) {
                                    lastCode = value
                                    currentOnScanResult(value)
                                }
                            }
                            .addOnCompleteListener {
                                analyzing.set(false)
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                runCatching {
                    cameraProvider!!.unbindAll()
                    cameraProvider!!.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }

                onDispose {
                    analysis.clearAnalyzer()
                    barcodeScanner.close()
                    executor.shutdown()
                    runCatching { cameraProvider!!.unbindAll() }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                surfaceRequest?.let { request ->
                    CameraXViewfinder(
                        surfaceRequest = request,
                        implementationMode = ImplementationMode.EMBEDDED,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else if (!cameraPermission.status.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A2330)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Camera permission required to scan QR codes",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { cameraPermission.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6)
                        )
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Cancel Scan")
        }
    }
}
