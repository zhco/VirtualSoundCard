// pitch_shifter.cpp
#include "pitch_shifter.h"
#include <cmath>
#include <algorithm>

void PitchShifter::init(int sampleRate, int fftSize) {
    m_sampleRate = sampleRate;
    m_fftSize = fftSize;
    m_hopSize = fftSize / 4;
    m_shift = 0.0f;
    m_inputBuf.resize(fftSize * 2, 0.0f);
    m_outputBuf.resize(fftSize * 2, 0.0f);
    m_window.resize(fftSize);
    m_phaseAcc.resize(fftSize / 2 + 1);
    m_lastPhase.resize(fftSize / 2 + 1);
    for (int i = 0; i < fftSize; i++)
        m_window[i] = 0.5f * (1.0f - std::cos(2.0f * M_PI * i / (fftSize - 1)));
}

void PitchShifter::setShift(float semitones) { m_shift = semitones; }

float PitchShifter::process(float input) {
    m_inputBuf[m_bufPos] = input;
    m_bufPos++;
    if (m_bufPos >= m_hopSize) {
        m_bufPos -= m_hopSize;
        // 移位输入缓冲
        std::copy(m_inputBuf.begin() + m_hopSize, m_inputBuf.begin() + m_fftSize + m_hopSize,
                  m_inputBuf.begin());
        // 窗口+FFT
        std::vector<float> real(m_fftSize), imag(m_fftSize);
        for (int i = 0; i < m_fftSize; i++)
            real[i] = m_inputBuf[i] * m_window[i];
        fft(real, imag, false);
        // 相位调整
        float rate = std::pow(2.0f, m_shift / 12.0f) - 1.0f;
        for (int k = 0; k <= m_fftSize / 2; k++) {
            float mag = std::sqrt(real[k] * real[k] + imag[k] * imag[k]);
            float phase = std::atan2(imag[k], real[k]);
            float delta = phase - m_lastPhase[k];
            m_lastPhase[k] = phase;
            float trueFreq = k * m_sampleRate / (float)m_fftSize +
                             delta * m_sampleRate / (2.0f * M_PI * m_hopSize);
            int targetBin = std::clamp((int)(k * (1.0f + rate)), 0, m_fftSize / 2);
            m_phaseAcc[targetBin] += 2.0f * M_PI * trueFreq * m_hopSize / m_sampleRate;
            float p = m_phaseAcc[targetBin];
            real[targetBin] = mag * std::cos(p);
            imag[targetBin] = mag * std::sin(p);
        }
        fft(real, imag, true);
        // 叠加到输出缓冲
        for (int i = 0; i < m_fftSize; i++)
            m_outputBuf[i] += real[i] * m_window[i] / (m_fftSize / m_hopSize);
        // 移位输出缓冲
        std::rotate(m_outputBuf.begin(), m_outputBuf.begin() + m_hopSize, m_outputBuf.end());
        std::fill(m_outputBuf.end() - m_hopSize, m_outputBuf.end(), 0.0f);
    }
    float out = m_outputBuf[m_drainPos];
    m_drainPos = (m_drainPos + 1) % (int)m_outputBuf.size();
    return out;
}

void PitchShifter::fft(std::vector<float> &real, std::vector<float> &imag, bool inverse) {
    int n = m_fftSize;
    // 位反转
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) { std::swap(real[i], real[j]); std::swap(imag[i], imag[j]); }
    }
    // Cooley-Tukey
    for (int len = 2; len <= n; len <<= 1) {
        float ang = 2.0f * M_PI / len * (inverse ? -1.0f : 1.0f);
        float wlen_r = std::cos(ang), wlen_i = std::sin(ang);
        for (int i = 0; i < n; i += len) {
            float w_r = 1.0f, w_i = 0.0f;
            for (int j = 0; j < len / 2; j++) {
                float u_r = real[i + j], u_i = imag[i + j];
                float v_r = real[i + j + len/2] * w_r - imag[i + j + len/2] * w_i;
                float v_i = real[i + j + len/2] * w_i + imag[i + j + len/2] * w_r;
                real[i + j] = u_r + v_r; imag[i + j] = u_i + v_i;
                real[i + j + len/2] = u_r - v_r; imag[i + j + len/2] = u_i - v_i;
                float t = w_r * wlen_r - w_i * wlen_i;
                w_i = w_r * wlen_i + w_i * wlen_r;
                w_r = t;
            }
        }
    }
    if (inverse)
        for (int i = 0; i < n; i++) { real[i] /= n; imag[i] /= n; }
}
