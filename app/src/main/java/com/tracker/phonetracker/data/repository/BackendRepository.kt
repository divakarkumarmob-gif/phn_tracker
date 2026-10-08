package com.tracker.phonetracker.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.tracker.phonetracker.data.model.LocationPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class BackendRepository(private val context: Context) {

    companion object {
        private const val TAG = "BackendRepository"
        private const val PREFS_NAME = "tracker_settings"
        private const val KEY_RENDER_URL = "render_server_url"
        
        // Default Render service endpoint URL (placeholder / user's deployed app)
        // Can be overridden anytime in SharedPreferences
        const val DEFAULT_RENDER_URL = "https://phn-tracker-backend.onrender.com"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getRenderBaseUrl(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_RENDER_URL, DEFAULT_RENDER_URL) ?: DEFAULT_RENDER_URL
    }

    fun setRenderBaseUrl(url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RENDER_URL, url.trim().removeSuffix("/")).apply()
    }

    /**
     * Sends the current location payload to the Render backend server.
     */
    suspend fun postLocationToBackend(payload: LocationPayload): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getRenderBaseUrl().removeSuffix("/")
        val endpoint = "$baseUrl/api/location"

        try {
            val jsonString = gson.toJson(payload)
            val requestBody = jsonString.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "PhoneTracker-Android/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully synced with Render backend: HTTP ${response.code}")
                Result.success(responseBody)
            } else {
                Log.w(TAG, "Render backend returned status: ${response.code} - $responseBody")
                Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Render backend at $endpoint: ${e.message}")
            Result.failure(e)
        }
    }
}
