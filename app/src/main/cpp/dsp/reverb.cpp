// reverb.cpp
#include "reverb.h"
#include <cmath>
#include <algorithm>

static const int kCombTuning[] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
static const int kAllpassTuning[] = {556, 441, 341, 225};

void Reverb::init(int sampleRate, float roomSize, float damping, float width) {
    m_sampleRate = sampleRate;
    m_roomSize = roomSize;
    m_damping = damping;
    m_width = width;
    m_combIdx.resize(8);
    m_combFb.resize(8);
    m_combFilt.resize(8);
    for (int i = 0; i < 8; i++) {
        int len = kCombTuning[i] * sampleRate / 44100;
        m_combL[i].resize(len + 1, 0.0f);
        m_combR[i].resize(len + 1, 0.0f);
        m_combIdx[i] = 0;
        m_combFb[i] = 0.0f;
        m_combFilt[i] = 0.0f;
    }
    m_allpassIdx.resize(4);
    for (int i = 0; i < 4; i++) {
        int len = kAllpassTuning[i] * sampleRate / 44100;
        m_allpassL[i].resize(len + 1, 0.0f);
        m_allpassR[i].resize(len + 1, 0.0f);
        m_allpassIdx[i] = 0;
    }
}

void Reverb::process(float inL, float inR, float *outL, float *outR) {
    float mono = (inL + inR) * 0.5f;
    float fb = m_roomSize * 0.85f;
    float damp1 = m_damping;
    float damp2 = 1.0f - damp1;

    float outMono = 0.0f;
    // 8个梳状滤波器
    for (int i = 0; i < 8; i++) {
        int idx = m_combIdx[i];
        float buf = (i < 4) ? m_combL[i][idx] : m_combR[i][idx];
        m_combFilt[i] = buf * damp2 + m_combFilt[i] * damp1;
        m_combL[i][idx] = mono + m_combFilt[i] * m_combFb[i];
        m_combR[i][idx] = mono + m_combFilt[i] * m_combFb[i];
        m_combFb[i] = fb;
        m_combIdx[i] = (idx + 1) % (int)m_combL[i].size();
        outMono += buf;
    }
    outMono /= 8.0f;

    // 4个全通滤波器
    float tmp = outMono;
    for (int i = 0; i < 4; i++) {
        int idx = m_allpassIdx[i];
        float buf = m_allpassL[i][idx];
        m_allpassL[i][idx] = tmp + buf * m_allpassFb;
        tmp = buf - tmp * m_allpassFb;
        m_allpassIdx[i] = (idx + 1) % (int)m_allpassL[i].size();
    }

    float w1 = (1.0f + m_width) * 0.5f;
    float w2 = (1.0f - m_width) * 0.5f;
    *outL = tmp * w1 + tmp * w2;
    *outR = tmp * w2 + tmp * w1;
}
