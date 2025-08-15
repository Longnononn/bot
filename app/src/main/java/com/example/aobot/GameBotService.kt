package com.example.aobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.tensorflow.lite.Interpreter

class GameBotService : AccessibilityService() {

    private lateinit var autoRank: AutoRankManager
    private var interpreter: Interpreter? = null
    private lateinit var screenshotUtils: ScreenshotUtils

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        
        if (resultCode != 0 && data != null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = mpm.getMediaProjection(resultCode, data)
            screenshotUtils = ScreenshotUtils(this, mediaProjection)
            
            interpreter = (application as GameBotApp).getInterpreter()
            
            if (interpreter != null) {
                autoRank = AutoRankManager(this, interpreter!!, screenshotUtils)
                autoRank.start()
            } else {
                Log.e("GameBotService", "Interpreter không được khởi tạo, bot sẽ không chạy.")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GameBotService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.w("GameBotService", "Bot bị ngắt")
        autoRank.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoRank.stop()
    }

    companion object {
        fun performTap(x: Float, y: Float) {
            // ... (Logic thực hiện hành động)
        }
    }
}