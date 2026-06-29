package com.marvis.vs.beauty

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.io.FileInputStream
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

/**
 * 人脸检测器 - 基于 TFLite 模型
 * 需要将 face_landmark.tflite 放入 assets/
 * 推荐使用 MediaPipe Face Mesh 的 TFLite 转换版本
 */
class FaceDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var inputWidth = 192
    private var inputHeight = 192
    private var imgData: ByteBuffer? = null

    fun loadModel(path: String = "face_landmark.tflite"): Boolean = try {
        val fd = context.assets.openFd(path)
        val input = FileInputStream(fd.fileDescriptor)
        val model = input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })
        val shape = interpreter?.getInputTensor(0)?.shape()
        if (shape != null && shape.size >= 2) { inputWidth = shape[1]; inputHeight = shape[2] }
        imgData = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3).apply { order(ByteOrder.nativeOrder()) }
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    fun detect(bitmap: Bitmap): BeautyRenderer.FaceLandmarks {
        val result = BeautyRenderer.FaceLandmarks()
        val tflite = interpreter ?: return result
        imgData?.clear()
        val scaled = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, false)
        val pixels = IntArray(inputWidth * inputHeight)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val buf = imgData ?: return result
        for (p in pixels) { buf.put(((p shr 16) and 0xFF).toByte()); buf.put(((p shr 8) and 0xFF).toByte()); buf.put((p and 0xFF).toByte()) }
        buf.flip()
        val output = Array(1) { FloatArray(468 * 3) }
        tflite.run(buf, output)
        val lm = output[0]
        if (lm.isNotEmpty() && lm[0] > -1f) {
            result.detected = true
            var sx = 0f; var sy = 0f; var n = 0
            for (i in lm.indices step 3) { if (lm[i] >= 0) { sx += lm[i]; sy += lm[i+1]; n++ } }
            if (n > 0) { result.faceCenterX = sx/n/inputWidth; result.faceCenterY = sy/n/inputHeight }
            val leIdx = intArrayOf(33,133,159,145); var leX=0f; var leY=0f
            for (i in leIdx) { val id = i*3; if (id+2 < lm.size && lm[id]>=0) { leX+=lm[id]; leY+=lm[id+1] } }
            result.leftEyeX = leX/leIdx.size/inputWidth; result.leftEyeY = leY/leIdx.size/inputHeight
            val reIdx = intArrayOf(362,263,386,374); var reX=0f; var reY=0f
            for (i in reIdx) { val id = i*3; if (id+2 < lm.size && lm[id]>=0) { reX+=lm[id]; reY+=lm[id+1] } }
            result.rightEyeX = reX/reIdx.size/inputWidth; result.rightEyeY = reY/reIdx.size/inputHeight
        }
        scaled.recycle()
        return result
    }

    fun release() { interpreter?.close(); interpreter = null }
}
