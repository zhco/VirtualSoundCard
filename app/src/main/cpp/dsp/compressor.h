// compressor.h - 动态压缩器
#pragma once

class Compressor {
public:
    void init(int sampleRate, float thresholdDb = -20.0f, float ratio = 4.0f,
              float attackSec = 0.005f, float releaseSec = 0.1f);
    float process(float input);

private:
    float m_threshold;     // 线性阈值
    float m_ratio_inv;     // 1/压缩比
    float m_attack_coeff;
    float m_release_coeff;
    float m_envelope = 0.0f;
    float m_makeup_gain;
};
