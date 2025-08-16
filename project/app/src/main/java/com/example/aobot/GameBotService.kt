package com.example.aobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.tensorflow.lite.Interpreter

class GameBotService : AccessibilityService() {

    private lateinit var autoRank: AutoRankManager
    private lateinit var screenshotUtils: ScreenshotUtils

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode != 0 && data != null) {
            val decisionInterpreter = (application as GameBotApp).getDecisionInterpreter()
            val detectionInterpreter = (application as GameBotApp).getDetectionInterpreter()

            if (detectionInterpreter != null && decisionInterpreter != null) {
                // Khởi tạo ScreenshotUtils với Context của Service
                screenshotUtils = ScreenshotUtils(this)
                screenshotUtils.setupMediaProjection(resultCode, data)

                // Khởi tạo AutoRankManager với các Interpreter và phụ thuộc đầy đủ
                autoRank = AutoRankManager(
                    this,
                    detectionInterpreter,
                    decisionInterpreter,
                    screenshotUtils,
                    NetworkHelper
                )
                autoRank.start()
            } else {
                Log.e("GameBotService", "Interpreters không được khởi tạo, bot sẽ không chạy.")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GameBotService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Có thể thêm logic xử lý sự kiện Accessibility tại đây
    }

    override fun onInterrupt() {
        Log.w("GameBotService", "Bot bị ngắt")
        if (::autoRank.isInitialized) {
            autoRank.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::autoRank.isInitialized) {
            autoRank.stop()
        }
        if (::screenshotUtils.isInitialized) {
            screenshotUtils.release()
        }
        Log.d("GameBotService", "Bot Service đã bị hủy.")
    }

    companion object {
        private var instance: GameBotService? = null

        fun getInstance(): GameBotService? = instance
        
        fun performTap(x: Float, y: Float) {
            getInstance()?.let { service ->
                val path = Path()
                path.moveTo(x, y)
                val gestureBuilder = GestureDescription.Builder()
                val gestureDescription = gestureBuilder.addStroke(
                    GestureDescription.StrokeDescription(path, 0, 10)
                ).build()
                service.dispatchGesture(gestureDescription, null, null)
                Log.d("GameBotService", "Thực hiện tap tại ($x, $y)")
            } ?: Log.e("GameBotService", "Service instance is null. Cannot perform tap.")
        }
    }
}
