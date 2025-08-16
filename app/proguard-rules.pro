# Giữ tên class/method của TensorFlow Lite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# Giữ tên class/method của OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Giữ tên class/method của Tesseract OCR
-keep class com.googlecode.tesseract.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**
-keep class cz.adaptech.tesseract4android.** { *; }
-dontwarn cz.adaptech.tesseract4android.**

# Giữ tên resource id để Accessibility Service đọc ViewId
-keepclassmembers class ** {
    public static <fields>;
}

# Không tối ưu hằng số để tránh lỗi khi load mô hình AI
-dontoptimize
-dontpreverify

# Giữ annotation
-keepattributes *Annotation*

# Giữ class native (JNI)
-keepclasseswithmembernames class * {
    native <methods>;
}
