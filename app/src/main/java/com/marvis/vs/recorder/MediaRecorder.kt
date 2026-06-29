package com.marvis.vs.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface
import com.marvis.vs.audio.AudioEngine
import java.nio.ByteBuffer

/**
 * 视频录制器 - MediaCodec硬编码 + MediaMuxer合成
 * 视频: AVC/H.264, 音频: AAC
 */
class MediaRecorder(private val width: Int, private val height: Int) {
    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var inputSurface: Surface? = null
    private var eglDisplay: EGLDisplay? = null
    private var eglSurface: EGLSurface? = null
    private var eglContext: EGLContext? = null

    @Volatile var isRecording = false
        private set

    fun start(outputPath: String): Boolean {
        return try {
            // Video encoder
            val videoFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 8000000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoCodec?.configure(videoFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoCodec?.createInputSurface()

            // Audio encoder
            val audioFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
            }
            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioCodec?.configure(audioFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // Muxer
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoCodec?.start()
            audioCodec?.start()

            // EGL context for rendering to encoder surface
            setupEGL()

            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getInputSurface(): Surface? = inputSurface

    /**
     * 将美颜后的纹理渲染到编码器
     */
    fun feedVideoFrame(textureId: Int) {
        if (!isRecording) return
        // 此处由外部GL线程调用，将textureId渲染到inputSurface
        // 具体实现依赖GLSurfaceView的onDrawFrame回调
    }

    fun feedAudioData(data: ByteArray) {
        if (!isRecording) return
        val codec = audioCodec ?: return
        val idx = codec.dequeueInputBuffer(10000)
        if (idx >= 0) {
            val buf = codec.getInputBuffer(idx)
            buf?.clear()
            buf?.put(data)
            codec.queueInputBuffer(idx, 0, data.size, System.nanoTime() / 1000, 0)
        }
        drainAudio()
    }

    fun stop() {
        isRecording = false
        videoCodec?.signalEndOfInputStream()
        drainVideo()
        drainAudio()
        muxer?.stop()
        muxer?.release()
        videoCodec?.stop(); videoCodec?.release()
        audioCodec?.stop(); audioCodec?.release()
        inputSurface?.release()
        releaseEGL()
    }

    private fun drainVideo() {
        val codec = videoCodec ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 10000)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (idx >= 0) {
                val buf = codec.getOutputBuffer(idx) ?: continue
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) { codec.releaseOutputBuffer(idx, false); continue }
                if (info.size > 0 && muxerStarted) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    muxer?.writeSampleData(videoTrack, buf, info)
                }
                codec.releaseOutputBuffer(idx, false)
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    videoTrack = muxer?.addTrack(codec.outputFormat) ?: -1
                    audioTrack = muxer?.addTrack(audioCodec!!.outputFormat) ?: -1
                    muxer?.start()
                    muxerStarted = true
                }
            }
        }
    }

    private fun drainAudio() {
        val codec = audioCodec ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 10000)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (idx >= 0) {
                val buf = codec.getOutputBuffer(idx) ?: continue
                if (info.size > 0 && muxerStarted) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    muxer?.writeSampleData(audioTrack, buf, info)
                }
                codec.releaseOutputBuffer(idx, false)
            }
        }
    }

    private fun setupEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
        val configAttr = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES3_BIT_KHR
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        EGL14.eglChooseConfig(eglDisplay, configAttr, 0, configs, 0, 1, IntArray(1), 0)
        val ctxAttr = intArrayOf(0x3098, 3, EGL14.EGL_NONE) // EGL_CONTEXT_CLIENT_VERSION = 3
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], inputSurface, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun releaseEGL() {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }
}
