#pragma once

#include <cstdint>

enum class SegmentEffect : int {
    BackgroundReplace = 0,
    InkSplash = 1,
    CyberpunkGrid = 2,
    GlitchBackground = 3,
    PixelateBackground = 4,
    GlowSilhouette = 5,
    NeonOutline = 6,
    VanGoghPortrait = 7,
    Dramatic = 8,
    ColorPop = 9,
    Dreamy = 10,
    GoldenHour = 11,
    DoubleExposure = 12
};

struct EffectParams {
    SegmentEffect type;
    float intensity;
    uint8_t color1_r, color1_g, color1_b;
    uint8_t color2_r, color2_g, color2_b;
    float params[8];
    uint32_t seed;
};

void effects_generate_params(SegmentEffect type, EffectParams* out);

bool effect_apply(
    const EffectParams& p,
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int width, int height);
