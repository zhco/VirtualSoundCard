// reverb.h - FreeVerb 简化混响
#pragma once
#include <vector>

class Reverb {
public:
    void init(int sampleRate, float roomSize = 0.8f, float damping = 0.5f, float width = 0.5f);
    void process(float inL, float inR, float *outL, float *outR);

private:
    float m_sampleRate;
    float m_roomSize, m_damping, m_width;
    std::vector<float> m_combL[8], m_combR[8];
    std::vector<int> m_combIdx;
    std::vector<float> m_allpassL[4], m_allpassR[4];
    std::vector<int> m_allpassIdx;
    std::vector<float> m_combFb, m_combFilt;
    float m_allpassFb = 0.5f;
};
