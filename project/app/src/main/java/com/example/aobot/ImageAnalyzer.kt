package com.example.aobot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class DetectionResult(val label: String, val score: Float, val location: RectF)

/**
 * Lớp này sử dụng model TFLite để phát hiện các đối tượng trên bitmap.
 *
 * @param interpreter Đối tượng Interpreter đã được khởi tạo với model phát hiện đối tượng.
 */
class ImageAnalyzer(private val interpreter: Interpreter) {

    private val labels = listOf("hero", "enemy", "skill1", "skill2", "skill3", "attack_btn", "minimap")
    private var inputSize = 320

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, false)
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        scaled.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                buffer.putFloat(((value shr 16 and 0xFF) / 255f))
                buffer.putFloat(((value shr 8 and 0xFF) / 255f))
                buffer.putFloat(((value and 0xFF) / 255f))
            }
        }
        return buffer
    }

    fun detectObjects(bitmap: Bitmap, confThreshold: Float = 0.25f): List<DetectionResult> {
        val inputBuffer = bitmapToByteBuffer(bitmap)
        val outputShape = arrayOf(1, 25200, 6)
        val outputBuffer = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
        interpreter.run(inputBuffer, outputBuffer)

        val results = mutableListOf<DetectionResult>()
        for (i in 0 until outputShape[1]) {
            val conf = outputBuffer[0][i][4]
            if (conf > confThreshold) {
                val clsIndex = outputBuffer[0][i][5].toInt()
                val label = if (clsIndex in labels.indices) labels[clsIndex] else "unknown"
                val cx = outputBuffer[0][i][0] * bitmap.width
                val cy = outputBuffer[0][i][1] * bitmap.height
                val w = outputBuffer[0][i][2] * bitmap.width
                val h = outputBuffer[0][i][3] * bitmap.height
                val rect = RectF(
                    max(0f, cx - w / 2),
                    max(0f, cy - h / 2),
                    min(bitmap.width.toFloat(), cx + w / 2),
                    min(bitmap.height.toFloat(), cy + h / 2)
                )
                results.add(DetectionResult(label, conf, rect))
            }
        }
        return results.sortedByDescending { it.score }
    }
}