package com.example.aobot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var detectionInterpreter: Interpreter? = null
    private var decisionInterpreter: Interpreter? = null
    private val workerBaseUrl = "https://bot-learning-server.<your-cloudflare-id>.workers.dev"
    private lateinit var screenshotUtils: ScreenshotUtils
    private lateinit var startBotButton: Button

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

        // Sửa lỗi: Khởi tạo ScreenshotUtils với context
        screenshotUtils = ScreenshotUtils(this)

        // Sửa lỗi: Ánh xạ view startBotButton
        startBotButton = findViewById(R.id.startBotButton)

        startBotButton.setOnClickListener {
            if (detectionInterpreter != null && decisionInterpreter != null) {
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
        
        // Bắt đầu tải các models khi Activity được tạo
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
            // Sửa lỗi: Khởi tạo GPU delegate đúng cách
            val compatList = CompatibilityList()
            val options = Interpreter.Options()
            if (compatList.isGpuDelegateAvailable) {
                options.addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
            } else {
                // Xử lý khi GPU không có sẵn
                Log.w("MainActivity", "GPU không được hỗ trợ trên thiết bị này.")
            }

            // Tải mô hình phát hiện đối tượng
            val detectionModelUrl = NetworkHelper.fetchLatestModelUrl(workerBaseUrl, "detection")
            val detectionModelFile = File(filesDir, "detection_model.tflite")
            
            // Sửa lỗi: Thêm kiểm tra null trước khi tải xuống
            val successDetection = detectionModelUrl?.let { NetworkHelper.downloadFile(it, detectionModelFile) } ?: false

            // Tải mô hình ra quyết định
            val decisionModelUrl = NetworkHelper.fetchLatestModelUrl(workerBaseUrl, "decision")
            val decisionModelFile = File(filesDir, "decision_model.tflite")

            // Sửa lỗi: Thêm kiểm tra null trước khi tải xuống
            val successDecision = decisionModelUrl?.let { NetworkHelper.downloadFile(it, decisionModelFile) } ?: false

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
                        Log.e("MainActivity", "Error loading models: ${e.message}")
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
