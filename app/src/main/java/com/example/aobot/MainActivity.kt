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
import java.io.File

class MainActivity : AppCompatActivity() {

    private var interpreter: Interpreter? = null
    // Thay thế bằng URL Worker thực tế của bạn
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

        screenshotUtils = ScreenshotUtils(this)

        findViewById<Button>(R.id.btnGrantAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStartBot).setOnClickListener {
            if ((application as GameBotApp).getInterpreter() == null) {
                Toast.makeText(this, "Model chưa tải xong. Vui lòng đợi.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Yêu cầu quyền MediaProjection để chụp màn hình
            screenshotUtils.initMediaProjection(mediaProjectionLauncher)
        }

        fetchAndLoadModel()
    }

    private fun fetchAndLoadModel() {
        CoroutineScope(Dispatchers.IO).launch {
            val modelUrl = NetworkHelper.fetchLatestModelUrl(workerBaseUrl)
            if (modelUrl.isNullOrEmpty()) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Không lấy được URL model", Toast.LENGTH_LONG).show() }
                return@launch
            }

            val modelFile = File(filesDir, "pro_decision_model.tflite")
            val success = NetworkHelper.downloadFile(modelUrl, modelFile)

            runOnUiThread {
                if (success) {
                    interpreter?.close()
                    try {
                        interpreter = Interpreter(modelFile)
                        (application as GameBotApp).setInterpreter(interpreter!!)
                        Toast.makeText(this@MainActivity, "Model loaded thành công", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Lỗi khi load model: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Tải model thất bại", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter?.close()
    }
}