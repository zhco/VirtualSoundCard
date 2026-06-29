// audio_engine.cpp - 虚拟声卡核心音频引擎 (JNI桥接层)
#include <jni.h>
#include <android/log.h>
#include <oboe/Oboe.h>
#include <atomic>
#include <mutex>
#include <vector>
#include <cstring>
#include <cmath>

#include "dsp/compressor.h"
#include "dsp/eq.h"
#include "dsp/reverb.h"
#include "dsp/noise_gate.h"
#include "dsp/ducker.h"
#include "dsp/pitch_shifter.h"

#define TAG "VSAudio"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace oboe;

// === 全局参数 (可由Java层通过JNI设置) ===
static std::atomic<float> g_mic_gain{1.0f};          // 麦克风增益 0~3
static std::atomic<float> g_music_gain{1.0f};         // 伴奏音量 0~1
static std::atomic<float> g_output_gain{1.0f};        // 输出音量 0~2
static std::atomic<bool>  g_noise_gate_on{true};      // 降噪开关
static std::atomic<bool>  g_compressor_on{true};      // 压缩器开关
static std::atomic<bool>  g_eq_on{false};             // EQ开关
static std::atomic<bool>  g_reverb_on{false};         // 混响开关
static std::atomic<bool>  g_ducker_on{false};         // 闪避开关
static std::atomic<bool>  g_pitch_on{false};          // 变声开关
static std::atomic<float> g_pitch_semitones{0.0f};    // 变声音调(半音)
static std::atomic<float> g_ducker_threshold{0.1f};   // 闪避阈值
static std::atomic<float> g_ducker_attenuation{0.3f}; // 闪避衰减(0~1,越小压得越低)
static std::atomic<float> g_reverb_mix{0.2f};         // 混响干湿比

// === 滤波状态 (每通道独立) ===
static NoiseGate   g_noise_gate_l, g_noise_gate_r;
static Compressor  g_comp_l, g_comp_r;
static EQ          g_eq_l, g_eq_r;
static Reverb      g_reverb_l, g_reverb_r;
static Ducker      g_ducker_l, g_ducker_r;
static PitchShifter g_pitch_l, g_pitch_r;

static int g_sample_rate = 48000;
static int g_channels   = 2;

// === 音频缓冲区 ===
static std::vector<float> g_music_buffer;   // 伴奏缓冲区(环形)
static std::atomic<int>   g_music_read_idx{0};
static std::atomic<int>   g_music_write_idx{0};
static std::mutex         g_music_mutex;
static const int          MUSIC_BUFFER_SIZE = 48000 * 10; // 10秒缓冲

// === 音频流回调 ===
class VSAudioCallback : public AudioStreamCallback {
public:
    DataCallbackResult onAudioReady(AudioStream *stream, void *audioData, int32_t numFrames) override {
        auto *output = static_cast<float *>(audioData);
        int total_samples = numFrames * g_channels;

        // 1. 读取伴奏
        std::vector<float> music(total_samples, 0.0f);
        {
            std::lock_guard<std::mutex> lock(g_music_mutex);
            int read = g_music_read_idx.load();
            for (int i = 0; i < total_samples; i++) {
                if (read < g_music_write_idx.load()) {
                    music[i] = g_music_buffer[read % MUSIC_BUFFER_SIZE];
                    read++;
                }
            }
            g_music_read_idx.store(read);
        }

        // 2. 处理每帧
        float mg = g_music_gain.load();
        float gg = g_mic_gain.load();
        float og = g_output_gain.load();

        for (int i = 0; i < numFrames; i++) {
            float mic_l = output[i * 2]     * gg;
            float mic_r = output[i * 2 + 1] * gg;
            float mus_l = music[i * 2]     * mg;
            float mus_r = music[i * 2 + 1] * mg;

            // --- 麦克风处理链 ---
            if (g_noise_gate_on) {
                mic_l = g_noise_gate_l.process(mic_l);
                mic_r = g_noise_gate_r.process(mic_r);
            }
            if (g_compressor_on) {
                mic_l = g_comp_l.process(mic_l);
                mic_r = g_comp_r.process(mic_r);
            }
            if (g_eq_on) {
                mic_l = g_eq_l.process(mic_l);
                mic_r = g_eq_r.process(mic_r);
            }
            if (g_pitch_on) {
                mic_l = g_pitch_l.process(mic_l);
                mic_r = g_pitch_r.process(mic_r);
            }

            // --- 闪避：用人声压伴奏 ---
            float mixed_l, mixed_r;
            if (g_ducker_on) {
                float env = (std::abs(mic_l) + std::abs(mic_r)) * 0.5f;
                mixed_l = mic_l + g_ducker_l.process(mus_l, env);
                mixed_r = mic_r + g_ducker_r.process(mus_r, env);
            } else {
                mixed_l = mic_l + mus_l;
                mixed_r = mic_r + mus_r;
            }

            // --- 混响(作用在混合后) ---
            if (g_reverb_on) {
                float dry_l = mixed_l, dry_r = mixed_r;
                g_reverb_l.process(mixed_l, mixed_r, &mixed_l, &mixed_r);
                float rm = g_reverb_mix.load();
                mixed_l = dry_l * (1.0f - rm) + mixed_l * rm;
                mixed_r = dry_r * (1.0f - rm) + mixed_r * rm;
            }

            // --- 输出限幅 ---
            output[i * 2]     = std::tanh(mixed_l * og);
            output[i * 2 + 1] = std::tanh(mixed_r * og);
        }

        return DataCallbackResult::Continue;
    }
};

static std::shared_ptr<AudioStream> g_stream = nullptr;
static std::shared_ptr<VSAudioCallback> g_callback = nullptr;

// === JNI 导出 ===

extern "C" JNIEXPORT jboolean JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeStart(JNIEnv*, jobject, jint sampleRate, jint channels) {
    g_sample_rate = sampleRate;
    g_channels = channels;

    // 初始化各DSP模块
    g_noise_gate_l.init(sampleRate, -40.0f, 0.01f, 0.1f);
    g_noise_gate_r.init(sampleRate, -40.0f, 0.01f, 0.1f);
    g_comp_l.init(sampleRate, -20.0f, 4.0f, 0.005f, 0.1f);
    g_comp_r.init(sampleRate, -20.0f, 4.0f, 0.005f, 0.1f);
    g_eq_l.init(sampleRate);
    g_eq_r.init(sampleRate);
    g_reverb_l.init(sampleRate, 0.8f, 0.5f, 0.3f);
    g_reverb_r.init(sampleRate, 0.8f, 0.5f, 0.3f);
    g_ducker_l.init(sampleRate, 0.01f, 0.2f, 0.3f);
    g_ducker_r.init(sampleRate, 0.01f, 0.2f, 0.3f);
    g_pitch_l.init(sampleRate);
    g_pitch_r.init(sampleRate);

    g_music_buffer.resize(MUSIC_BUFFER_SIZE, 0.0f);
    g_music_read_idx = g_music_write_idx = 0;

    AudioStreamBuilder builder;
    builder.setDirection(Direction::Output)
           ->setPerformanceMode(PerformanceMode::LowLatency)
           ->setSharingMode(SharingMode::Exclusive)
           ->setFormat(AudioFormat::Float)
           ->setChannelCount(channels)
           ->setSampleRate(sampleRate);

    g_callback = std::make_shared<VSAudioCallback>();
    builder.setCallback(g_callback.get());

    Result r = builder.openStream(g_stream);
    if (r != Result::OK) {
        LOGE("Failed to open audio stream: %s", convertToText(r));
        return false;
    }

    r = g_stream->requestStart();
    if (r != Result::OK) {
        LOGE("Failed to start audio stream");
        g_stream->close();
        return false;
    }

    LOGD("Audio engine started: %d Hz, %d ch", sampleRate, channels);
    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeStop(JNIEnv*, jobject) {
    if (g_stream) {
        g_stream->requestStop();
        g_stream->close();
        g_stream = nullptr;
    }
    g_callback = nullptr;
    LOGD("Audio engine stopped");
}

extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeWriteMusicData(JNIEnv* env, jobject, jfloatArray data, jint length) {
    jfloat *arr = env->GetFloatArrayElements(data, nullptr);
    std::lock_guard<std::mutex> lock(g_music_mutex);
    int write = g_music_write_idx.load();
    for (int i = 0; i < length; i++) {
        g_music_buffer[write % MUSIC_BUFFER_SIZE] = arr[i];
        write++;
    }
    g_music_write_idx.store(write);
    env->ReleaseFloatArrayElements(data, arr, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeFlushMusic(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_music_mutex);
    g_music_read_idx.store(0);
    g_music_write_idx.store(0);
    std::fill(g_music_buffer.begin(), g_music_buffer.end(), 0.0f);
}

// === 参数设置JNI ===
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetMicGain(JNIEnv*, jobject, jfloat v)  { g_mic_gain = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetMusicGain(JNIEnv*, jobject, jfloat v) { g_music_gain = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetOutputGain(JNIEnv*, jobject, jfloat v) { g_output_gain = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetNoiseGate(JNIEnv*, jobject, jboolean v) { g_noise_gate_on = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetCompressor(JNIEnv*, jobject, jboolean v) { g_compressor_on = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetEQ(JNIEnv*, jobject, jboolean v) { g_eq_on = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetReverb(JNIEnv*, jobject, jboolean v) { g_reverb_on = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetDucker(JNIEnv*, jobject, jboolean v) { g_ducker_on = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetPitch(JNIEnv*, jobject, jboolean v) { g_pitch_on = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetDuckerThreshold(JNIEnv*, jobject, jfloat v) { g_ducker_threshold = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetDuckerAttenuation(JNIEnv*, jobject, jfloat v) { g_ducker_attenuation = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetReverbMix(JNIEnv*, jobject, jfloat v) { g_reverb_mix = v; }
extern "C" JNIEXPORT void JNICALL
Java_com_marvis_vs_audio_AudioEngine_nativeSetPitchSemitones(JNIEnv*, jobject, jfloat v) {
    g_pitch_semitones = v;
    g_pitch_l.setShift(v);
    g_pitch_r.setShift(v);
}
