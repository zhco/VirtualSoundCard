// noise_gate.h - 噪声门
#pragma once

class NoiseGate {
public:
    void init(int sampleRate, float thresholdDb = -40.0f, float attackSec = 0.01f, float releaseSec = 0.1f);
    float process(float input);

private:
    float m_threshold;
    float m_attack_coeff, m_release_coeff;
    float m_envelope = 0.0f;
};
