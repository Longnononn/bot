package com.example.aobot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.aobot.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var screenshotUtils: ScreenshotUtils

    // Interpreters (CPU-only here to avoid GPU compile-time issues on CI)
    private var detectionInterpreter: Interpreter? = null
    private var decisionInterpreter: Interpreter? = null

    // Base URL cho Cloudflare Worker (thay <your-cloudflare-id> bằng ID thật)
    private val workerBaseUrl = "https://bot-learning-server.<your-cloudflare-id>.workers.dev"

    // Activity result launcher cho MediaProjection
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Thiết lập media projection trong ScreenshotUtils
            screenshotUtils.setupMediaProjection(result.resultCode, result.data!!)
            Toast.makeText(this, "Bot đã sẵn sàng và đang chạy!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Quyền chụp màn hình bị từ chối", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        screenshotUtils = ScreenshotUtils(this)

        // Nút mở trang Accessibility settings (btnGrantAccess)
        binding.btnGrantAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Vui lòng bật GameBotService", Toast.LENGTH_LONG).show()
        }

        // Nút bắt đầu bot (btnStartBot)
        binding.btnStartBot.setOnClickListener {
            // Kiểm tra accessibility service
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Chưa bật Accessibility Service", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Nếu models đã load thì request quyền chụp màn hình
            if (detectionInterpreter != null && decisionInterpreter != null) {
                screenshotUtils.initMediaProjection(mediaProjectionLauncher)
            } else {
                Toast.makeText(this, "Models đang được tải, vui lòng chờ...", Toast.LENGTH_SHORT).show()
            }
        }

        // Bắt đầu tải models (async)
        loadModels()
    }

    /**
     * Kiểm tra xem Accessibility service có được bật cho package này không.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${GameBotService::class.java.canonicalName}"
        val enabledServices = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: Exception) {
            null
        }
        return enabledServices?.contains(service) == true
    }

    /**
     * Tải model từ worker và khởi tạo Interpreter (CPU).
     * Sử dụng Coroutine để chạy I/O ở background.
     */
    private fun loadModels() {
        CoroutineScope(Dispatchers.Main).launch {
            binding.btnStartBot.isEnabled = false
            binding.btnStartBot.text = "Đang tải models..."

            val success = withContext(Dispatchers.IO) {
                try {
                    // Lấy URL model từ Worker (suspend)
                    val detectionUrl = NetworkHelper.fetchLatestModelUrl(workerBaseUrl, "detection")
                    val decisionUrl = NetworkHelper.fetchLatestModelUrl(workerBaseUrl, "decision")

                    if (detectionUrl.isNullOrBlank() || decisionUrl.isNullOrBlank()) {
                        Log.e("MainActivity", "Model URL missing (detection:$detectionUrl, decision:$decisionUrl)")
                        return@withContext false
                    }

                    // Lưu file model vào internal storage
                    val detectionFile = File(filesDir, "detection_model.tflite")
                    val decisionFile = File(filesDir, "decision_model.tflite")

                    val ok1 = NetworkHelper.downloadFile(detectionUrl, detectionFile)
                    val ok2 = NetworkHelper.downloadFile(decisionUrl, decisionFile)

                    if (!ok1 || !ok2) {
                        Log.e("MainActivity", "Download failed ok1=$ok1 ok2=$ok2")
                        return@withContext false
                    }

                    // Ensure files exist and are non-empty
                    if (!detectionFile.exists() || detectionFile.length() == 0L || !decisionFile.exists() || decisionFile.length() == 0L) {
                        Log.e("MainActivity", "Downloaded model files invalid")
                        return@withContext false
                    }

                    // Tạo Interpreter (CPU only) — tránh tham chiếu tới API GPU gây lỗi compile trên CI
                    detectionInterpreter = Interpreter(detectionFile)
                    decisionInterpreter = Interpreter(decisionFile)

                    return@withContext true
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loadModels: ${e.message}", e)
                    return@withContext false
                }
            }

            if (success) {
                Toast.makeText(this@MainActivity, "Models đã được tải và load thành công", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Tải models thất bại — kiểm tra Worker hoặc mạng", Toast.LENGTH_LONG).show()
            }

            binding.btnStartBot.isEnabled = true
            binding.btnStartBot.text = "Bắt đầu Bot"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            detectionInterpreter?.close()
        } catch (e: Exception) { /* ignore */ }
        try {
            decisionInterpreter?.close()
        } catch (e: Exception) { /* ignore */ }
        screenshotUtils.release()
    }
}
