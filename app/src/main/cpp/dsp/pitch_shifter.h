// pitch_shifter.h - 相位声码器变声
#pragma once
#include <vector>

class PitchShifter {
public:
    void init(int sampleRate, int fftSize = 2048);
    void setShift(float semitones);
    float process(float input);

private:
    int m_sampleRate, m_fftSize, m_hopSize;
    float m_shift;
    std::vector<float> m_inputBuf, m_outputBuf, m_window;
    std::vector<float> m_phaseAcc, m_lastPhase;
    int m_bufPos = 0, m_drainPos = 0;
    void fft(std::vector<float> &real, std::vector<float> &imag, bool inverse);
};
