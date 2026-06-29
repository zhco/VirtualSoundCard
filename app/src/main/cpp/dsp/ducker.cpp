// ducker.cpp
#include "ducker.h"
#include <cmath>
#include <algorithm>

void Ducker::init(int sampleRate, float attackSec, float releaseSec, float attenuation) {
    m_attenuation = std::max(0.0f, std::min(1.0f, attenuation));
    m_attack_coeff = std::exp(-1.0f / (attackSec * sampleRate));
    m_release_coeff = std::exp(-1.0f / (releaseSec * sampleRate));
}

float Ducker::process(float music, float voiceEnv) {
    float target = (voiceEnv > 0.01f) ? m_attenuation : 1.0f;
    if (target < m_env)
        m_env = m_attack_coeff * m_env + (1.0f - m_attack_coeff) * target;
    else
        m_env = m_release_coeff * m_env + (1.0f - m_release_coeff) * target;
    return music * m_env;
}
