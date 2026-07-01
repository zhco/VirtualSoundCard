package com.marvis.vs.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLES11Ext
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var camera: Camera? = null
    private var previewSurface: Surface? = null
    private var oesTextureId = 0
    private lateinit var surfaceTexture: SurfaceTexture
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    val previewSize = Size(1920, 1080)

    fun initOesTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTextureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        surfaceTexture = SurfaceTexture(oesTextureId)
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        previewSurface = Surface(surfaceTexture)
        return oesTextureId
    }

    fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture?.addListener({
            try {
                val cameraProvider = cameraProviderFuture?.get() ?: return@addListener
                val preview = Preview.Builder()
                    .setTargetResolution(previewSize)
                    .build()
                    .apply {
                        setSurfaceProvider { request ->
                            previewSurface?.let { surf ->
                                request.provideSurface(surf, cameraExecutor) { result ->
                                    // surface released
                                }
                            }
                        }
                    }
                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun updateTexImage() {
        try { surfaceTexture.updateTexImage() } catch (_: Exception) {}
    }

    fun getOesTextureId() = oesTextureId
    fun getTransformMatrix(mtx: FloatArray) { surfaceTexture.getTransformMatrix(mtx) }

    fun release() {
        previewSurface?.release()
        cameraExecutor.shutdown()
        GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
    }
}
