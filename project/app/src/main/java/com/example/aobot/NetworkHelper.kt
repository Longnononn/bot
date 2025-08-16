// NetworkHelper.kt
package com.example.aobot

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object NetworkHelper {

    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun fetchLatestModelUrl(baseUrl: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/model").get().build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val jsonResponse = gson.fromJson(response.body?.string(), Map::class.java)
            return@withContext jsonResponse["model_url"] as? String
        } catch (e: Exception) {
            Log.e("NetworkHelper", "Error fetching model URL: ${e.message}")
            return@withContext null
        }
    }

    suspend fun downloadFile(url: String, file: File): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false
            FileOutputStream(file).use { outputStream ->
                response.body?.byteStream()?.copyTo(outputStream)
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("NetworkHelper", "Error downloading file: ${e.message}")
            return@withContext false
        }
    }

    suspend fun sendDataToWorker(baseUrl: String, state: String, screenshotBase64: String): String? = withContext(Dispatchers.IO) {
        val json = gson.toJson(mapOf("state" to state, "screenshot" to screenshotBase64))
        val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$baseUrl/data").post(requestBody).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext response.message
            val jsonResponse = gson.fromJson(response.body?.string(), Map::class.java)
            return@withContext jsonResponse["key"] as? String
        } catch (e: Exception) {
            Log.e("NetworkHelper", "Error sending data to worker: ${e.message}")
            return@withContext e.message
        }
    }
}