package com.example.aobot

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import android.util.Base64

class AutoRankManager(
    private val context: Context,
    private val detectionInterpreter: Interpreter, // Thêm Interpreter cho model phát hiện
    private val decisionInterpreter: Interpreter,  // Thêm Interpreter cho model ra quyết định
    private val screenshotUtils: ScreenshotUtils,
    private val networkHelper: NetworkHelper
) {

    private val detector = TFLiteDetector(detectionInterpreter)
    private val decisionMaker = DecisionMaker(decisionInterpreter)
    private val gson = Gson()
    private val workerBaseUrl = "https://bot-learning-server.<your-cloudflare-id>.workers.dev"

    private var bgHandler: Handler? = null
    private var isRunning = false

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
        isRunning = false
        bgHandler?.removeCallbacksAndMessages(null)
        Log.d("AutoRankManager", "Bot loop stopped.")
    }

    private val loopRunnable = object : Runnable {
        override fun run() {
            try {
                val bmp: Bitmap? = screenshotUtils.takeScreenshot()
                bmp?.let {
                    val detections = detector.detectObjects(it)
                    val state = mapState(detections, it.width, it.height)
                    val action = decisionMaker.getAction(state)
                    
                    performAction(action, detections)
                    
                    // Gửi dữ liệu lên Worker (tùy chọn)
                    sendDataToWorker(state, it)

                    it.recycle()
                }
            } catch (e: Exception) {
                Log.e("AutoRankManager", "Loop error: ${e.message}")
            }
            if (isRunning) bgHandler?.postDelayed(this, 50)
        }
    }

    private fun mapState(detections: List<DetectionResult>, w: Int, h: Int) = mapOf<String, Any>(
        "hero_pos_x" to w/2f,
        "hp" to 1f,
        "mana" to 1f,
        "enemies_minimap" to detections.any { it.label=="enemy" }
    )

    private fun performAction(action: String, detections: List<DetectionResult>) {
        when (action) {
            "attack_tower" -> {
                // Ví dụ: Tìm vị trí tháp địch và gọi performTap
                Log.d("AutoRankManager", "Action: attack_tower")
            }
            "cast_skill_1" -> {
                // Ví dụ: Tím vị trí nút skill và gọi performTap
                Log.d("AutoRankManager", "Action: cast_skill_1")
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }
}