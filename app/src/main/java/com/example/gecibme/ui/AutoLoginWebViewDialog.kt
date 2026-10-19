package com.example.gecibme.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import com.example.gecibme.R
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AutoLoginWebViewDialog(
    url: String,
    username: String,
    password: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var currentTitle by remember { mutableStateOf("Betöltés...") }
    var statusMessage by remember { mutableStateOf("Automatikus bejelentkezés folyamatban...") }
    var hasNotifiedSuccess by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar with status and close button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(text = "❌", fontSize = 16.sp)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowContentAccess = true
                                settings.allowFileAccess = true

                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                        super.onPageFinished(view, loadedUrl)
                                        isLoading = false
                                        currentTitle = view?.title ?: "Jelenléti oldal"

                                        val safeUser = JSONObject.quote(username)
                                        val safePass = JSONObject.quote(password)

                                        val autoLoginJs = """
                                            (function() {
                                                try {
                                                    // 1. Click Címtár / Single Sign-On button if present
                                                    var cimtarBtn = document.querySelector('.login-identityprovider-btn');
                                                    if (cimtarBtn) {
                                                        cimtarBtn.click();
                                                        return;
                                                    }

                                                    // 2. Fill login form if username and password fields exist
                                                    var usernameInput = document.getElementById('username');
                                                    var passwordInput = document.getElementById('password');

                                                    if (usernameInput && passwordInput) {
                                                        if (!usernameInput.value) {
                                                            usernameInput.value = $safeUser;
                                                            usernameInput.dispatchEvent(new Event('input', { bubbles: true }));
                                                            usernameInput.dispatchEvent(new Event('change', { bubbles: true }));
                                                        }

                                                        if (!passwordInput.value) {
                                                            passwordInput.value = $safePass;
                                                            passwordInput.dispatchEvent(new Event('input', { bubbles: true }));
                                                            passwordInput.dispatchEvent(new Event('change', { bubbles: true }));
                                                        }

                                                        var proceedBtn = document.querySelector('[name="_eventId_proceed"]');
                                                        if (proceedBtn) {
                                                            proceedBtn.click();
                                                        } else if (usernameInput.form) {
                                                            usernameInput.form.submit();
                                                        }
                                                    }
                                                } catch (e) {
                                                    console.error("Auto login JS error:", e);
                                                }
                                            })();
                                        """.trimIndent()

                                        view?.evaluateJavascript(autoLoginJs, null)

                                        if (loadedUrl != null && loadedUrl.contains("attendance", ignoreCase = true)) {
                                            statusMessage = "✅ Jelenlét rögzítve!"
                                            if (!hasNotifiedSuccess) {
                                                hasNotifiedSuccess = true
                                                Toast.makeText(ctx, "Sikeres bejelentkezés! ✅", Toast.LENGTH_LONG).show()
                                                showSuccessNotification(
                                                    ctx,
                                                    "Sikeres bejelentkezés! ✅",
                                                    "A jelenlét sikeresen rögzítve lett a BME Moodle rendszerében."
                                                )
                                            }
                                        } else if (loadedUrl != null && loadedUrl.contains("idp", ignoreCase = true)) {
                                            statusMessage = "🔑 Bejelentkezés a Címtárba..."
                                        } else {
                                            statusMessage = "Betöltve"
                                        }
                                    }
                                }

                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun showSuccessNotification(context: Context, title: String, message: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "login_success_channel",
            "Bejelentkezési értesítések",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sikeres jelenléti bejelentkezés értesítések"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, "login_success_channel")
        .setSmallIcon(R.mipmap.gajdos)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .build()

    notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
}
