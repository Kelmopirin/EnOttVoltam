package com.example.gecibme

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.gecibme.service.SseForegroundService
import com.example.gecibme.ui.LoginScreen
import com.example.gecibme.ui.QrScannerScreen
import com.example.gecibme.ui.theme.GeciBMETheme

data class ReceivedQrEvent(
    val qrCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {

    private var receivedQrEvent = mutableStateOf<ReceivedQrEvent?>(null)

    private val qrReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val qrCode = intent?.getStringExtra(SseForegroundService.EXTRA_QR_CODE)
            if (!qrCode.isNullOrBlank()) {
                receivedQrEvent.value = ReceivedQrEvent(qrCode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Check intent for notification launches
        handleIntent(intent)

        // Request notification permission on Android 13+
        val requestNotificationPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ -> }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            var isLoggedIn by remember {
                mutableStateOf(sharedPrefs.getBoolean("is_logged_in", false))
            }
            var loggedInUsername by remember {
                mutableStateOf(sharedPrefs.getString("logged_in_username", "") ?: "")
            }
            var loggedInPassword by remember {
                mutableStateOf(sharedPrefs.getString("logged_in_password", "") ?: "")
            }
            var serverHost by remember {
                mutableStateOf(sharedPrefs.getString("server_host", "https://csalas.onrender.com") ?: "https://csalas.onrender.com")
            }

            // Start or stop SSE service based on login state
            LaunchedEffect(isLoggedIn, serverHost) {
                if (isLoggedIn && serverHost.isNotBlank()) {
                    SseForegroundService.start(this@MainActivity, serverHost)
                } else {
                    SseForegroundService.stop(this@MainActivity)
                }
            }

            // Register BroadcastReceiver for live QR updates
            DisposableEffect(Unit) {
                val filter = IntentFilter(SseForegroundService.ACTION_QR_RECEIVED)
                ContextCompat.registerReceiver(
                    this@MainActivity,
                    qrReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                onDispose {
                    try {
                        unregisterReceiver(qrReceiver)
                    } catch (_: Exception) {}
                }
            }

            GeciBMETheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLoggedIn) {
                        QrScannerScreen(
                            username = loggedInUsername,
                            password = loggedInPassword,
                            initialServerHost = serverHost,
                            externalReceivedQrEvent = receivedQrEvent.value,
                            onLogout = {
                                SseForegroundService.stop(this@MainActivity)
                                sharedPrefs.edit { clear() }
                                isLoggedIn = false
                                loggedInUsername = ""
                                loggedInPassword = ""
                                serverHost = "https://csalas.onrender.com"
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        LoginScreen(
                            initialServerHost = serverHost,
                            onLoginSuccess = { username, password, host ->
                                sharedPrefs.edit {
                                    putBoolean("is_logged_in", true)
                                    putString("logged_in_username", username)
                                    putString("logged_in_password", password)
                                    putString("server_host", host)
                                }
                                loggedInUsername = username
                                loggedInPassword = password
                                serverHost = host
                                isLoggedIn = true
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val qrCode = intent?.getStringExtra(SseForegroundService.EXTRA_QR_CODE)
        if (!qrCode.isNullOrBlank()) {
            receivedQrEvent.value = ReceivedQrEvent(qrCode)
        }
    }
}
