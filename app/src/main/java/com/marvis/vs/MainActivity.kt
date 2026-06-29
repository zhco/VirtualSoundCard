package com.marvis.vs

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.marvis.vs.audio.AudioEngine
import com.marvis.vs.beauty.BeautyRenderer
import com.marvis.vs.beauty.FaceDetector
import com.marvis.vs.camera.CameraController
import com.marvis.vs.recorder.MediaRecorder
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity() {

    private lateinit var glSurface: GLSurfaceView
    private lateinit var btnRecord: Button
    private lateinit var seekSmooth: SeekBar
    private lateinit var seekWhiten: SeekBar
    private lateinit var seekThin: SeekBar
    private lateinit var seekBigEye: SeekBar
    private lateinit var switchNoiseGate: Switch
    private lateinit var switchReverb: Switch
    private lateinit var switchDucker: Switch
    private lateinit var switchPitch: Switch

    private lateinit var beautyRenderer: BeautyRenderer
    private lateinit var faceDetector: FaceDetector
    private lateinit var cameraController: CameraController
    private var mediaRecorder: MediaRecorder? = null

    private var isRecording = false
    private var oesTexId = 0

    companion object {
        private const val REQ_PERM = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        checkPermissions()
    }

    private fun bindViews() {
        glSurface = findViewById(R.id.glSurface)
        btnRecord = findViewById(R.id.btnRecord)
        seekSmooth = findViewById(R.id.seekSmooth)
        seekWhiten = findViewById(R.id.seekWhiten)
        seekThin = findViewById(R.id.seekThin)
        seekBigEye = findViewById(R.id.seekBigEye)
        switchNoiseGate = findViewById(R.id.switchNoiseGate)
        switchReverb = findViewById(R.id.switchReverb)
        switchDucker = findViewById(R.id.switchDucker)
        switchPitch = findViewById(R.id.switchPitch)
    }

    private fun checkPermissions() {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERM)
        } else {
            initApp()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initApp()
        } else {
            Toast.makeText(this, "需要全部权限才能运行", Toast.LENGTH_LONG).show()
        }
    }

    private fun initApp() {
        beautyRenderer = BeautyRenderer(this)
        faceDetector = FaceDetector(this)
        cameraController = CameraController(this)

        // 启动音频引擎
        AudioEngine.start(48000)

        // 加载人脸模型 (可选，没有模型也能跑，只是没有瘦脸大眼)
        faceDetector.loadModel("face_landmark.tflite")

        // 加载默认LUT滤镜
        try { beautyRenderer.loadLutFromAsset("lut_default.png") } catch (_: Exception) {}

        // 设置GLSurfaceView
        glSurface.setEGLContextClientVersion(3)
        glSurface.setRenderer(GlRenderer())
        glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        // UI事件
        btnRecord.setOnClickListener { toggleRecording() }
        seekSmooth.setOnSeekBarChangeListener(simpleSeek { beautyRenderer.smoothStrength = it / 100f })
        seekWhiten.setOnSeekBarChangeListener(simpleSeek { beautyRenderer.whitenStrength = it / 100f })
        seekThin.setOnSeekBarChangeListener(simpleSeek { beautyRenderer.thinFaceStrength = it / 100f })
        seekBigEye.setOnSeekBarChangeListener(simpleSeek { beautyRenderer.bigEyeStrength = it / 100f })
        switchNoiseGate.setOnCheckedChangeListener { _, on -> AudioEngine.noiseGate = on }
        switchReverb.setOnCheckedChangeListener { _, on -> AudioEngine.reverbEnabled = on }
        switchDucker.setOnCheckedChangeListener { _, on -> AudioEngine.duckerEnabled = on }
        switchPitch.setOnCheckedChangeListener { _, on -> AudioEngine.pitchEnabled = on }
    }

    private fun toggleRecording() {
        if (isRecording) {
            mediaRecorder?.stop()
            mediaRecorder = null
            isRecording = false
            btnRecord.text = "开始录制"
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            dir.mkdirs()
            val path = File(dir, "VS_${System.currentTimeMillis()}.mp4").absolutePath
            mediaRecorder = MediaRecorder(1920, 1080)
            if (mediaRecorder?.start(path) == true) {
                isRecording = true
                btnRecord.text = "停止录制"
            }
        }
    }

    inner class GlRenderer : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            oesTexId = cameraController.init()
            beautyRenderer.init(1920, 1080)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            cameraController.updateTexImage()

            // 人脸检测 (每3帧检测一次节省性能)
            if (frameCount % 3 == 0) {
                // TODO: 从OES纹理读回Bitmap做检测，或用更高效的方式
                // 简化: 跳过帧检测，默认值已经可用
            }
            frameCount++

            // 美颜处理链
            val outputTex = beautyRenderer.processFrame(oesTexId)

            // 渲染到屏幕 或 编码器
            if (isRecording) {
                mediaRecorder?.feedVideoFrame(outputTex)
            }
            // 渲染到屏幕 (简化: 清屏显示)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            glSurface.requestRender()
        }

        private var frameCount = 0
    }

    private fun simpleSeek(cb: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) cb(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioEngine.stop()
        cameraController.release()
        beautyRenderer.release()
        faceDetector.release()
    }
}
