package com.example.aobot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var detectionInterpreter: Interpreter? = null
    private var decisionInterpreter: Interpreter? = null
    private val workerBaseUrl = "https://bot-learning-server.<your-cloudflare-id>.workers.dev"
    private lateinit var screenshotUtils: ScreenshotUtils

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, GameBotService::class.java)
            serviceIntent.putExtra("resultCode", result.resultCode)
            serviceIntent.putExtra("data", result.data)
            startService(serviceIntent)
            Toast.makeText(this, "Bot đã sẵn sàng và đang chạy!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Quyền chụp màn hình bị từ chối", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenshotUtils = ScreenshotUtils()

        val startBotButton: Button = findViewById(R.id.startBotButton)
        startBotButton.setOnClickListener {
            // Kiểm tra xem các model đã được load chưa
            if (detectionInterpreter != null && decisionInterpreter != null) {
                // Yêu cầu quyền Accessibility Service
                if (!isAccessibilityServiceEnabled()) {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "Vui lòng bật GameBotService", Toast.LENGTH_LONG).show()
                } else {
                    screenshotUtils.initMediaProjection(mediaProjectionLauncher)
                }
            } else {
                Toast.makeText(this, "Models đang được tải, vui lòng chờ...", Toast.LENGTH_SHORT).show()
            }
        }

        fetchAndLoadModels()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + GameBotService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return settingValue != null && settingValue.contains(service)
        }
        return false
    }

    private fun fetchAndLoadModels() {
        CoroutineScope(Dispatchers.IO).launch {
            // Tải và cấu hình GPU delegate
            val delegate = GpuDelegate(CompatibilityList().bestOptionsForThisDevice)
            val options = Interpreter.Options().addDelegate(delegate)

            // Tải mô hình phát hiện đối tượng
            val detectionModelUrl = NetworkHelper.fetchLatestModelUrl(workerBaseUrl, "detection")
            val detectionModelFile = File(filesDir, "detection_model.tflite")
            val successDetection = NetworkHelper.downloadFile(detectionModelUrl, detectionModelFile)

            // Tải mô hình ra quyết định
            val decisionModelUrl = NetworkHelper.fetchLatestModelUrl(workerBaseUrl, "decision")
            val decisionModelFile = File(filesDir, "decision_model.tflite")
            val successDecision = NetworkHelper.downloadFile(decisionModelUrl, decisionModelFile)

            runOnUiThread {
                if (successDetection && successDecision) {
                    try {
                        detectionInterpreter = Interpreter(detectionModelFile, options)
                        decisionInterpreter = Interpreter(decisionModelFile, options)
                        (application as GameBotApp).setDetectionInterpreter(detectionInterpreter!!)
                        (application as GameBotApp).setDecisionInterpreter(decisionInterpreter!!)
                        Toast.makeText(this@MainActivity, "Cả hai models đã được load thành công", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Lỗi khi load models: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Tải một hoặc cả hai models thất bại", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detectionInterpreter?.close()
        decisionInterpreter?.close()
    }
}
