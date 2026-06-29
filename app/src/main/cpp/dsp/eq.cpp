// eq.cpp
#include "eq.h"
#include <cmath>

void EQ::init(int sampleRate) {
    designLowShelf(300.0f, 0.0f, 0.707f, sampleRate);
    designPeak(1000.0f, 0.0f, 1.0f, sampleRate);
    designHighShelf(3000.0f, 0.0f, 0.707f, sampleRate);
}

void EQ::setLowGain(float db)  { m_low_gain  = std::pow(10.0f, db / 20.0f); }
void EQ::setMidGain(float db)  { m_mid_gain  = std::pow(10.0f, db / 20.0f); }
void EQ::setHighGain(float db) { m_high_gain = std::pow(10.0f, db / 20.0f); }

static float biquad(float x, float &x1, float &x2, float &y1, float &y2,
                    float b0, float b1, float b2, float a1, float a2, float gain) {
    float y = gain * (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2);
    x2 = x1; x1 = x;
    y2 = y1; y1 = y;
    return y;
}

float EQ::process(float input) {
    float lp = biquad(input, m_lp_x1, m_lp_x2, m_lp_y1, m_lp_y2,
                      m_lp_b0, m_lp_b1, m_lp_b2, m_lp_a1, m_lp_a2, m_low_gain);
    float bp = biquad(input, m_bp_x1, m_bp_x2, m_bp_y1, m_bp_y2,
                      m_bp_b0, m_bp_b1, m_bp_b2, m_bp_a1, m_bp_a2, m_mid_gain);
    float hp = biquad(input, m_hp_x1, m_hp_x2, m_hp_y1, m_hp_y2,
                      m_hp_b0, m_hp_b1, m_hp_b2, m_hp_a1, m_hp_a2, m_high_gain);
    return (lp + bp + hp) / 3.0f;
}

void EQ::designLowShelf(float fc, float gain, float Q, int sr) {
    float w0 = 2.0f * M_PI * fc / sr;
    float cos_w0 = std::cos(w0), sin_w0 = std::sin(w0);
    float A = std::pow(10.0f, gain / 40.0f);
    float alpha = sin_w0 / (2.0f * Q);
    float beta = std::sqrt(A) / Q;
    float a0 = (A + 1.0f) + (A - 1.0f) * cos_w0 + beta * sin_w0;
    m_lp_b0 =  A * ((A + 1.0f) - (A - 1.0f) * cos_w0 + beta * sin_w0) / a0;
    m_lp_b1 =  2.0f * A * ((A - 1.0f) - (A + 1.0f) * cos_w0) / a0;
    m_lp_b2 =  A * ((A + 1.0f) - (A - 1.0f) * cos_w0 - beta * sin_w0) / a0;
    m_lp_a1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cos_w0) / a0;
    m_lp_a2 =  ((A + 1.0f) + (A - 1.0f) * cos_w0 - beta * sin_w0) / a0;
}

void EQ::designPeak(float fc, float gain, float Q, int sr) {
    float w0 = 2.0f * M_PI * fc / sr;
    float cos_w0 = std::cos(w0), sin_w0 = std::sin(w0);
    float A = std::pow(10.0f, gain / 40.0f);
    float alpha = sin_w0 / (2.0f * Q);
    float a0 = 1.0f + alpha / A;
    m_bp_b0 = (1.0f + alpha * A) / a0;
    m_bp_b1 = (-2.0f * cos_w0) / a0;
    m_bp_b2 = (1.0f - alpha * A) / a0;
    m_bp_a1 = (-2.0f * cos_w0) / a0;
    m_bp_a2 = (1.0f - alpha / A) / a0;
}

void EQ::designHighShelf(float fc, float gain, float Q, int sr) {
    float w0 = 2.0f * M_PI * fc / sr;
    float cos_w0 = std::cos(w0), sin_w0 = std::sin(w0);
    float A = std::pow(10.0f, gain / 40.0f);
    float beta = std::sqrt(A) / Q;
    float a0 = (A + 1.0f) - (A - 1.0f) * cos_w0 + beta * sin_w0;
    m_hp_b0 =  A * ((A + 1.0f) + (A - 1.0f) * cos_w0 + beta * sin_w0) / a0;
    m_hp_b1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cos_w0) / a0;
    m_hp_b2 =  A * ((A + 1.0f) + (A - 1.0f) * cos_w0 - beta * sin_w0) / a0;
    m_hp_a1 =  2.0f * ((A - 1.0f) - (A + 1.0f) * cos_w0) / a0;
    m_hp_a2 =  ((A + 1.0f) - (A - 1.0f) * cos_w0 - beta * sin_w0) / a0;
}
