#include "effects.h"

#include <cmath>
#include <cstring>
#include <cstdlib>
#include <algorithm>

// ─── Utility ──────────────────────────────────────────────────────────────

static inline uint8_t clamp_u8(float v) {
    if (v < 0.0f) return 0;
    if (v > 255.0f) return 255;
    return (uint8_t)(v + 0.5f);
}

static inline float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}

static inline uint8_t lerp_u8(uint8_t a, uint8_t b, float t) {
    return clamp_u8((float)a + ((float)b - (float)a) * t);
}

static inline float smoothstep(float edge0, float edge1, float x) {
    float t = std::max(0.0f, std::min(1.0f, (x - edge0) / (edge1 - edge0)));
    return t * t * (3.0f - 2.0f * t);
}

// Simple LCG random
static thread_local uint32_t rng_state = 12345;
static void rng_seed(uint32_t s) { rng_state = s; }
static uint32_t rng_next() {
    rng_state = rng_state * 1103515245 + 12345;
    return rng_state;
}
static float rng_float() { return (float)(rng_next() & 0x7FFFFFFF) / 2147483648.0f; }
static int rng_int(int lo, int hi) {
    if (hi <= lo) return lo;
    return lo + (int)(rng_float() * (float)(hi - lo + 1));
}

// HSL → RGB (all in 0-1 range)
static void hsl_to_rgb(float h, float s, float l,
                        uint8_t* r, uint8_t* g, uint8_t* b) {
    float c = (1.0f - std::abs(2.0f * l - 1.0f)) * s;
    float hp = h / 60.0f;
    float x = c * (1.0f - std::abs(std::fmod(hp, 2.0f) - 1.0f));
    float r1 = 0, g1 = 0, bl = 0;
    int hi = (int)hp % 6;
    if (hi < 0) hi += 6;
    switch (hi) {
        case 0: r1=c; g1=x; bl=0; break;
        case 1: r1=x; g1=c; bl=0; break;
        case 2: r1=0; g1=c; bl=x; break;
        case 3: r1=0; g1=x; bl=c; break;
        case 4: r1=x; g1=0; bl=c; break;
        case 5: r1=c; g1=0; bl=x; break;
    }
    float m = l - c / 2.0f;
    *r = clamp_u8((r1 + m) * 255.0f);
    *g = clamp_u8((g1 + m) * 255.0f);
    *b = clamp_u8((bl + m) * 255.0f);
}

// ─── Param Generation (randomized per effect) ────────────────────────────

void effects_generate_params(SegmentEffect type, EffectParams* p) {
    if (!p) return;
    p->type = type;
    p->seed = (uint32_t)(std::rand() ^ (intptr_t)p);
    rng_seed(p->seed);

    // Default colors (will be randomized per effect type)
    float h1 = 0, s1 = 0, l1 = 0.5f;
    float h2 = 0, s2 = 0, l2 = 0.5f;

    for (int i = 0; i < 8; i++) p->params[i] = 0.0f;

    switch (type) {
    case SegmentEffect::BackgroundReplace: {
        p->intensity = 1.0f;
        h1 = rng_float() * 360.0f; s1 = 0.3f + rng_float() * 0.7f; l1 = 0.2f + rng_float() * 0.6f;
        h2 = h1 + 30.0f + rng_float() * 120.0f;
        if (h2 > 360.0f) h2 -= 360.0f;
        s2 = 0.3f + rng_float() * 0.7f; l2 = 0.2f + rng_float() * 0.6f;
        p->params[0] = rng_float() * 180.0f; // gradient angle
        p->params[1] = 0.1f + rng_float() * 0.3f; // edge feather width
        break;
    }
    case SegmentEffect::InkSplash: {
        p->intensity = 1.0f;
        h1 = rng_float() * 360.0f; s1 = 0.5f + rng_float() * 0.5f; l1 = 0.2f + rng_float() * 0.4f;
        p->params[0] = (float)rng_int(3, 12); // num splashes
        p->params[1] = 0.05f + rng_float() * 0.2f; // splash radius (fraction of width)
        p->params[2] = 0.1f + rng_float() * 0.4f; // noise amount
        break;
    }
    case SegmentEffect::CyberpunkGrid: {
        p->intensity = 1.0f;
        s1 = 0.8f + rng_float() * 0.2f; l1 = 0.5f + rng_float() * 0.3f;
        h1 = rng_float() < 0.5f ? 180.0f : 300.0f; // cyan or magenta
        s2 = 0.8f + rng_float() * 0.2f; l2 = 0.5f + rng_float() * 0.3f;
        h2 = (h1 > 200.0f) ? 300.0f : 180.0f; // opposite
        p->params[0] = (float)rng_int(20, 80); // grid cell size
        p->params[1] = 1.0f + rng_float() * 3.0f; // line width
        p->params[2] = 0.3f + rng_float() * 0.5f; // glow radius
        break;
    }
    case SegmentEffect::GlitchBackground: {
        p->intensity = 1.0f;
        p->params[0] = (float)rng_int(4, 16); // num slices
        p->params[1] = 5.0f + rng_float() * 40.0f; // max pixel offset
        p->params[2] = 1.0f + rng_float() * 5.0f; // chroma shift strength
        break;
    }
    case SegmentEffect::PixelateBackground: {
        p->intensity = 1.0f;
        p->params[0] = (float)rng_int(8, 48); // block size
        p->params[1] = 0.05f + rng_float() * 0.15f; // edge feather width
        break;
    }
    case SegmentEffect::GlowSilhouette: {
        p->intensity = 1.0f;
        h1 = rng_float() * 360.0f; s1 = 0.6f + rng_float() * 0.4f; l1 = 0.4f + rng_float() * 0.4f;
        p->params[0] = 3.0f + rng_float() * 10.0f; // glow radius
        p->params[1] = 0.5f + rng_float() * 0.5f; // glow opacity
        break;
    }
    case SegmentEffect::NeonOutline: {
        p->intensity = 1.0f;
        s1 = 0.8f + rng_float() * 0.2f; l1 = 0.5f;
        h1 = rng_float() * 360.0f; // fully random neon color
        p->params[0] = 1.0f + rng_float() * 3.0f; // outline thickness
        p->params[1] = 0.05f; // edge threshold
        break;
    }
    case SegmentEffect::VanGoghPortrait: {
        p->intensity = 1.0f;
        p->params[0] = 1.5f + rng_float() * 3.0f; // brush stroke strength
        p->params[1] = 0.1f + rng_float() * 0.3f; // edge enhancement
        break;
    }
    case SegmentEffect::Dramatic: {
        p->intensity = 1.0f;
        p->params[0] = 1.3f + rng_float() * 0.5f; // person contrast
        p->params[1] = 0.3f + rng_float() * 0.4f; // bg saturation
        p->params[2] = -0.05f - rng_float() * 0.1f; // bg brightness
        break;
    }
    case SegmentEffect::ColorPop: {
        p->intensity = 1.0f;
        p->params[0] = 1.2f + rng_float() * 0.5f; // person saturation boost
        p->params[1] = 0.0f; // bg saturation (0 = B&W)
        p->params[2] = 0.02f + rng_float() * 0.05f; // person brightness
        break;
    }
    case SegmentEffect::Dreamy: {
        p->intensity = 1.0f;
        h1 = 30.0f; s1 = 0.3f; l1 = 0.6f; // warm person tint
        h2 = 210.0f; s2 = 0.2f; l2 = 0.5f; // cool bg tint
        p->params[0] = 1.0f + rng_float() * 3.0f; // blur on person
        break;
    }
    case SegmentEffect::GoldenHour: {
        p->intensity = 1.0f;
        h1 = 45.0f + rng_float() * 15.0f; // warm golden
        h2 = 200.0f + rng_float() * 40.0f; // cool blue
        p->params[0] = 0.2f + rng_float() * 0.3f; // warmth amount
        break;
    }
    case SegmentEffect::DoubleExposure: {
        p->intensity = 1.0f;
        h1 = rng_float() * 360.0f; s1 = 0.3f + rng_float() * 0.5f; l1 = 0.3f + rng_float() * 0.4f;
        h2 = h1 + 180.0f; if (h2 > 360.0f) h2 -= 360.0f;
        s2 = 0.3f + rng_float() * 0.5f; l2 = 0.3f + rng_float() * 0.4f;
        p->params[0] = 0.3f + rng_float() * 0.5f; // blend amount
        p->params[1] = (float)rng_int(0, 3); // pattern type (0=gradient,1=grid,2=noise,3=radial)
        break;
    }
    }

    hsl_to_rgb(h1, s1, l1, &p->color1_r, &p->color1_g, &p->color1_b);
    hsl_to_rgb(h2, s2, l2, &p->color2_r, &p->color2_g, &p->color2_b);
}

// ─── Per-Effect Functions ─────────────────────────────────────────────────

static void effect_bg_replace(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float angle = p.params[0] * 3.14159f / 180.0f;
    float feather = p.params[1];
    float cos_a = std::cos(angle);
    float sin_a = std::sin(angle);
    float cx = (float)w / 2.0f;
    float cy = (float)h / 2.0f;
    float max_dist = std::sqrt((float)w * (float)w + (float)h * (float)h) / 2.0f;

    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = (y * w + x) * 4;
            float dx = (float)x - cx;
            float dy = (float)y - cy;
            float t = (dx * cos_a + dy * sin_a) / max_dist + 0.5f;
            t = std::max(0.0f, std::min(1.0f, t));

            uint8_t bg_r = lerp_u8(p.color1_r, p.color2_r, t);
            uint8_t bg_g = lerp_u8(p.color1_g, p.color2_g, t);
            uint8_t bg_b = lerp_u8(p.color1_b, p.color2_b, t);

            float m = mask[idx / 4];
            float blend = smoothstep(0.5f - feather, 0.5f + feather, m);

            out[idx + 0] = lerp_u8(bg_r, rgba[idx + 0], blend);
            out[idx + 1] = lerp_u8(bg_g, rgba[idx + 1], blend);
            out[idx + 2] = lerp_u8(bg_b, rgba[idx + 2], blend);
            out[idx + 3] = 255;
        }
    }
}

static void effect_ink_splash(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    int num = (int)p.params[0];
    float radius = p.params[1] * (float)w;
    float noise = p.params[2];

    struct Splash { float x, y, r; uint8_t cr, cg, cb; };
    Splash* splashes = new Splash[num];
    for (int i = 0; i < num; i++) {
        splashes[i].x = rng_float() * (float)w;
        splashes[i].y = rng_float() * (float)h;
        splashes[i].r = radius * (0.3f + rng_float() * 0.7f);
        float sh = rng_float() * 360.0f;
        hsl_to_rgb(sh, 0.6f + rng_float() * 0.4f, 0.3f + rng_float() * 0.3f,
                   &splashes[i].cr, &splashes[i].cg, &splashes[i].cb);
    }

    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = (y * w + x) * 4;
            float m = mask[idx / 4];
            if (m > 0.5f) {
                // Person area — pass through
                std::memcpy(out + idx, rgba + idx, 4);
                continue;
            }

            // Background — check proximity to splashes
            float best_dist = 1e10f;
            uint8_t sr = 0, sg = 0, sb = 0;
            for (int i = 0; i < num; i++) {
                float dx = (float)x - splashes[i].x;
                float dy = (float)y - splashes[i].y;
                float d = std::sqrt(dx * dx + dy * dy);
                if (d < best_dist) {
                    best_dist = d;
                    sr = splashes[i].cr;
                    sg = splashes[i].cg;
                    sb = splashes[i].cb;
                }
            }

            float ink_strength = std::max(0.0f, 1.0f - best_dist / radius);
            float nz = (rng_float() - 0.5f) * noise;
            ink_strength = std::max(0.0f, std::min(1.0f, ink_strength + nz));

            out[idx + 0] = lerp_u8(rgba[idx + 0], sr, ink_strength);
            out[idx + 1] = lerp_u8(rgba[idx + 1], sg, ink_strength);
            out[idx + 2] = lerp_u8(rgba[idx + 2], sb, ink_strength);
            out[idx + 3] = 255;
        }
    }
    delete[] splashes;
}

static void effect_cyberpunk_grid(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    int cell = (int)p.params[0];
    float line_w = p.params[1];
    float glow = p.params[2];

    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = (y * w + x) * 4;
            float m = mask[idx / 4];

            if (m > 0.5f) {
                std::memcpy(out + idx, rgba + idx, 4);
                continue;
            }

            // Check if pixel is near a grid line
            int gx = x % cell;
            int gy = y % cell;
            float d_h = (float)std::min(gx, cell - gx);
            float d_v = (float)std::min(gy, cell - gy);
            float d = std::min(d_h, d_v);

            if (d < glow) {
                // Glow zone
                float intensity = smoothstep(glow, 0.0f, d);
                float main_color = smoothstep(glow, line_w, d);
                uint8_t r = lerp_u8(p.color1_r, p.color2_r, intensity);
                uint8_t g2 = lerp_u8(p.color1_g, p.color2_g, intensity);
                uint8_t b = lerp_u8(p.color1_b, p.color2_b, intensity);

                if (main_color <= 0.0f) {
                    // Center line — full neon
                    out[idx + 0] = clamp_u8(lerp((float)rgba[idx + 0], (float)r, 0.9f));
                    out[idx + 1] = clamp_u8(lerp((float)rgba[idx + 1], (float)g2, 0.9f));
                    out[idx + 2] = clamp_u8(lerp((float)rgba[idx + 2], (float)b, 0.9f));
                } else {
                    // Glow falloff
                    float glow_s = std::max(0.0f, intensity - main_color);
                    out[idx + 0] = lerp_u8(rgba[idx + 0], r, glow_s * 0.5f);
                    out[idx + 1] = lerp_u8(rgba[idx + 1], g2, glow_s * 0.5f);
                    out[idx + 2] = lerp_u8(rgba[idx + 2], b, glow_s * 0.5f);
                }
            } else {
                // Darken background slightly for contrast
                out[idx + 0] = (uint8_t)((unsigned)rgba[idx + 0] * 7 / 10);
                out[idx + 1] = (uint8_t)((unsigned)rgba[idx + 1] * 7 / 10);
                out[idx + 2] = (uint8_t)((unsigned)rgba[idx + 2] * 7 / 10);
            }
            out[idx + 3] = 255;
        }
    }
}

static void effect_glitch_bg(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    int num_slices = (int)p.params[0];
    float max_off = p.params[1];
    float chroma = p.params[2];

    // Generate random slice boundaries
    struct Slice { int y0, y1; int offset; int chroma_r, chroma_b; };
    Slice* slices = new Slice[num_slices];
    int ypos = 0;
    for (int i = 0; i < num_slices; i++) {
        slices[i].y0 = ypos;
        int sh = h / num_slices;
        sh = sh + rng_int(-sh / 3, sh / 3);
        slices[i].y1 = std::min(ypos + std::max(sh, 1), h);
        slices[i].offset = rng_int((int)-max_off, (int)max_off);
        slices[i].chroma_r = rng_int(-(int)chroma, (int)chroma);
        slices[i].chroma_b = rng_int(-(int)chroma, (int)chroma);
        ypos = slices[i].y1;
        if (ypos >= h) break;
    }

    // Copy original first
    std::memcpy(out, rgba, (size_t)w * h * 4);

    for (int s = 0; s < num_slices; s++) {
        if (slices[s].y0 >= h) break;
        for (int y = slices[s].y0; y < slices[s].y1; y++) {
            for (int x = 0; x < w; x++) {
                int idx = (y * w + x) * 4;
                float m = mask[idx / 4];
                if (m > 0.5f) continue; // Don't glitch person

                int src_x = x + slices[s].offset;
                if (src_x < 0 || src_x >= w) {
                    out[idx + 0] = (uint8_t)(rng_float() * 255.0f);
                    out[idx + 1] = (uint8_t)(rng_float() * 255.0f);
                    out[idx + 2] = (uint8_t)(rng_float() * 255.0f);
                    continue;
                }

                int src_idx = (y * w + src_x) * 4;
                out[idx + 0] = rgba[src_idx + 0];
                out[idx + 1] = rgba[src_idx + 1];
                out[idx + 2] = rgba[src_idx + 2];

                // Chromatic aberration
                int r_idx = (y * w + std::min(w - 1, std::max(0, src_x + slices[s].chroma_r))) * 4;
                int b_idx = (y * w + std::min(w - 1, std::max(0, src_x + slices[s].chroma_b))) * 4;
                out[idx + 0] = rgba[r_idx + 0];
                out[idx + 2] = rgba[b_idx + 2];
            }
        }
    }
    delete[] slices;
}

static void effect_pixelate_bg(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    int block = (int)p.params[0];
    float feather = p.params[1];

    // Pass through person area at full resolution first
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = (y * w + x) * 4;
            float m = mask[idx / 4];
            float t = smoothstep(0.5f - feather, 0.5f + feather, m);

            int bx = (x / block) * block + block / 2;
            int by = (y / block) * block + block / 2;
            bx = std::min(bx, w - 1);
            by = std::min(by, h - 1);
            int b_idx = (by * w + bx) * 4;

            out[idx + 0] = lerp_u8(rgba[b_idx + 0], rgba[idx + 0], t);
            out[idx + 1] = lerp_u8(rgba[b_idx + 1], rgba[idx + 1], t);
            out[idx + 2] = lerp_u8(rgba[b_idx + 2], rgba[idx + 2], t);
            out[idx + 3] = 255;
        }
    }
}

static void effect_glow_silhouette(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float radius = p.params[0];
    float opacity = p.params[1];

    // Create blurred edge mask
    int kr = (int)std::ceil(radius * 2.0f + 1.0f);
    float* glow = new float[(size_t)w * h]();
    float* temp = new float[(size_t)w * h]();

    // Gaussian blur on mask to get glow
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            float sum = 0.0f, w_sum = 0.0f;
            for (int ky = -kr; ky <= kr; ky++) {
                int sy = y + ky;
                if (sy < 0 || sy >= h) continue;
                float gy = std::exp(-(float)(ky * ky) / (2.0f * radius * radius));
                for (int kx = -kr; kx <= kr; kx++) {
                    int sx = x + kx;
                    if (sx < 0 || sx >= w) continue;
                    float gx = std::exp(-(float)(kx * kx) / (2.0f * radius * radius));
                    float g = gx * gy;
                    temp[y * w + x] += mask[sy * w + sx] * g;
                    w_sum += g;
                }
            }
            if (w_sum > 0) temp[y * w + x] /= w_sum;
        }
    }

    // Edge detection on blurred mask
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            float c = temp[y * w + x];
            // Difference from original mask gives edge
            float edge = std::abs(c - mask[y * w + x]);
            glow[y * w + x] = edge * opacity;
        }
    }

    // Apply glow color
    for (int i = 0; i < w * h; i++) {
        int idx = i * 4;
        float g = glow[i];
        out[idx + 0] = lerp_u8(rgba[idx + 0], p.color1_r, g);
        out[idx + 1] = lerp_u8(rgba[idx + 1], p.color1_g, g);
        out[idx + 2] = lerp_u8(rgba[idx + 2], p.color1_b, g);
        out[idx + 3] = 255;
    }

    delete[] glow;
    delete[] temp;
}

static void effect_neon_outline(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float thickness = p.params[0];
    float threshold = p.params[1];

    // Sobel edge detection on mask
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = (y * w + x) * 4;

            if (y == 0 || y == h - 1 || x == 0 || x == w - 1) {
                std::memcpy(out + idx, rgba + idx, 4);
                continue;
            }

            float gx = -mask[(y-1)*w + x-1] + mask[(y-1)*w + x+1]
                       -2.0f * mask[y*w + x-1] + 2.0f * mask[y*w + x+1]
                       -mask[(y+1)*w + x-1] + mask[(y+1)*w + x+1];
            float gy = -mask[(y-1)*w + x-1] - 2.0f * mask[(y-1)*w + x] - mask[(y-1)*w + x+1]
                       + mask[(y+1)*w + x-1] + 2.0f * mask[(y+1)*w + x] + mask[(y+1)*w + x+1];
            float edge = std::sqrt(gx * gx + gy * gy);

            // Normalize to 0-1
            edge = std::min(1.0f, edge * 3.0f);

            if (edge > threshold) {
                // Thickness: sample multiple edges
                // For a simple outline, just use the edge directly
                out[idx + 0] = lerp_u8(rgba[idx + 0], p.color1_r, edge);
                out[idx + 1] = lerp_u8(rgba[idx + 1], p.color1_g, edge);
                out[idx + 2] = lerp_u8(rgba[idx + 2], p.color1_b, edge);
            } else {
                std::memcpy(out + idx, rgba + idx, 4);
            }
        }
    }
}

static void effect_van_gogh(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float stroke = p.params[0];
    float enhance = p.params[1];

    // Pass through background as-is, apply stroke effect on person
    std::memcpy(out, rgba, (size_t)w * h * 4);

    // Simple brushstroke: enhance edges + boost saturation on person area
    for (int y = 1; y < h - 1; y++) {
        for (int x = 1; x < w - 1; x++) {
            int idx = (y * w + x) * 4;
            float m = mask[idx / 4];
            if (m < 0.5f) continue;

            // Simple edge-aware sharpen
            float l_self = rgba[idx + 0] * 0.3f + rgba[idx + 1] * 0.59f + rgba[idx + 2] * 0.11f;
            float l_left = rgba[(y * w + x-1) * 4 + 0] * 0.3f
                         + rgba[(y * w + x-1) * 4 + 1] * 0.59f
                         + rgba[(y * w + x-1) * 4 + 2] * 0.11f;
            float edge = std::abs(l_self - l_left) / 255.0f * enhance;

            // Boost saturation
            int r = rgba[idx + 0], g = rgba[idx + 1], b = rgba[idx + 2];
            float gray = r * 0.3f + g * 0.59f + b * 0.11f;
            float sat = 1.0f + stroke * 0.3f;
            int nr = clamp_u8(lerp(gray, (float)r, sat));
            int ng = clamp_u8(lerp(gray, (float)g, sat));
            int nb = clamp_u8(lerp(gray, (float)b, sat));

            // Add brushstroke texture (subtle noise)
            float nz = (rng_float() - 0.5f) * stroke * 0.1f * 255.0f;
            out[idx + 0] = clamp_u8(lerp((float)nr, (float)clamp_u8((float)nr + nz), edge * 0.5f) + edge * 30.0f);
            out[idx + 1] = clamp_u8(lerp((float)ng, (float)clamp_u8((float)ng + nz), edge * 0.5f) + edge * 15.0f);
            out[idx + 2] = clamp_u8(lerp((float)nb, (float)clamp_u8((float)nb + nz), edge * 0.5f));
        }
    }
}

static void effect_dramatic(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float contrast = p.params[0];
    float bg_sat = p.params[1];
    float bg_bright = p.params[2];

    for (int i = 0; i < w * h; i++) {
        int idx = i * 4;
        float m = mask[i];

        float r = (float)rgba[idx + 0];
        float g = (float)rgba[idx + 1];
        float b = (float)rgba[idx + 2];
        float gray = r * 0.3f + g * 0.59f + b * 0.11f;

        if (m > 0.5f) {
            // Person: increase contrast
            float cr = (r / 255.0f - 0.5f) * contrast + 0.5f;
            float cg = (g / 255.0f - 0.5f) * contrast + 0.5f;
            float cb = (b / 255.0f - 0.5f) * contrast + 0.5f;
            out[idx + 0] = clamp_u8(cr * 255.0f);
            out[idx + 1] = clamp_u8(cg * 255.0f);
            out[idx + 2] = clamp_u8(cb * 255.0f);
        } else {
            // Background: desaturate + darken
            float bg = gray + bg_bright * 255.0f;
            float adjusted = lerp(gray, bg, 0.7f);
            out[idx + 0] = clamp_u8(lerp(adjusted, r, bg_sat));
            out[idx + 1] = clamp_u8(lerp(adjusted, g, bg_sat));
            out[idx + 2] = clamp_u8(lerp(adjusted, b, bg_sat));
        }
        out[idx + 3] = 255;
    }
}

static void effect_color_pop(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float person_sat = p.params[0];
    float bg_sat = p.params[1];
    float person_bright = p.params[2];

    for (int i = 0; i < w * h; i++) {
        int idx = i * 4;
        float m = mask[i];

        float r = (float)rgba[idx + 0];
        float g = (float)rgba[idx + 1];
        float b = (float)rgba[idx + 2];
        float gray = r * 0.3f + g * 0.59f + b * 0.11f;

        if (m > 0.5f) {
            // Person: boost saturation + slight brightness
            r = lerp(gray, r, person_sat) + person_bright * 255.0f;
            g = lerp(gray, g, person_sat) + person_bright * 255.0f;
            b = lerp(gray, b, person_sat) + person_bright * 255.0f;
            out[idx + 0] = clamp_u8(r);
            out[idx + 1] = clamp_u8(g);
            out[idx + 2] = clamp_u8(b);
        } else {
            // Background: B&W
            out[idx + 0] = clamp_u8(lerp(gray, r, bg_sat));
            out[idx + 1] = clamp_u8(lerp(gray, g, bg_sat));
            out[idx + 2] = clamp_u8(lerp(gray, b, bg_sat));
        }
        out[idx + 3] = 255;
    }
}

static void effect_dreamy(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float blur_amount = p.params[0];
    int kr = (int)std::ceil(blur_amount * 2.0f);

    // Apply soft blur + warm tint to person
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = (y * w + x) * 4;
            float m = mask[idx / 4];

            if (m > 0.5f) {
                // Blur person
                float r = 0, g = 0, b = 0, wsum = 0;
                for (int ky = -kr; ky <= kr; ky++) {
                    for (int kx = -kr; kx <= kr; kx++) {
                        int sx = std::max(0, std::min(w - 1, x + kx));
                        int sy = std::max(0, std::min(h - 1, y + ky));
                        float gw = std::exp(-(float)(kx*kx + ky*ky) / (2.0f * blur_amount * blur_amount));
                        int si = (sy * w + sx) * 4;
                        r += rgba[si + 0] * gw;
                        g += rgba[si + 1] * gw;
                        b += rgba[si + 2] * gw;
                        wsum += gw;
                    }
                }
                r /= wsum; g /= wsum; b /= wsum;
                // Warm tint
                out[idx + 0] = clamp_u8(r * 1.1f);
                out[idx + 1] = clamp_u8(g * 0.95f);
                out[idx + 2] = clamp_u8(b * 0.85f);
            } else {
                // Cool tint background
                out[idx + 0] = (uint8_t)((unsigned)rgba[idx + 0] * 9 / 10);
                out[idx + 1] = (uint8_t)((unsigned)rgba[idx + 1] * 9 / 10);
                out[idx + 2] = clamp_u8(std::min(255.0f, (float)rgba[idx + 2] * 1.1f));
            }
            out[idx + 3] = 255;
        }
    }
}

static void effect_golden_hour(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float warmth = p.params[0];

    for (int i = 0; i < w * h; i++) {
        int idx = i * 4;
        float m = mask[i];

        if (m > 0.5f) {
            // Warm golden on person
            out[idx + 0] = clamp_u8((float)rgba[idx + 0] * (1.0f + warmth * 0.3f));
            out[idx + 1] = clamp_u8((float)rgba[idx + 1] * (1.0f + warmth * 0.15f));
            out[idx + 2] = clamp_u8((float)rgba[idx + 2] * (1.0f - warmth * 0.2f));
        } else {
            // Cool blue on background
            out[idx + 0] = clamp_u8((float)rgba[idx + 0] * (1.0f - warmth * 0.15f));
            out[idx + 1] = clamp_u8((float)rgba[idx + 1] * (1.0f - warmth * 0.05f));
            out[idx + 2] = clamp_u8((float)rgba[idx + 2] * (1.0f + warmth * 0.2f));
        }
        out[idx + 3] = 255;
    }
}

static void effect_double_exposure(
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int w, int h, const EffectParams& p)
{
    float blend = p.params[0];
    int pattern = (int)p.params[1];

    // Generate overlay pattern programmatically
    uint8_t* overlay = new uint8_t[(size_t)w * h * 4];
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = (y * w + x) * 4;
            float t;
            switch (pattern) {
            case 0: // Gradient
                t = (float)x / (float)w;
                break;
            case 1: // Grid
                t = ((x / 40) + (y / 40)) % 2 == 0 ? 0.0f : 1.0f;
                break;
            case 2: // Noise
                t = rng_float();
                break;
            case 3: // Radial
            default: {
                float dx = (float)x / (float)w - 0.5f;
                float dy = (float)y / (float)h - 0.5f;
                t = std::sqrt(dx * dx + dy * dy) * 1.4f;
                t = std::min(1.0f, t);
                break;
            }
            }
            overlay[idx + 0] = lerp_u8(p.color1_r, p.color2_r, t);
            overlay[idx + 1] = lerp_u8(p.color1_g, p.color2_g, t);
            overlay[idx + 2] = lerp_u8(p.color1_b, p.color2_b, t);
            overlay[idx + 3] = 255;
        }
    }

    // Blend overlay using mask as alpha
    for (int i = 0; i < w * h; i++) {
        int idx = i * 4;
        float a = blend * 0.5f; // Base blend
        float m = mask[i];
        // Person area: more overlay, background: less overlay (or vice versa)
        float fa = a * (1.0f + m * 0.5f);
        out[idx + 0] = lerp_u8(rgba[idx + 0], overlay[idx + 0], fa);
        out[idx + 1] = lerp_u8(rgba[idx + 1], overlay[idx + 1], fa);
        out[idx + 2] = lerp_u8(rgba[idx + 2], overlay[idx + 2], fa);
        out[idx + 3] = 255;
    }
    delete[] overlay;
}

// ─── Main Apply Function ──────────────────────────────────────────────────

bool effect_apply(
    const EffectParams& p,
    const uint8_t* rgba, const float* mask, uint8_t* out,
    int width, int height)
{
    if (!rgba || !mask || !out || width <= 0 || height <= 0) return false;

    rng_seed(p.seed);

    switch (p.type) {
    case SegmentEffect::BackgroundReplace:
        effect_bg_replace(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::InkSplash:
        effect_ink_splash(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::CyberpunkGrid:
        effect_cyberpunk_grid(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::GlitchBackground:
        effect_glitch_bg(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::PixelateBackground:
        effect_pixelate_bg(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::GlowSilhouette:
        effect_glow_silhouette(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::NeonOutline:
        effect_neon_outline(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::VanGoghPortrait:
        effect_van_gogh(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::Dramatic:
        effect_dramatic(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::ColorPop:
        effect_color_pop(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::Dreamy:
        effect_dreamy(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::GoldenHour:
        effect_golden_hour(rgba, mask, out, width, height, p);
        break;
    case SegmentEffect::DoubleExposure:
        effect_double_exposure(rgba, mask, out, width, height, p);
        break;
    }
    return true;
}
