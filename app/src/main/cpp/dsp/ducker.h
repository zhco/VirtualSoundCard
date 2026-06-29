// ducker.h - 人声闪避
#pragma once

class Ducker {
public:
    void init(int sampleRate, float attackSec = 0.01f, float releaseSec = 0.2f, float attenuation = 0.3f);
    float process(float music, float voiceEnv);

private:
    float m_attenuation;
    float m_attack_coeff, m_release_coeff;
    float m_env = 0.0f;
};
