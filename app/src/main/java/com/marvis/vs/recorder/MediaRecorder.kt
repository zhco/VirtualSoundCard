package com.marvis.vs.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface

/**
 * 视频录制器 - MediaCodec硬编码 + MediaMuxer合成
 * 视频: AVC/H.264, 音频: AAC
 * 渲染使用GLSurfaceView已有的EGL上下文，不创建独立上下文
 */
class MediaRecorder(private val width: Int, private val height: Int) {
    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var inputSurface: Surface? = null

    @Volatile var isRecording = false
        private set

    fun start(outputPath: String): Boolean {
        return try {
            val videoFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 8000000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoCodec?.configure(videoFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoCodec?.createInputSurface()

            val audioFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
            }
            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioCodec?.configure(audioFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoCodec?.start()
            audioCodec?.start()

            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            stopInternal()
            false
        }
    }

    fun getInputSurface(): Surface? = inputSurface

    fun drainVideoEncoder() {
        val codec = videoCodec ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 10000)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (idx >= 0) {
                val buf = codec.getOutputBuffer(idx) ?: continue
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    codec.releaseOutputBuffer(idx, false)
                    continue
                }
                if (info.size > 0 && muxerStarted) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer?.writeSampleData(videoTrack, buf, info)
                }
                codec.releaseOutputBuffer(idx, false)
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    videoTrack = muxer?.addTrack(codec.outputFormat) ?: -1
                    if (audioCodec != null) {
                        audioTrack = muxer?.addTrack(audioCodec?.outputFormat) ?: -1
                    }
                    muxer?.start()
                    muxerStarted = true
                }
            }
        }
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
        drainAudioEncoder()
    }

    fun stop() {
        isRecording = false
        videoCodec?.signalEndOfInputStream()
        drainVideoEncoder()
        drainAudioEncoder()
        stopInternal()
    }

    private fun stopInternal() {
        try { muxer?.stop() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        try { videoCodec?.stop() } catch (_: Exception) {}
        try { videoCodec?.release() } catch (_: Exception) {}
        try { audioCodec?.stop() } catch (_: Exception) {}
        try { audioCodec?.release() } catch (_: Exception) {}
        try { inputSurface?.release() } catch (_: Exception) {}
        muxer = null
        videoCodec = null
        audioCodec = null
        inputSurface = null
    }

    private fun drainAudioEncoder() {
        val codec = audioCodec ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 10000)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (idx >= 0) {
                val buf = codec.getOutputBuffer(idx) ?: continue
                if (info.size > 0 && muxerStarted) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer?.writeSampleData(audioTrack, buf, info)
                }
                codec.releaseOutputBuffer(idx, false)
            }
        }
    }
}
