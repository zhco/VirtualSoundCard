package com.marvis.vs.audio

import android.content.res.AssetManager

/**
 * 虚拟声卡音频引擎 - JNI桥接层
 * 底层C++通过Oboe实现低延迟音频处理链
 */
object AudioEngine {
    init {
        System.loadLibrary("vs_audio")
    }

    // 生命周期
    external fun nativeStart(sampleRate: Int, channels: Int = 2): Boolean
    external fun nativeStop()

    // 伴奏数据推入
    external fun nativeWriteMusicData(data: FloatArray, length: Int)
    external fun nativeFlushMusic()

    // 参数控制
    external fun nativeSetMicGain(v: Float)
    external fun nativeSetMusicGain(v: Float)
    external fun nativeSetOutputGain(v: Float)
    external fun nativeSetNoiseGate(on: Boolean)
    external fun nativeSetCompressor(on: Boolean)
    external fun nativeSetEQ(on: Boolean)
    external fun nativeSetReverb(on: Boolean)
    external fun nativeSetDucker(on: Boolean)
    external fun nativeSetPitch(on: Boolean)
    external fun nativeSetDuckerThreshold(v: Float)
    external fun nativeSetDuckerAttenuation(v: Float)
    external fun nativeSetReverbMix(v: Float)
    external fun nativeSetPitchSemitones(v: Float)

    var isRunning = false
        private set

    fun start(sampleRate: Int = 48000): Boolean {
        if (isRunning) return true
        isRunning = nativeStart(sampleRate, 2)
        return isRunning
    }

    fun stop() {
        if (!isRunning) return
        nativeStop()
        isRunning = false
    }

    // --- 便捷参数设置 ---
    var micGain: Float = 1.0f
        set(v) { field = v; nativeSetMicGain(v) }

    var musicGain: Float = 1.0f
        set(v) { field = v; nativeSetMusicGain(v) }

    var noiseGate: Boolean = true
        set(v) { field = v; nativeSetNoiseGate(v) }

    var compressor: Boolean = true
        set(v) { field = v; nativeSetCompressor(v) }

    var eqEnabled: Boolean = false
        set(v) { field = v; nativeSetEQ(v) }

    var reverbEnabled: Boolean = false
        set(v) { field = v; nativeSetReverb(v) }

    var duckerEnabled: Boolean = false
        set(v) { field = v; nativeSetDucker(v) }

    var pitchEnabled: Boolean = false
        set(v) { field = v; nativeSetPitch(v) }

    var pitchSemitones: Float = 0.0f
        set(v) { field = v; nativeSetPitchSemitones(v) }

    var duckerAttenuation: Float = 0.3f
        set(v) { field = v; nativeSetDuckerAttenuation(v) }

    var reverbMix: Float = 0.2f
        set(v) { field = v; nativeSetReverbMix(v) }

    fun pushMusic(data: FloatArray) {
        nativeWriteMusicData(data, data.size)
    }

    fun flushMusic() {
        nativeFlushMusic()
    }
}
