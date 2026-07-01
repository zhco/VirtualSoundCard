package com.marvis.vs

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
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
import java.io.StringWriter
import java.io.PrintWriter
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
        try {
            setContentView(R.layout.activity_main)
            bindViews()
            checkPermissions()
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            AlertDialog.Builder(this)
                .setTitle("启动崩溃")
                .setMessage(sw.toString().take(2000))
                .setPositiveButton("关闭") { _, _ -> finish() }
                .show()
        }
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
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.RECORD_AUDIO)

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQ_PERM)
        } else {
            initApp()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                try {
                    initApp()
                } catch (e: Exception) {
                    val sw = StringWriter()
                    e.printStackTrace(PrintWriter(sw))
                    AlertDialog.Builder(this)
                        .setTitle("初始化崩溃")
                        .setMessage(sw.toString().take(2000))
                        .setPositiveButton("关闭") { _, _ -> finish() }
                        .show()
                }
            } else {
                Toast.makeText(this, "需要相机和麦克风权限才能运行", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initApp() {
        beautyRenderer = BeautyRenderer(this)
        faceDetector = FaceDetector(this)
        cameraController = CameraController(applicationContext, this)

        AudioEngine.start(48000)

        faceDetector.loadModel("face_landmark.tflite")

        try { beautyRenderer.loadLutFromAsset("lut_default.png") } catch (_: Exception) {}

        glSurface.setEGLContextClientVersion(3)
        glSurface.setRenderer(GlRenderer())
        glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        // 在主线程启动相机
        cameraController.startCamera()

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

    private fun getRecordingDir(): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            externalMediaDirs.firstOrNull()?.let { File(it, "Movies") }
                ?: File(filesDir, "Movies")
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        dir.mkdirs()
        return dir
    }

    private fun toggleRecording() {
        if (isRecording) {
            mediaRecorder?.stop()
            mediaRecorder = null
            isRecording = false
            btnRecord.text = "开始录制"
            runOnUiThread { Toast.makeText(this@MainActivity, "已保存", Toast.LENGTH_SHORT).show() }
        } else {
            try {
                val dir = getRecordingDir()
                val path = File(dir, "VS_${System.currentTimeMillis()}.mp4").absolutePath
                mediaRecorder = MediaRecorder(1920, 1080)
                if (mediaRecorder?.start(path) == true) {
                    isRecording = true
                    btnRecord.text = "停止录制"
                } else {
                    mediaRecorder = null
                    Toast.makeText(this, "录制启动失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mediaRecorder = null
                Toast.makeText(this, "录制出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class GlRenderer : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            oesTexId = cameraController.initOesTexture()
            beautyRenderer.init(1920, 1080)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            cameraController.updateTexImage()
            frameCount++
            val outputTex = beautyRenderer.processFrame(oesTexId)

            if (isRecording) {
                mediaRecorder?.drainVideoEncoder()
            }
            beautyRenderer.drawToScreen(outputTex)
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
        mediaRecorder?.stop()
        AudioEngine.stop()
        cameraController.release()
        beautyRenderer.release()
        faceDetector.release()
    }
}
