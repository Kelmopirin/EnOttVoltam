package com.example.gecibme.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gecibme.MainActivity
import com.example.gecibme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class SseForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite read timeout for long-lived SSE
        .build()

    private var serverHost: String = ""
    private var isListening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra(EXTRA_SERVER_HOST)
        if (!host.isNullOrBlank()) {
            serverHost = sanitizeHostUrl(host)
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
            if (!isListening) {
                isListening = true
                startSseListening()
            }
        }

        return START_STICKY
    }

    private fun startSseListening() {
        serviceScope.launch {
            while (isActive && isListening) {
                val eventsUrl = if (serverHost.endsWith("/events")) {
                    serverHost
                } else if (serverHost.endsWith("/")) {
                    "${serverHost}events"
                } else {
                    "$serverHost/events"
                }

                try {
                    val request = Request.Builder()
                        .url(eventsUrl)
                        .header("Accept", "text/event-stream")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful && response.body != null) {
                            val inputStream = response.body!!.byteStream()
                            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

                            var line: String? = null
                            while (isActive && reader.readLine().also { line = it } != null) {
                                val currentLine = line?.trim() ?: continue
                                if (currentLine.startsWith("data:")) {
                                    val jsonPayload = currentLine.removePrefix("data:").trim()
                                    if (jsonPayload.isNotEmpty()) {
                                        try {
                                            val json = JSONObject(jsonPayload)
                                            val qrCode = json.optString("qr_code", "")
                                            if (qrCode.isNotBlank()) {
                                                onQrCodeReceived(qrCode)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // If disconnected or error, wait 3 seconds before retrying reconnect
                if (isActive) {
                    delay(3000)
                }
            }
        }
    }

    private fun onQrCodeReceived(qrCode: String) {
        // 1. Broadcast event for active MainActivity
        val broadcastIntent = Intent(ACTION_QR_RECEIVED).apply {
            putExtra(EXTRA_QR_CODE, qrCode)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        // 2. Trigger high priority system notification
        showQrReceivedNotification(qrCode)
    }

    private fun showQrReceivedNotification(qrCode: String) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_QR_CODE, qrCode)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_EVENT_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_EVENTS_ID)
            .setSmallIcon(R.mipmap.gajdos)
            .setContentTitle("Új QR-kód érkezett!")
            .setContentText(qrCode)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_EVENT_ID, notification)
    }

    private fun createForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.mipmap.gajdos)
            .setContentTitle("ÉnOttVoltam Szolgáltatás")
            .setContentText("Csatlakozva a szerverhez (SSE)...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "SSE Háttérszolgáltatás",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aktív kapcsolat a QR szerverrel"
            }

            val eventsChannel = NotificationChannel(
                CHANNEL_EVENTS_ID,
                "QR Értesítések",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Értesítés szerverről érkező QR-kódokról"
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(eventsChannel)
        }
    }

    private fun stopForegroundService() {
        isListening = false
        serviceJob.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        serviceJob.cancel()
    }

    private fun sanitizeHostUrl(host: String): String {
        var clean = host.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean
    }

    companion object {
        const val ACTION_START = "com.example.gecibme.service.ACTION_START"
        const val ACTION_STOP = "com.example.gecibme.service.ACTION_STOP"
        const val ACTION_QR_RECEIVED = "com.example.gecibme.service.ACTION_QR_RECEIVED"

        const val EXTRA_SERVER_HOST = "extra_server_host"
        const val EXTRA_QR_CODE = "extra_qr_code"

        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val NOTIFICATION_EVENT_ID = 2002

        private const val CHANNEL_SERVICE_ID = "sse_service_channel"
        private const val CHANNEL_EVENTS_ID = "sse_events_channel"

        fun start(context: Context, serverHost: String) {
            val intent = Intent(context, SseForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_HOST, serverHost)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SseForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
