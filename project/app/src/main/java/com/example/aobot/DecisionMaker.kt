package com.example.aobot

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lớp này chịu trách nhiệm sử dụng model TFLite để đưa ra quyết định hành động.
 *
 * @param interpreter Đối tượng Interpreter đã được khởi tạo với model.
 */
class DecisionMaker(private val interpreter: Interpreter) {

    /**
     * Dựa trên trạng thái hiện tại của game, sử dụng model TFLite để dự đoán hành động tốt nhất.
     *
     * @param state Trạng thái của game, ví dụ: vị trí hero, máu, mana, v.v.
     * @return Tên của hành động được đề xuất.
     */
    fun getAction(state: Map<String, Any>): String {
        val inputBuffer = convertStateToByteBuffer(state)

        // Khai báo output buffer. Kích thước phụ thuộc vào output của model.
        // Giả định model có 10 lớp đầu ra, mỗi lớp tương ứng với một hành động.
        val outputBuffer = Array(1) { FloatArray(10) }

        // Chạy inference trên TFLite interpreter.
        interpreter.run(inputBuffer, outputBuffer)

        // Danh sách các hành động có thể có. Phải khớp với output của model.
        val actions = listOf(
            "move_lane", "roam", "farm", "cast_skill_1", "cast_skill_2",
            "cast_ultimate", "attack_tower", "retreat", "combo", "push"
        )
        val probabilities = outputBuffer[0]

        // Tìm chỉ số của hành động có xác suất cao nhất.
        val bestActionIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0

        // Trả về tên của hành động tốt nhất.
        return actions[bestActionIndex]
    }
    
    /**
     * Chuyển đổi Map<String, Any> thành ByteBuffer để làm đầu vào cho TFLite model.
     * Hàm này phải được điều chỉnh để khớp với định dạng đầu vào cụ thể của model.
     *
     * @param state Dữ liệu đầu vào.
     * @return ByteBuffer chứa dữ liệu đã được chuẩn hóa.
     */
    private fun convertStateToByteBuffer(state: Map<String, Any>): ByteBuffer {
        // Giả định input của model là một tensor 1D với 10 giá trị Float.
        val inputSize = 10
        val buffer = ByteBuffer.allocateDirect(inputSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // Khai báo một mảng các key theo đúng thứ tự mà mô hình mong đợi
        val sortedKeys = listOf(
            "hero_health", "hero_mana", "hero_location_x", "hero_location_y",
            "enemy_closest_distance", "enemy_count", "tower_closest_distance",
            "is_pushing", "is_retreating", "game_state"
        )
        
        // Đẩy các giá trị vào buffer theo đúng thứ tự đã xác định
        for (key in sortedKeys) {
            val value = state[key]
            when (value) {
                is Float -> buffer.putFloat(value)
                is Double -> buffer.putFloat(value.toFloat())
                is Int -> buffer.putFloat(value.toFloat())
                else -> {
                    // Xử lý trường hợp giá trị không hợp lệ hoặc thiếu
                    Log.w("DecisionMaker", "Giá trị cho key '$key' không hợp lệ: $value")
                    buffer.putFloat(0.0f) // Sử dụng giá trị mặc định
                }
            }
        }
        
        buffer.rewind()
        return buffer
    }
}
