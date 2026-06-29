package com.marvis.vs.camera

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLES11Ext
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraController(private val lifecycleOwner: LifecycleOwner) {
    private var camera: Camera? = null
    private var previewSurface: Surface? = null
    private var oesTextureId = 0
    private lateinit var surfaceTexture: SurfaceTexture

    val previewSize = Size(1920, 1080)

    fun init(executor: java.util.concurrent.Executor = Executors.newSingleThreadExecutor()): Int {
        // 创建OES纹理
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTextureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        surfaceTexture = SurfaceTexture(oesTextureId)
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        previewSurface = Surface(surfaceTexture)

        val cameraProvider = ProcessCameraProvider.getInstance(lifecycleOwner as android.content.Context).get()
        val preview = Preview.Builder()
            .setTargetResolution(previewSize)
            .build()
            .apply { setSurfaceProvider { previewSurface?.let { it1 -> it.provideSurface(it1, executor) {} } } }

        val selector = CameraSelector.DEFAULT_FRONT_CAMERA
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return oesTextureId
    }

    fun updateTexImage() { surfaceTexture.updateTexImage() }
    fun getOesTextureId() = oesTextureId
    fun getTransformMatrix(mtx: FloatArray) { surfaceTexture.getTransformMatrix(mtx) }

    fun release() {
        previewSurface?.release()
        GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
    }
}

