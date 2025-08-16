package com.example.aobot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var screenshotUtils: ScreenshotUtils
    private var detectionInterpreter: Interpreter? = null
    private var decisionInterpreter: Interpreter? = null

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

        // Button cấp quyền
        binding.btnGrantAccess.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Vui lòng bật GameBotService", Toast.LENGTH_LONG).show()
        }

        // Button start bot
        binding.btnStartBot.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Chưa bật Accessibility Service", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            screenshotUtils.initMediaProjection(mediaProjectionLauncher)
        }

        loadModels()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + GameBotService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(service) == true
    }

    private fun loadModels() {
        CoroutineScope(Dispatchers.IO).launch {
            val options = Interpreter.Options()
            val compatList = CompatibilityList()
            if (compatList.isGpuDelegateAvailable) {
                options.addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
            }

            // Giả lập file model trong internal storage
            val detectionModelFile = File(filesDir, "detection_model.tflite")
            val decisionModelFile = File(filesDir, "decision_model.tflite")

            detectionInterpreter = Interpreter(detectionModelFile, options)
            decisionInterpreter = Interpreter(decisionModelFile, options)

            runOnUiThread {
                Toast.makeText(this@MainActivity, "Models đã được load thành công", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        detectionInterpreter?.close()
        decisionInterpreter?.close()
        super.onDestroy()
    }
}
