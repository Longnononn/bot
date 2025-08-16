package com.example.aobot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.aobot.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var detectionInterpreter: Interpreter? = null
    private var decisionInterpreter: Interpreter? = null
    private val workerBaseUrl = "https://bot-learning-server.<your-cloudflare-id>.workers.dev"
    private lateinit var screenshotUtils: ScreenshotUtils

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            screenshotUtils.setupMediaProjection(result.resultCode, result.data!!)
            Toast.makeText(this, "Bot đã sẵn sàng và đang chạy!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Quyền chụp màn hình bị từ chối", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        screenshotUtils = ScreenshotUtils(this)

        binding.btnStartBot.setOnClickListener {
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
            val options = Interpreter.Options()
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val gpuDelegate = GpuDelegate(compatList.getBestOptionsForThisDevice())
                options.addDelegate(gpuDelegate)
            } else {
                Log.w("MainActivity", "GPU không được hỗ trợ trên thiết bị này.")
            }

            val detectionModelFile = File(filesDir, "detection_model.tflite")
            val decisionModelFile = File(filesDir, "decision_model.tflite")

            // Giả sử NetworkHelper đã có phương thức tải model
            val successDetection = NetworkHelper.downloadLatestModel(workerBaseUrl, "detection", detectionModelFile)
            val successDecision = NetworkHelper.downloadLatestModel(workerBaseUrl, "decision", decisionModelFile)

            runOnUiThread {
                if (successDetection && successDecision) {
                    try {
                        detectionInterpreter = Interpreter(detectionModelFile, options)
                        decisionInterpreter = Interpreter(decisionModelFile, options)
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
