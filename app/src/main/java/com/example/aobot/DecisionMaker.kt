package com.example.aobot

import android.content.Context
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
        // Chuyển đổi Map state thành ByteBuffer để làm input cho TFLite.
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
     * Cần điều chỉnh hàm này để phù hợp với định dạng đầu vào của model cụ thể của bạn.
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

        // Đẩy các giá trị từ map vào buffer theo đúng thứ tự mà model mong đợi.
        state.forEach { (_, value) ->
            when (value) {
                is Float -> buffer.putFloat(value)
                is Boolean -> buffer.putFloat(if (value) 1.0f else 0.0f)
                // Thêm các kiểu dữ liệu khác nếu cần thiết.
            }
        }
        buffer.rewind()
        return buffer
    }
}