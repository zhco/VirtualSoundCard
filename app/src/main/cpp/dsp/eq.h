// eq.h - 三频段均衡器
#pragma once

class EQ {
public:
    void init(int sampleRate);
    float process(float input);
    void setLowGain(float db);
    void setMidGain(float db);
    void setHighGain(float db);

private:
    float m_low_gain = 1.0f;
    float m_mid_gain = 1.0f;
    float m_high_gain = 1.0f;
    // 双二阶滤波器状态 (low-shelf, peak, high-shelf)
    float m_lp_x1=0, m_lp_x2=0, m_lp_y1=0, m_lp_y2=0;
    float m_bp_x1=0, m_bp_x2=0, m_bp_y1=0, m_bp_y2=0;
    float m_hp_x1=0, m_hp_x2=0, m_hp_y1=0, m_hp_y2=0;
    float m_lp_b0, m_lp_b1, m_lp_b2, m_lp_a1, m_lp_a2;
    float m_bp_b0, m_bp_b1, m_bp_b2, m_bp_a1, m_bp_a2;
    float m_hp_b0, m_hp_b1, m_hp_b2, m_hp_a1, m_hp_a2;
    void designLowShelf(float fc, float gain, float Q, int sr);
    void designPeak(float fc, float gain, float Q, int sr);
    void designHighShelf(float fc, float gain, float Q, int sr);
};
