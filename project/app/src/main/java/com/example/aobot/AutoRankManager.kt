package com.example.aobot

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class AutoRankManager(
    private val context: Context,
    private val detectionInterpreter: Interpreter,
    private val decisionInterpreter: Interpreter,
    private val screenshotUtils: ScreenshotUtils,
    private val networkHelper: NetworkHelper
) {

    private val detector = TFLiteDetector(detectionInterpreter)
    private val decisionMaker = DecisionMaker(decisionInterpreter)
    private val gson = Gson()
    private val workerBaseUrl = "https://bot-learning-server.<your-cloudflare-id>.workers.dev"

    private var bgHandler: Handler? = null
    private var isRunning = false

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                try {
                    val screenshot = screenshotUtils.takeScreenshot()
                    if (screenshot != null) {
                        val detections = detector.detectObjects(screenshot)
                        val state = extractGameState(detections)
                        
                        val action = decisionMaker.getAction(state)
                        
                        executeAction(action, detections)
                        
                        // Gửi dữ liệu cho mô hình học nếu cần
                        // sendDataToWorker(state, screenshot)
                        
                        screenshot.recycle()
                    } else {
                        Log.e("AutoRankManager", "Không thể chụp màn hình.")
                    }
                } catch (e: Exception) {
                    Log.e("AutoRankManager", "Lỗi trong vòng lặp bot: ${e.message}")
                }
                bgHandler?.postDelayed(this, 100) // Lặp lại sau 100ms
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        val handlerThread = HandlerThread("AutoRankThread")
        handlerThread.start()
        bgHandler = Handler(handlerThread.looper)
        bgHandler?.post(loopRunnable)
        Log.d("AutoRankManager", "Bot loop started.")
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        bgHandler?.removeCallbacks(loopRunnable)
        bgHandler?.looper?.quitSafely()
        Log.d("AutoRankManager", "Bot loop stopped.")
    }
    
    /**
     * Trích xuất thông tin game từ các đối tượng được phát hiện để tạo trạng thái.
     */
    private fun extractGameState(detections: List<DetectionResult>): Map<String, Any> {
        val hero = detections.firstOrNull { it.label == "hero" }
        val enemy = detections.firstOrNull { it.label == "enemy" }
        
        // Tạo một map với các giá trị giả định hoặc trích xuất từ detections
        // Cần thay thế bằng logic thực tế để lấy các giá trị này từ màn hình
        return mapOf(
            "hero_health" to 1.0f,
            "hero_mana" to 0.8f,
            "hero_location_x" to (hero?.location?.centerX() ?: 0f),
            "hero_location_y" to (hero?.location?.centerY() ?: 0f),
            "enemy_closest_distance" to if (enemy != null) {
                // Tính khoảng cách giữa hero và enemy
                Math.sqrt(
                    Math.pow((hero!!.location.centerX() - enemy.location.centerX()).toDouble(), 2.0) +
                    Math.pow((hero.location.centerY() - enemy.location.centerY()).toDouble(), 2.0)
                ).toFloat()
            } else 1000f,
            "enemy_count" to detections.count { it.label == "enemy" }.toFloat(),
            "tower_closest_distance" to 500f, // Giá trị giả định
            "is_pushing" to 0f, // Giá trị giả định
            "is_retreating" to 0f, // Giá trị giả định
            "game_state" to 0f // Giá trị giả định
        )
    }

    private fun executeAction(action: String, detections: List<DetectionResult>) {
        when (action) {
            "attack_tower" -> {
                Log.d("AutoRankManager", "Action: attack_tower")
                // TODO: Triển khai logic tấn công tháp
            }
            "cast_skill_1" -> {
                // Ví dụ: Tìm vị trí nút skill và gọi performTap
                val skill1Btn = detections.firstOrNull { it.label == "skill1" }
                if (skill1Btn != null) {
                    GameBotService.performTap(skill1Btn.location.centerX(), skill1Btn.location.centerY())
                    Log.d("AutoRankManager", "Action: Sử dụng skill 1")
                }
            }
            else -> {
                val enemy = detections.firstOrNull { it.label == "enemy" }
                if (enemy != null) {
                    GameBotService.performTap(enemy.location.centerX(), enemy.location.centerY())
                    Log.d("AutoRankManager", "Action: Tấn công kẻ địch tại (${enemy.location.centerX()}, ${enemy.location.centerY()})")
                }
            }
        }
    }
    
    private fun sendDataToWorker(state: Map<String, Any>, screenshot: Bitmap) {
        val screenshotBase64 = encodeBitmapToBase64(screenshot)
        val stateJson = gson.toJson(state)

        CoroutineScope(Dispatchers.IO).launch {
            val key = networkHelper.sendDataToWorker(workerBaseUrl, stateJson, screenshotBase64)
            if (key != null) {
                Log.d("AutoRankManager", "Dữ liệu đã được gửi thành công với key: $key")
            } else {
                Log.e("AutoRankManager", "Gửi dữ liệu thất bại")
            }
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
