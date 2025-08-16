package com.example.aobot

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkHelper {
    private val gson = Gson()

    // OkHttpClient với timeout + logging (tắt logging ở production nếu cần)
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    /**
     * Gọi endpoint worker để lấy URL model mới nhất.
     * Hỗ trợ response JSON có các trường "model_url", "url", hoặc "download".
     * @return URL string hoặc null nếu lỗi.
     */
    suspend fun fetchLatestModelUrl(baseUrl: String, type: String): String? = withContext(Dispatchers.IO) {
        val requestUrl = "$baseUrl/model?type=$type"
        val request = Request.Builder().url(requestUrl).get().build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("NetworkHelper", "fetchLatestModelUrl failed: ${resp.code} ${resp.message}")
                    return@withContext null
                }
                val bodyStr = resp.body?.string()
                if (bodyStr.isNullOrBlank()) return@withContext null
                val map: Map<*, *> = try {
                    gson.fromJson(bodyStr, Map::class.java)
                } catch (e: Exception) {
                    Log.e("NetworkHelper", "JSON parse error: ${e.message}")
                    return@withContext null
                }
                // Hỗ trợ nhiều tên trường khác nhau
                return@withContext (map["model_url"] as? String)
                    ?: (map["url"] as? String)
                    ?: (map["download"] as? String)
            }
        } catch (e: Exception) {
            Log.e("NetworkHelper", "Error fetching model URL for type $type: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Tải file từ url và lưu về file đích. Trả về true nếu thành công.
     */
    suspend fun downloadFile(url: String, file: File): Boolean = withContext(Dispatchers.IO) {
        // Tạo parent nếu cần
        try {
            file.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
        } catch (e: Exception) {
            Log.w("NetworkHelper", "Could not create parent dirs: ${e.message}")
        }

        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("NetworkHelper", "downloadFile failed: ${resp.code} ${resp.message}")
                    return@withContext false
                }
                val body = resp.body ?: return@withContext false
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("NetworkHelper", "Error downloading file: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Gửi state + screenshot (base64) đến worker, trả về key (nếu có).
     */
    suspend fun sendDataToWorker(baseUrl: String, state: String, screenshotBase64: String): String? = withContext(Dispatchers.IO) {
        val json = gson.toJson(mapOf("state" to state, "screenshot" to screenshotBase64))
        val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$baseUrl/data").post(requestBody).build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("NetworkHelper", "sendDataToWorker failed: ${resp.code} ${resp.message}")
                    return@withContext null
                }
                val bodyStr = resp.body?.string()
                if (bodyStr.isNullOrBlank()) return@withContext null
                val map: Map<*, *> = try {
                    gson.fromJson(bodyStr, Map::class.java)
                } catch (e: Exception) {
                    Log.e("NetworkHelper", "JSON parse error sendDataToWorker: ${e.message}")
                    return@withContext null
                }
                return@withContext map["key"] as? String
            }
        } catch (e: Exception) {
            Log.e("NetworkHelper", "Error sending data to worker: ${e.message}")
            return@withContext null
        }
    }
}
