package com.example.aobot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import java.nio.ByteBuffer

class ScreenshotUtils(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var width = 0
    private var height = 0
    private var density = 0
    private var isInitialized = false

    /**
     * Khởi tạo yêu cầu cấp quyền MediaProjection từ MainActivity.
     */
    fun initMediaProjection(launcher: ActivityResultLauncher<Intent>) {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mpm.createScreenCaptureIntent())
    }

    /**
     * Thiết lập MediaProjection và VirtualDisplay sau khi đã có quyền.
     * Hàm này được gọi từ GameBotService.
     */
    fun setupMediaProjection(resultCode: Int, data: Intent) {
        if (isInitialized) return
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getMetrics(metrics)

        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, ImageFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        isInitialized = true
    }

    /**
     * Chụp ảnh màn hình hiện tại.
     * @return Bitmap của màn hình hoặc null nếu không thể chụp.
     */
    fun takeScreenshot(): Bitmap? {
        if (!isInitialized) {
            return null
        }
        val image = imageReader?.acquireLatestImage() ?: return null
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        // Trả về bitmap đã cắt đúng kích thước
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    /**
     * Giải phóng tài nguyên.
     */
    fun release() {
        if (!isInitialized) return
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        isInitialized = false
    }
}
