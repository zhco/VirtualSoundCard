cmake_minimum_required(VERSION 3.22.1)
project("virtualsoundcard")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -fno-exceptions -fno-rtti")

# Oboe
find_package(oboe REQUIRED CONFIG)

add_library(vs_audio SHARED
    audio_engine.cpp
    dsp/compressor.cpp
    dsp/eq.cpp
    dsp/reverb.cpp
    dsp/noise_gate.cpp
    dsp/ducker.cpp
    dsp/pitch_shifter.cpp
)

target_include_directories(vs_audio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(vs_audio
    oboe::oboe
    android
    log
)
