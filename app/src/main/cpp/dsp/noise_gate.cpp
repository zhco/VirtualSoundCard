// noise_gate.cpp
#include "noise_gate.h"
#include <cmath>
#include <algorithm>

void NoiseGate::init(int sampleRate, float thresholdDb, float attackSec, float releaseSec) {
    m_threshold = std::pow(10.0f, thresholdDb / 20.0f);
    m_attack_coeff = std::exp(-1.0f / (attackSec * sampleRate));
    m_release_coeff = std::exp(-1.0f / (releaseSec * sampleRate));
}

float NoiseGate::process(float input) {
    float abs_in = std::abs(input);
    if (abs_in > m_envelope)
        m_envelope = m_attack_coeff * m_envelope + (1.0f - m_attack_coeff) * abs_in;
    else
        m_envelope = m_release_coeff * m_envelope + (1.0f - m_release_coeff) * abs_in;
    return (m_envelope > m_threshold) ? input : 0.0f;
}
