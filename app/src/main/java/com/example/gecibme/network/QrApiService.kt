package com.example.gecibme.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SendQrResponse(
    val id: Int,
    val qrCode: String,
    val scannedAt: String
)

class QrApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun sendQrCode(serverUrl: String, qrCode: String): Result<SendQrResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("qr_code", qrCode)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""

            if (response.isSuccessful || response.code == 201) {
                val jsonResponse = JSONObject(responseBodyString)
                val id = jsonResponse.optInt("id", -1)
                val scannedAt = jsonResponse.optString("scanned_at", "")
                Result.success(SendQrResponse(id = id, qrCode = qrCode, scannedAt = scannedAt))
            } else {
                val errorMessage = try {
                    JSONObject(responseBodyString).optString("error", "Hiba kód: ${response.code}")
                } catch (_: Exception) {
                    "Szerver válasz hiba (HTTP ${response.code})"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Nem sikerült kapcsolódni a szerverhez"))
        }
    }
}
