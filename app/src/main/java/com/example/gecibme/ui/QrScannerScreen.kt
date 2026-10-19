package com.example.gecibme.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gecibme.ReceivedQrEvent
import com.example.gecibme.network.QrApiService
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs

sealed interface ServerUploadStatus {
    object Idle : ServerUploadStatus
    object Sending : ServerUploadStatus
    data class Success(val id: Int, val scannedAt: String) : ServerUploadStatus
    data class Error(val message: String) : ServerUploadStatus
}

@Composable
fun QrScannerScreen(
    modifier: Modifier = Modifier,
    username: String? = null,
    password: String? = null,
    initialServerHost: String = "https://csalas.onrender.com",
    externalReceivedQrEvent: ReceivedQrEvent? = null,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewScanner(
                username = username,
                password = password,
                initialServerHost = initialServerHost,
                externalReceivedQrEvent = externalReceivedQrEvent,
                onLogout = onLogout
            )
        } else {
            PermissionDeniedContent(
                onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) }
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CameraPreviewScanner(
    username: String?,
    password: String?,
    initialServerHost: String,
    externalReceivedQrEvent: ReceivedQrEvent?,
    onLogout: (() -> Unit)?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { QrApiService() }

    var serverUrl by remember { mutableStateOf(initialServerHost) }
    var showServerSettingsDialog by remember { mutableStateOf(false) }

    var scannedResult by remember { mutableStateOf<String?>(null) }
    var uploadStatus by remember { mutableStateOf<ServerUploadStatus>(ServerUploadStatus.Idle) }

    var showAutoLoginDialog by remember { mutableStateOf(false) }
    var urlToAutoLogin by remember { mutableStateOf<String?>(null) }

    var isFlashOn by remember { mutableStateOf(false) }
    var currentZoomRatio by remember { mutableStateOf(1f) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Handle incoming QR code broadcast from SseForegroundService
    LaunchedEffect(externalReceivedQrEvent) {
        val incomingCode = externalReceivedQrEvent?.qrCode
        if (!incomingCode.isNullOrBlank()) {
            val formattedUrl = when {
                URLUtil.isValidUrl(incomingCode) -> incomingCode
                incomingCode.startsWith("www.") || incomingCode.contains(".bme.hu") || incomingCode.contains(".hu") -> "https://$incomingCode"
                else -> null
            }

            scannedResult = incomingCode
            if (formattedUrl != null && !username.isNullOrBlank() && !password.isNullOrBlank()) {
                urlToAutoLogin = formattedUrl
                showAutoLoginDialog = true
            }
        }
    }

    fun sendToServer(text: String) {
        coroutineScope.launch {
            uploadStatus = ServerUploadStatus.Sending
            val result = apiService.sendQrCode(serverUrl, text)
            uploadStatus = result.fold(
                onSuccess = { response -> ServerUploadStatus.Success(response.id, response.scannedAt) },
                onFailure = { error -> ServerUploadStatus.Error(error.message ?: "Ismeretlen hiba") }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    camera?.let { cam ->
                        val zoomState = cam.cameraInfo.zoomState.value
                        val currentZoom = zoomState?.zoomRatio ?: 1f
                        val minZoom = zoomState?.minZoomRatio ?: 1f
                        val maxZoom = (zoomState?.maxZoomRatio ?: 5f).coerceAtMost(8f)
                        val targetZoom = (currentZoom * zoom).coerceIn(minZoom, maxZoom)
                        cam.cameraControl.setZoomRatio(targetZoom)
                        currentZoomRatio = targetZoom
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val executor = ContextCompat.getMainExecutor(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(resolutionSelector)
                        .build()

                    previewView.display?.rotation?.let { rotation ->
                        imageAnalysis.targetRotation = rotation
                    }

                    val analysisExecutor = Executors.newSingleThreadExecutor()
                    imageAnalysis.setAnalyzer(
                        analysisExecutor,
                        QrCodeAnalyzer { resultText ->
                            if (scannedResult == null) {
                                scannedResult = resultText
                                sendToServer(resultText)

                                val formattedUrl = when {
                                    URLUtil.isValidUrl(resultText) -> resultText
                                    resultText.startsWith("www.") || resultText.contains(".bme.hu") || resultText.contains(".hu") -> "https://$resultText"
                                    else -> null
                                }

                                if (formattedUrl != null && !username.isNullOrBlank() && !password.isNullOrBlank()) {
                                    urlToAutoLogin = formattedUrl
                                    showAutoLoginDialog = true
                                }
                            }
                        }
                    )

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        boundCamera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                            if (zoomState != null) {
                                currentZoomRatio = zoomState.zoomRatio
                            }
                        }
                        camera = boundCamera
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent overlay with viewfinder
        ScannerOverlay()

        // Quick Zoom Control Buttons (1x, 2x, 4x)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (scannedResult != null) 300.dp else 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(1f, 2f, 4f).forEach { zoomTarget ->
                val isSelected = abs(currentZoomRatio - zoomTarget) < 0.3f
                IconButton(
                    onClick = {
                        camera?.let { cam ->
                            val zoomState = cam.cameraInfo.zoomState.value
                            val minZoom = zoomState?.minZoomRatio ?: 1f
                            val maxZoom = zoomState?.maxZoomRatio ?: 5f
                            val targetZoom = zoomTarget.coerceIn(minZoom, maxZoom)
                            cam.cameraControl.setZoomRatio(targetZoom)
                            currentZoomRatio = targetZoom
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                            shape = CircleShape
                        )
                ) {
                    Text(
                        text = "${zoomTarget.toInt()}x",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Top control buttons (Server Settings, Logout & Flashlight)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Server URL button
                IconButton(
                    onClick = { showServerSettingsDialog = true },
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                ) {
                    Text(
                        text = "⚙️",
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }

                if (onLogout != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            text = "🚪",
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Flashlight button
            camera?.cameraInfo?.let { cameraInfo ->
                if (cameraInfo.hasFlashUnit()) {
                    IconButton(
                        onClick = {
                            isFlashOn = !isFlashOn
                            camera?.cameraControl?.enableTorch(isFlashOn)
                        },
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            text = if (isFlashOn) "⚡" else "💡",
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Header instruction text
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Irányítsd a kamerát a QR-kódra",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (!username.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "👤 $username",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
        }

        // Scanned result card
        scannedResult?.let { resultText ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Beolvasott eredmény",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ComposeSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Server status banner
                    ServerStatusBanner(
                        status = uploadStatus,
                        onRetry = { sendToServer(resultText) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val isValidUrl = URLUtil.isValidUrl(resultText) || resultText.startsWith("www.") || resultText.contains(".bme.hu") || resultText.contains(".hu")
                    if (isValidUrl && !username.isNullOrBlank() && !password.isNullOrBlank()) {
                        Button(
                            onClick = {
                                val targetUrl = if (resultText.startsWith("http://") || resultText.startsWith("https://")) resultText else "https://$resultText"
                                urlToAutoLogin = targetUrl
                                showAutoLoginDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Automata bejelentkezés a weboldalra 🌐", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(resultText))
                                Toast.makeText(context, "Másolva a vágólapra!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Másolás")
                        }

                        if (isValidUrl) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val targetUrl = if (resultText.startsWith("http://") || resultText.startsWith("https://")) resultText else "https://$resultText"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Nem sikerült megnyitni a hivatkozást", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Megnyitás")
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = {
                                scannedResult = null
                                uploadStatus = ServerUploadStatus.Idle
                                showAutoLoginDialog = false
                                urlToAutoLogin = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Újra")
                        }
                    }
                }
            }
        }
    }

    // Auto-Login WebView Dialog
    if (showAutoLoginDialog && urlToAutoLogin != null && !username.isNullOrBlank() && !password.isNullOrBlank()) {
        AutoLoginWebViewDialog(
            url = urlToAutoLogin!!,
            username = username,
            password = password,
            onDismiss = { showAutoLoginDialog = false }
        )
    }

    // Server Settings Dialog
    if (showServerSettingsDialog) {
        var tempUrl by remember { mutableStateOf(serverUrl) }

        AlertDialog(
            onDismissRequest = { showServerSettingsDialog = false },
            title = {
                Text(
                    text = "Szerver beállítások",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Adja meg a hálózati szerver végpontjának címét (POST JSON):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Szerver URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        serverUrl = tempUrl.trim()
                        showServerSettingsDialog = false
                    }
                ) {
                    Text("Mentés")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showServerSettingsDialog = false }
                ) {
                    Text("Mégse")
                }
            }
        )
    }
}

@Composable
private fun ServerStatusBanner(
    status: ServerUploadStatus,
    onRetry: () -> Unit
) {
    ComposeSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = when (status) {
            ServerUploadStatus.Idle -> MaterialTheme.colorScheme.surfaceVariant
            ServerUploadStatus.Sending -> MaterialTheme.colorScheme.surfaceVariant
            is ServerUploadStatus.Success -> Color(0xFFE8F5E9)
            is ServerUploadStatus.Error -> Color(0xFFFFEBEE)
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (status) {
                ServerUploadStatus.Idle -> {
                    Text(
                        text = "Várakozás küldésre...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ServerUploadStatus.Sending -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Küldés a szerverre...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is ServerUploadStatus.Success -> {
                    Text(
                        text = "✅ Eltárolva a szerveren! (ID: #${status.id})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
                is ServerUploadStatus.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "❌ Hiba a küldés során: ${status.message}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFC62828),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = onRetry) {
                            Text("Újraküldés 🔄", color = Color(0xFFC62828))
                        }
                    }
                }
            }
        }
    }
}

private class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (!rawValue.isNullOrBlank()) {
                            onQrCodeScanned(rawValue)
                            break
                        }
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

@Composable
private fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boxSize = size.width * 0.7f
        val left = (size.width - boxSize) / 2
        val top = (size.height - boxSize) / 2

        // Draw dim background everywhere
        drawRect(
            color = Color.Black.copy(alpha = 0.5f)
        )

        // Clear center transparent rectangle for camera view
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            blendMode = BlendMode.Clear
        )

        // Draw border frame around camera window
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ComposeSurface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "📷",
                    fontSize = 44.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Kamera engedély szükséges",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "A QR-kódok beolvasásához az alkalmazásnak szüksége van a kamera használatára.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Kamera engedélyezése",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
