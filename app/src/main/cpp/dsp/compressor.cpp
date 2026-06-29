// compressor.cpp
#include "compressor.h"
#include <cmath>
#include <algorithm>

void Compressor::init(int sampleRate, float thresholdDb, float ratio,
                      float attackSec, float releaseSec) {
    m_threshold = std::pow(10.0f, thresholdDb / 20.0f);
    m_ratio_inv = 1.0f / ratio;
    m_attack_coeff = std::exp(-1.0f / (attackSec * sampleRate));
    m_release_coeff = std::exp(-1.0f / (releaseSec * sampleRate));
    m_makeup_gain = std::pow(m_threshold, 1.0f - m_ratio_inv);
    m_envelope = 0.0f;
}

float Compressor::process(float input) {
    float abs_in = std::abs(input);
    // RMS包络检测
    if (abs_in > m_envelope)
        m_envelope = m_attack_coeff * m_envelope + (1.0f - m_attack_coeff) * abs_in;
    else
        m_envelope = m_release_coeff * m_envelope + (1.0f - m_release_coeff) * abs_in;

    float gain = 1.0f;
    if (m_envelope > m_threshold) {
        float over = m_envelope / m_threshold;
        gain = std::pow(over, m_ratio_inv - 1.0f);
    }
    return input * gain * m_makeup_gain;
}
