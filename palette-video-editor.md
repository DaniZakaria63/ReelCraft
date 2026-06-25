# PALETTE: Android AI Video Editor

## A fully-local, privacy-first video editor where every preset is an AI effect powered by FFmpeg + on-device ML, running entirely in native C++ with JNI.

---

# 1. Goals

## 1.1 Core Mission
Build an Android video editing application where creative "presets" are powered by local AI models — each preset understands scene content (person, background, objects) and applies artistic transformations selectively, with zero cloud dependency.

## 1.2 Why Local
- **Privacy**: Video footage never leaves the device. No uploads to TikTok/CapCut servers.
- **Offline**: Works in airplane mode, subway, remote areas.
- **Cost**: Zero server inference costs. One download, infinite use.
- **Latency**: On-device NPU/GPU inference beats network round-trips.

## 1.3 Why C++ + JNI
- **FFmpeg is C/C++** — cannot be bypassed. Running it via JNI eliminates Java-Kotlin bridge overhead.
- **Performance**: Native memory management, direct hardware access, no GC pauses during frame processing.
- **Unified pipeline**: FFmpeg decode → AI inference → FFmpeg filter → FFmpeg encode stays in one process space.
- **Model portability**: TFLite, ONNX Runtime, QNN, and llama.cpp all have first-class C++ APIs. Java wrappers add latency.

## 1.4 Design Principles

| Principle | Meaning |
|---|---|
| **One Engine** | FFmpeg handles all pixel math. AI handles all semantic understanding. |
| **Minimal Models** | One segmentation model + one style model. 90% of features. Zero fine-tuning required to ship. |
| **Temporal First** | Every effect must be stable across frames. No per-frame flickering. |
| **Combinatoric Presets** | 3 independent layers × configurable params = infinite effects from 30 components. |
| **Progressive Download** | Core models (~50MB) ship with app. Heavy models (~300MB) download on first use. |

---

# 2. Big View

## 2.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│              Android Application (Kotlin/Compose)         │
│                   :app module                             │
│  ┌──────────────┐  ┌────────────────┐  ┌──────────────┐  │
│  │ TimelineScreen│  │ PresetPicker   │  │ PreviewView  │  │
│  └──────┬───────┘  └───────┬────────┘  └──────┬───────┘  │
│         │                  │                   │          │
│         └──────────────────┼───────────────────┘          │
│                            │ JNI                          │
│         ┌──────────────────▼──────────────────────────┐   │
│         │          NativeEngine (libreelcraft.so)      │   │
│         │          APP NATIVE — Effects + ML           │   │
│         │                                              │   │
│         │  ┌────────────────┐  ┌────────────────────┐  │   │
│         │  │  AI Pipeline   │  │  Effects            │  │   │
│         │  │ ┌────────────┐ │  │ ┌────────────────┐  │  │   │
│         │  │ │ SINet      │ │  │ │ Preset Recipe  │  │  │   │
│         │  │ │ (Seg.)     │ │  │ │ Parser         │  │  │   │
│         │  │ ├────────────┤ │  │ ├────────────────┤  │  │   │
│         │  │ │ Style      │ │  │ │ Compositor     │  │  │   │
│         │  │ │ Transfer   │ │  │ │ (Mask × Style) │  │  │   │
│         │  │ └────────────┘ │  │ ├────────────────┤  │  │   │
│         │  └────────────────┘  │ │ Temporal       │  │  │   │
│         │                      │ │ Smoother       │  │  │   │
│         │                      │ └────────────────┘  │  │   │
│         │  ┌────────────────────────────────────────┐ │  │   │
│         │  │   FFmpeg C-API Consumer                │ │  │   │
│         │  │   (dlopen + dlsym → libffmpeg.so)     │ │  │   │
│         │  └────────────────┬───────────────────────┘ │  │   │
│         └───────────────────┼─────────────────────────┘  │   │
└─────────────────────────────┼────────────────────────────┘   │
                              │ C-API (dlopen/dlsym)            │
┌─────────────────────────────▼────────────────────────────┐   │
│           FFmpeg Engine (libffmpeg.so)                    │   │
│                :ffmpeg module                             │   │
│                                                          │   │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐  │   │
│  │   Decoder    │  │   Encoder    │  │  FilterGraph   │  │   │
│  │  (hwaccel:   │  │  (hwaccel:   │  │  Builder +     │  │   │
│  │  MediaCodec) │  │  MediaCodec) │  │  Runner        │  │   │
│  └──────┬───────┘  └──────┬───────┘  └───────┬────────┘  │   │
│         │                 │                  │            │   │
│         └─────────────────┼──────────────────┘            │   │
│                           │                               │   │
│         ┌─────────────────▼──────────────────────────┐    │   │
│         │    Prebuilt FFmpeg .so                      │    │   │
│         │    libavcodec, libavformat, libavfilter,    │    │   │
│         │    libswscale, libswresample, libavutil     │    │   │
│         └────────────────────────────────────────────┘    │   │
└───────────────────────────────────────────────────────────┘   │
```

## 2.2 Data Flow (Per Video Frame)

```
                  ┌── MODULE ──┐
                  │  :ffmpeg   │  ←── libffmpeg.so (C-API)
                  │  :app      │  ←── libreelcraft.so (JNI)
                  └────────────┘

Raw Frame (AVFrame)
    │
    ▼
╔═════════════════════════════════╗
║ 1. Decode via FFmpeg            ║  ← :ffmpeg MODULE
║    (hwaccel: MediaCodec)        ║
╚══════════╤══════════════════════╝
           │  ── RGB frame data passed via C-API return ──▶
           ▼
╔═════════════════════════════════╗
║ 2. Downscale to 224×224         ║  ← :app MODULE
║    (libswscale)                 ║
╚══════════╤══════════════════════╝
           │
           ▼
╔═════════════════════════════════╗
║ 3. SINet Inference              ║  ← :app MODULE
║    → raw mask (224×224)         ║     ~5ms on NPU
╚══════════╤══════════════════════╝
           │
           ▼
╔═════════════════════════════════╗
║ 4. Upscale mask to              ║  ← :app MODULE
║    original resolution          ║     ~1ms (libswscale)
╚══════════╤══════════════════════╝
           │
           ▼
╔═════════════════════════════════╗
║ 5. Temporal Smoother            ║  ← :app MODULE
║    → clean video mask           ║     Prevents flicker
╚══════════╤══════════════════════╝
           │
           ▼
╔═════════════════════════════════╗
║ 6. Feather Zone Generator       ║  ← :app MODULE
║    → soft mask (0.0 → 1.0)     ║     Creates seamless transition
╚══════════╤══════════════════════╝
           │
           ▼
╔═════════════════════════════════╗
║ 7. Style Application            ║  ← :app MODULE
║    (FFmpeg filter graph via     ║     C-API call to :ffmpeg
║     C-API, or neural style)    ║     or TFLite inference
╚══════════╤══════════════════════╝
           │
           ▼
╔═════════════════════════════════╗
║ 8. Composite:                   ║  ← :app MODULE
║    result = mask × person       ║     Per-pixel blend
║          + (1-mask) × bg        ║
╚══════════╤══════════════════════╝
           │  ── composited RGB frame ──▶
           ▼
╔═════════════════════════════════╗
║ 9. Encode via FFmpeg            ║  ← :ffmpeg MODULE
║    (hwaccel: MediaCodec)        ║     C-API call
╚════════════════════════════════╝
```

## 2.3 Keyframe-Based Mask Propagation (Performance)

Running SINet on **every frame** is wasteful. Instead:

```
Timeline:  KF    F1    F2    F3    F4    KF    F5    F6    F7    KF
           │                              │                        │
SINet:    RUN                           RUN                       RUN
           │                              │                        │
Propagate:  │←── motion vectors ─────────→│←── motion vectors ────→│
           │        (FFmpeg mestimate)     │                        │
```

- SINet runs on **keyframes** (every 10-15 frames)
- Between keyframes, mask is propagated via FFmpeg `mestimate` + `mcompensate`
- Propagated mask is smoothed with the previous SINet mask to prevent drift

---

# 3. Features

## 3.1 Preset System

Every preset is a **recipe card** combining 3 independent layers:

```
Preset: "Van Gogh Portrait"
├── Mask Target: Background
├── Style: Van Gogh (oil brush, impasto)
├── Intensity: 70%
├── Feather: 15px
├── Style Animation: Subtle swirl rotation (+2° per frame)
└── Extra: Edge glow around silhouette

Preset: "Cyberpunk Me"
├── Mask Target: Person
├── Style: Neon glow + cyberpunk color grade
├── Intensity: 85%
├── Feather: 5px
├── Style Animation: Pulsing neon at 120bpm
└── Extra: Grid overlay on background

Preset: "Sketch World"
├── Mask Target: Everything
├── Style: Pencil sketch (different density: bg=light, person=detailed)
├── Intensity: 100%
├── Feather: 0px (no blend, full replace)
├── Style Animation: Hatch lines rotate 1° per frame
└── Extra: Paper texture overlay
```

## 3.2 Feature List

### Core Editing
| Feature | Implementation |
|---|---|
| Timeline with trim/split | FFmpeg `trim` + `concat` filters |
| Multiple video/audio tracks | FFmpeg overlay + amix |
| Speed ramp (slow/fast) | FFmpeg `setpts` |
| Reverse clip | FFmpeg `reverse` |
| Transitions (crossfade, wipe) | FFmpeg `xfade` |
| Text/overlay | FFmpeg `drawtext` + `overlay` |

### AI-Powered Presets
| Preset Class | Mask Target | Style | Animation |
|---|---|---|---|
| Van Gogh | Background | Oil brush texture transfer | Stroke rotation |
| Random Line Art | Background | Edge-detection + sketch lines | Lines regenerate per frame |
| Watercolor | Background | Diffuse color bleeding | Color spread animation |
| Cyberpunk | Person | Neon edge + color grade | Pulsing glow |
| Pencil Sketch | Full scene | Hatch density varies by region | Cross-hatch angle sweep |
| Pixel Art | Full scene | Quantized palette + dither | Palette shift animation |
| Glitch | Person only | Channel offset + corruption | Random glitch bursts |
| Ink Splash | Background | Voronoi ink drops | Drops expand outward |
| Silhouette Glow | Person edge | Edges only → neon outline | Light travels along contour |
| Ghost Trail | Person | Semi-transparent previous frames | Trail decays over time |

### Preset Configuration
- **Intensity slider** (0-100%): Blends between original and styled
- **Feather slider** (0-50px): Softness of mask edge transition
- **Animation speed** (0-200%): How fast the style evolves over time
- **Style seed**: Randomization seed for generative styles (line art, ink splash)
- **Invert mask**: Swap what gets styled (person ↔ background)
- **Save as new preset**: Current configuration → named recipe

## 3.3 Export
- Resolutions: 480p, 720p, 1080p
- Codec: H.264/H.265 via MediaCodec hardware encoding
- Container: MP4
- Target: Gallery share, Instagram/TikTok/Reels aspect ratios

---

# 4. Technical Approach

## 4.1 Project Structure

```
palette/                              # Root project
│
├── app/                              # ─── :app MODULE ───
│   │                                 #     Kotlin UI + Effects/ML engine
│   ├── build.gradle.kts              #     Depends on :ffmpeg
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/palette/editor/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/                   # Jetpack Compose UI
│   │   │   │   ├── TimelineScreen.kt
│   │   │   │   ├── PresetPicker.kt
│   │   │   │   └── PreviewView.kt
│   │   │   └── bridge/               # JNI bridge classes
│   │   │       ├── NativeEngine.kt
│   │   │       └── NativeModels.kt
│   │   ├── cpp/                      # Native C++ (libreelcraft.so)
│   │   │   ├── CMakeLists.txt        # Builds libreelcraft.so
│   │   │   ├── core/
│   │   │   │   ├── engine.h          # Main engine interface
│   │   │   │   ├── engine.cpp
│   │   │   │   ├── pipeline.h        # Frame processing pipeline
│   │   │   │   └── pipeline.cpp
│   │   │   ├── ffmpeg_client/        # C-API consumer for :ffmpeg module
│   │   │   │   ├── ffmpeg_api.h      # Function pointer structs from dlopen
│   │   │   │   └── ffmpeg_client.h   # Wraps dlopen/dlsym calls
│   │   │   │   └── ffmpeg_client.cpp
│   │   │   ├── ml/
│   │   │   │   ├── sinet.h           # SINet segmentation wrapper
│   │   │   │   ├── sinet.cpp
│   │   │   │   ├── style_transfer.h  # Style transfer model wrapper
│   │   │   │   ├── style_transfer.cpp
│   │   │   │   ├── tflite_backend.h  # TFLite C++ inference
│   │   │   │   ├── tflite_backend.cpp
│   │   │   │   └── model_manager.h   # Model lifecycle (load/unload/swap)
│   │   │   │   └── model_manager.cpp
│   │   │   ├── effects/
│   │   │   │   ├── preset.h          # Preset recipe parser
│   │   │   │   ├── preset.cpp
│   │   │   │   ├── compositor.h      # Mask + style blend
│   │   │   │   ├── compositor.cpp
│   │   │   │   ├── temporal.h        # Temporal mask smoother
│   │   │   │   └── temporal.cpp
│   │   │   └── jni/
│   │   │       ├── jni_bridge.cpp    # JNI entry points
│   │   │       └── jni_helpers.h
│   │   └── assets/
│   │       ├── models/
│   │       │   ├── sinet_quant.tflite
│   │       │   ├── mediapipe_selfie.tflite
│   │       │   └── style_base.tflite
│   │       └── presets/
│   │           └── default_recipes.json
│
├── ffmpeg/                           # ─── :ffmpeg MODULE ───
│   │                                 #     Prebuilt FFmpeg wrappers
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt        # Builds libffmpeg.so
│   │   │   ├── api/                  # Public C-API exposed to app module
│   │   │   │   ├── ffmpeg_engine.h   # ← Single header: all public functions
│   │   │   │   └── ffmpeg_types.h    # Shared types (FfmpegFrame, etc.)
│   │   │   ├── decoder.h             # Video decode (MediaCodec hwaccel)
│   │   │   ├── decoder.cpp
│   │   │   ├── encoder.h             # Video encode
│   │   │   ├── encoder.cpp
│   │   │   ├── filter_graph.h        # FFmpeg filter graph builder
│   │   │   ├── filter_graph.cpp
│   │   │   └── ffmpeg/               # FFmpeg headers (prebuilt)
│   │   │       └── include/
│   │   │           ├── libavcodec/
│   │   │           ├── libavformat/
│   │   │           ├── libavfilter/
│   │   │           ├── libavutil/
│   │   │           ├── libswscale/
│   │   │           └── libswresample/
│   │   └── jniLibs/
│   │       └── arm64-v8a/            # Prebuilt FFmpeg .so files
│   │           ├── libavcodec.so
│   │           ├── libavformat.so
│   │           ├── libavfilter.so
│   │           ├── libavutil.so
│   │           ├── libswscale.so
│   │           └── libswresample.so
│
└── tools/
    └── model_converter.py            # Script to convert models to TFLite
```

## 4.2 Native Engine Interface

The C++ engine exposes a minimal JNI surface to Kotlin:

```cpp
// jni_bridge.cpp — All JNI methods

extern "C" {

// Lifecycle
JNIEXPORT jlong JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeCreate(
    JNIEnv* env, jobject thiz, jstring model_path);

JNIEXPORT void JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong handle);

// Pipeline control
JNIEXPORT void JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeOpenVideo(
    JNIEnv* env, jobject thiz, jlong handle, jstring video_path);

JNIEXPORT jboolean JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeDecodeNextFrame(
    JNIEnv* env, jobject thiz, jlong handle,
    jobject bitmap);  // Output: decoded frame as Android Bitmap

// Preset application
JNIEXPORT void JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeApplyPreset(
    JNIEnv* env, jobject thiz, jlong handle,
    jstring preset_json);  // Recipe as JSON string

// Preview (single frame, real-time)
JNIEXPORT void JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeRenderPreview(
    JNIEnv* env, jobject thiz, jlong handle,
    jobject input_bitmap, jobject output_bitmap,
    jstring preset_json);

// Export
JNIEXPORT void JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeExport(
    JNIEnv* env, jobject thiz, jlong handle,
    jstring output_path, jstring preset_json,
    jobject progress_callback);

}
```

## 4.3 Model Loading (Native)

```cpp
// model_manager.h

struct ModelSpec {
    std::string path;
    ModelType type;        // SINET, MEDIAPIPE_SELFIE, STYLE_BASE
    ModelBackend backend;  // TFLITE, QNN, ONNX
    bool quantized;        // int8 or float32
    size_t input_width;
    size_t input_height;
    size_t input_channels; // 3 (RGB)
};

class ModelManager {
public:
    bool loadModel(const ModelSpec& spec);
    bool unloadModel(ModelType type);
    bool isLoaded(ModelType type) const;
    InferenceBackend* getBackend(ModelType type);
    
    // Runs segmentation, returns float mask buffer
    // mask is height × width × 1 (values 0.0 = bg, 1.0 = person)
    std::vector<float> runSegmentation(
        ModelType type,
        const uint8_t* rgb_data,
        int width, int height);
    
    // Runs style transfer, returns styled RGB buffer
    std::vector<uint8_t> runStyleTransfer(
        const uint8_t* rgb_data,
        int width, int height,
        const StyleParams& params);

private:
    std::unordered_map<ModelType, std::unique_ptr<InferenceBackend>> backends_;
    TFLiteBackend tflite_;
    #ifdef USE_QNN
    QNNBackend qnn_;
    #endif
};
```

```cpp
// tflite_backend.h

class TFLiteBackend : public InferenceBackend {
public:
    bool load(const ModelSpec& spec) override;
    bool unload() override;
    
    // Synchronous inference
    InferenceResult run(const float* input, size_t input_size) override;
    
    // Access model details
    int inputWidth() const { return input_width_; }
    int inputHeight() const { return input_height_; }
    int outputSize() const { return output_size_; }
    
private:
    std::unique_ptr<tflite::FlatBufferModel> model_;
    std::unique_ptr<tflite::Interpreter> interpreter_;
    int input_tensor_index_;
    int output_tensor_index_;
    int input_width_;
    int input_height_;
    int output_size_;
    
    // GPU delegate for faster inference
    tflite::TfLiteDelegate* gpu_delegate_ = nullptr;
};
```

## 4.4 Segmentation Pipeline

```cpp
// sinet.cpp

std::vector<float> SINet::run(const uint8_t* rgb_data, int width, int height) {
    // Step 1: Downscale to 224×224 using libswscale
    SwsContext* sws = sws_getContext(
        width, height, AV_PIX_FMT_RGB24,
        224, 224, AV_PIX_FMT_RGB24,
        SWS_BILINEAR, nullptr, nullptr, nullptr);
    
    uint8_t* resized = new uint8_t[224 * 224 * 3];
    uint8_t* src_slice[] = { const_cast<uint8_t*>(rgb_data) };
    int src_stride[] = { width * 3 };
    uint8_t* dst_slice[] = { resized };
    int dst_stride[] = { 224 * 3 };
    sws_scale(sws, src_slice, src_stride, 0, height, dst_slice, dst_stride);
    sws_freeContext(sws);
    
    // Step 2: Normalize to float [0,1]
    std::vector<float> input(224 * 224 * 3);
    for (int i = 0; i < 224 * 224 * 3; i++) {
        input[i] = resized[i] / 255.0f;
    }
    delete[] resized;
    
    // Step 3: Run TFLite inference
    auto result = backend_->run(input.data(), input.size());
    
    // Step 4: Sigmoid activation (SINet raw output is logits)
    std::vector<float> mask(224 * 224);
    for (int i = 0; i < 224 * 224; i++) {
        mask[i] = 1.0f / (1.0f + std::exp(-result.output[i]));
    }
    
    return mask;
}
```

## 4.5 Temporal Smoother

```cpp
// temporal.h

class TemporalSmoother {
public:
    // Call every frame with the raw mask from SINet
    // Returns smoothed mask
    std::vector<float> smooth(
        const std::vector<float>& raw_mask,
        int frame_number);
    
    // On keyframe: store SINet mask as anchor
    void setKeyframe(const std::vector<float>& sinet_mask);
    
    // Reset (called when preset changes or seeking)
    void reset();

private:
    // Ring buffer of last N masks (N = 5)
    static constexpr int kHistorySize = 5;
    std::vector<float> history_[kHistorySize];
    int current_index_ = 0;
    
    // Keyframe anchor
    std::vector<float> keyframe_mask_;
    int keyframe_interval_ = 15;  // Frames between keyframes
    
    // Motion-compensated mask from FFmpeg
    std::vector<float> propagated_mask_;
};
```

```cpp
// temporal.cpp

std::vector<float> TemporalSmoother::smooth(
    const std::vector<float>& raw_mask, int frame_number) {
    
    if (frame_number % keyframe_interval_ == 0) {
        // Keyframe: use SINet direct, store as anchor
        keyframe_mask_ = raw_mask;
        propagated_mask_ = raw_mask;
    } else {
        // Propagate mask using motion vectors
        // (FFmpeg estimates motion between current frame and keyframe)
        propagated_mask_ = ffmpeg_propagate_mask(keyframe_mask_);
    }
    
    // Add raw mask to ring buffer
    history_[current_index_] = propagated_mask_;
    current_index_ = (current_index_ + 1) % kHistorySize;
    
    // Temporal median filter (pixel-wise across history)
    size_t mask_size = raw_mask.size();
    std::vector<float> smoothed(mask_size);
    
    for (size_t i = 0; i < mask_size; i++) {
        std::vector<float> values;
        for (int h = 0; h < kHistorySize; h++) {
            values.push_back(history_[h][i]);
        }
        std::sort(values.begin(), values.end());
        smoothed[i] = values[kHistorySize / 2];  // Median
    }
    
    return smoothed;
}
```

## 4.6 Feather Zone Generator

```cpp
// compositor.cpp (feather section)

struct FeatherParams {
    int feather_radius;    // Pixels of feather (0-50)
    float inner_threshold; // Pixels inside mask edge that are 100% person (default 0.0)
    float outer_threshold; // Pixels outside mask edge that are 100% bg (default 0.0)
};

// Generate feather mask from binary mask
// Input: mask (values 0.0 or 1.0)
// Output: feather_mask (values smoothly transition 0.0 → 1.0)
std::vector<float> generateFeatherMask(
    const std::vector<float>& binary_mask,
    int mask_width, int mask_height,
    const FeatherParams& params) {
    
    size_t size = binary_mask.size();
    std::vector<float> feather(size);
    
    // Distance transform approximated with multiple box blurs
    // Step 1: Cast to uint8 for OpenCV/compat
    cv::Mat mask_float(mask_height, mask_width, CV_32F, const_cast<float*>(binary_mask.data()));
    cv::Mat dist;
    
    // Distance from mask edge
    cv::distanceTransform(
        mask_float > 0.5f ? 255 : 0,
        dist,
        cv::DIST_L2, cv::DIST_MASK_5);
    
    // Step 2: Apply feather radius as sigmoid on distance
    float sigma = params.feather_radius > 0 ? params.feather_radius : 10.0f;
    for (int y = 0; y < mask_height; y++) {
        for (int x = 0; x < mask_width; x++) {
            float d = dist.at<float>(y, x);
            // Sigmoid centered at distance = 0
            feather[y * mask_width + x] = 1.0f / (1.0f + std::exp(-d / sigma * 3.0f));
        }
    }
    
    return feather;
}
```

## 4.7 Style Application (Per-Frame with Animation)

```cpp
// style_transfer.h

struct StyleParams {
    std::string style_name;     // "van_gogh", "line_art", "watercolor", etc.
    float intensity;            // 0.0 (none) → 1.0 (full)
    float animation_speed;      // 0.0 (static) → 2.0 (double speed)
    int seed;                   // Randomization seed for generative effects
    float color_influence;      // 0.0 → 1.0 (how much original color bleeds through)
};

class StyleTransfer {
public:
    // Apply style to a single frame region
    // Input: RGB frame data (could be full frame or mask-cropped region)
    // Output: Styled RGB frame
    std::vector<uint8_t> apply(
        const std::vector<uint8_t>& rgb_data,
        int width, int height,
        const StyleParams& params,
        int frame_number);  // frame_number drives animation
    
    enum AnimationType {
        STATIC,          // Same style every frame
        ROTATE,          // Style parameters rotate (e.g., brush angle)
        OSCILLATE,       // Style pulses in/out
        NOISE_DRIVEN,    // Perlin noise drives parameter evolution
        CUSTOM           // User-defined parameter curve
    };

private:
    // Animation state
    struct AnimState {
        float brush_angle = 0.0f;
        float stroke_density = 0.5f;
        float color_variance = 0.3f;
    };
    AnimState anim_state_;
    
    // Update animation parameters for current frame
    void advanceAnimation(const StyleParams& params, int frame_number);
    
    // FFmpeg filter chain builder for this style
    std::string buildStyleFilterGraph(const StyleParams& params, int frame_number);
};
```

```cpp
// style_transfer.cpp — Example: Van Gogh style

void StyleTransfer::advanceAnimation(const StyleParams& params, int frame_number) {
    float speed = params.animation_speed;
    float t = frame_number * speed;
    
    // Van Gogh: brush stroke angle slowly rotates
    anim_state_.brush_angle = std::fmod(t * 2.0f, 360.0f);
    
    // Stroke density oscillates with noise
    anim_state_.stroke_density = 0.5f + 0.3f * std::sin(t * 0.1f);
    
    // Color variance pulses slightly
    anim_state_.color_variance = 0.3f + 0.1f * std::sin(t * 0.05f);
}

std::string StyleTransfer::buildStyleFilterGraph(
    const StyleParams& params, int frame_number) {
    
    advanceAnimation(params, frame_number);
    
    // This FFmpeg filter graph chains multiple filters
    // to approximate the Van Gogh look using native FFmpeg filters.
    // No neural network needed for the style itself —
    // the network handles segmentation, FFmpeg handles the artistic look.
    
    std::string filter = "";
    
    // Step 1: Edge enhancement for impasto effect
    filter += "edgedetect=low=0.1:high=0.3:mode=canny, ";
    
    // Step 2: Blend edges with original (gives brush-stroke feel)
    filter += "blend=all_mode=screen:all_opacity=" +
              std::to_string(anim_state_.stroke_density) + ", ";
    
    // Step 3: Color quantization for oil-paint palette
    filter += "palettegen=max_colors=32:stats_mode=full, ";
    filter += "paletteuse=dither=bayer:bayer_scale=5, ";
    
    // Step 4: Curves adjustment for Van Gogh's warm tones
    filter += "curves=vintage, ";
    
    // Step 5: Subtle rotation of brush strokes (simulated with morpho)
    filter += "rotate=" + std::to_string(anim_state_.brush_angle) + "*PI/180:fill_color=white, ";
    
    // Step 6: Strong sharpen for impasto texture
    filter += "unsharp=lx=5:ly=5:la=2.0";
    
    return filter;
}
```

## 4.8 Composite: Mask × Style × Original

```cpp
// compositor.h

struct CompositorInput {
    std::vector<uint8_t> original_rgb;    // Original frame pixels
    std::vector<float> feather_mask;       // Smooth mask (0.0 = bg, 1.0 = person)
    std::vector<uint8_t> styled_bg_rgb;    // Background after style transfer
    std::vector<uint8_t> styled_person_rgb;// Person after style transfer (optional)
    int width;
    int height;
    bool style_person;                     // true = also style the person
};

// Output: composited frame
std::vector<uint8_t> composite(const CompositorInput& input) {
    size_t size = input.width * input.height * 3;
    std::vector<uint8_t> output(size);
    
    for (int y = 0; y < input.height; y++) {
        for (int x = 0; x < input.width; x++) {
            int idx = (y * input.width + x);
            int rgb_idx = idx * 3;
            
            float mask_val = input.feather_mask[idx];
            
            // Get background styled pixel
            uint8_t bg_r = input.styled_bg_rgb[rgb_idx + 0];
            uint8_t bg_g = input.styled_bg_rgb[rgb_idx + 1];
            uint8_t bg_b = input.styled_bg_rgb[rgb_idx + 2];
            
            // Get person pixel (original or styled)
            uint8_t person_r, person_g, person_b;
            if (input.style_person) {
                person_r = input.styled_person_rgb[rgb_idx + 0];
                person_g = input.styled_person_rgb[rgb_idx + 1];
                person_b = input.styled_person_rgb[rgb_idx + 2];
            } else {
                person_r = input.original_rgb[rgb_idx + 0];
                person_g = input.original_rgb[rgb_idx + 1];
                person_b = input.original_rgb[rgb_idx + 2];
            }
            
            // Blend: result = mask × person + (1 - mask) × bg
            float m = mask_val;
            output[rgb_idx + 0] = static_cast<uint8_t>(m * person_r + (1 - m) * bg_r);
            output[rgb_idx + 1] = static_cast<uint8_t>(m * person_g + (1 - m) * bg_g);
            output[rgb_idx + 2] = static_cast<uint8_t>(m * person_b + (1 - m) * bg_b);
        }
    }
    
    return output;
}
```

## 4.9 Preset Recipe Format

```json
{
  "name": "Van Gogh Portrait",
  "version": 1,
  "mask": {
    "target": "background",
    "model": "sinet",
    "feather_px": 15,
    "threshold": 0.5
  },
  "style": {
    "name": "van_gogh",
    "params": {
      "intensity": 0.7,
      "color_influence": 0.3,
      "stroke_density": 0.6
    },
    "animation": {
      "type": "rotate",
      "speed": 1.0,
      "param": "brush_angle",
      "rate": 2.0
    }
  },
  "person_treatment": "original",
  "export": {
    "resolution": "1080p",
    "codec": "h264",
    "bitrate": "8M"
  }
}
```

## 4.10 FFmpeg Integration

### 4.10.1 :ffmpeg Module — Public C-API (ffmpeg_engine.h)

The `:ffmpeg` module exposes a plain-C API via `dlopen`/`dlsym` so the app module can call it at runtime without compile-time linking. Only opaque handles and POD types cross the boundary — no C++ name mangling, no STL.

```c
// ffmpeg_engine.h — Public API exposed by libffmpeg.so
#ifndef FFMPEG_ENGINE_H
#define FFMPEG_ENGINE_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── Error codes ────────────────────────────────────── */
typedef enum {
    FFMPEG_OK = 0,
    FFMPEG_ERR_OPEN,
    FFMPEG_ERR_DECODE,
    FFMPEG_ERR_ENCODE,
    FFMPEG_ERR_FILTER,
    FFMPEG_ERR_MEMORY,
} FfmpegError;

/* ── Opaque handles ──────────────────────────────────── */
typedef struct FfmpegDecoder   FfmpegDecoder;
typedef struct FfmpegEncoder   FfmpegEncoder;
typedef struct FfmpegFilterGraph FfmpegFilterGraph;

/* ── Frame data (POD, no AVFrame leak) ──────────────── */
typedef struct {
    uint8_t* rgb_data;     // RGB24 pixel data (caller must free via ffmpeg_free_frame)
    int      width;
    int      height;
    int64_t  pts;          // Presentation timestamp
} FfmpegFrame;

/* ── Decoder ─────────────────────────────────────────── */
FfmpegDecoder* ffmpeg_decoder_open(const char* video_path);
void           ffmpeg_decoder_close(FfmpegDecoder* dec);
int            ffmpeg_decoder_width(FfmpegDecoder* dec);
int            ffmpeg_decoder_height(FfmpegDecoder* dec);
int            ffmpeg_decoder_fps(FfmpegDecoder* dec);
int            ffmpeg_decoder_frame_count(FfmpegDecoder* dec);
FfmpegError    ffmpeg_decoder_decode_next(FfmpegDecoder* dec, FfmpegFrame* out);

/* ── Encoder ─────────────────────────────────────────── */
FfmpegEncoder* ffmpeg_encoder_open(const char* output_path,
                                   int width, int height, int fps, int bitrate);
void           ffmpeg_encoder_close(FfmpegEncoder* enc);
FfmpegError    ffmpeg_encoder_encode_frame(FfmpegEncoder* enc, const FfmpegFrame* frame);
void           ffmpeg_encoder_finalize(FfmpegEncoder* enc);

/* ── Filter Graph ────────────────────────────────────── */
FfmpegFilterGraph* ffmpeg_filter_graph_create(void);
void               ffmpeg_filter_graph_destroy(FfmpegFilterGraph* fg);
FfmpegError        ffmpeg_filter_graph_parse(FfmpegFilterGraph* fg,
                                             const char* description,
                                             int input_width, int input_height);
FfmpegError        ffmpeg_filter_graph_process(FfmpegFilterGraph* fg,
                                               const FfmpegFrame* input,
                                               FfmpegFrame* output);

/* ── Frame memory ────────────────────────────────────── */
void ffmpeg_free_frame(FfmpegFrame* frame);

/* ── Version / info ──────────────────────────────────── */
const char* ffmpeg_version(void);

#ifdef __cplusplus
}
#endif

#endif // FFMPEG_ENGINE_H
```

### 4.10.2 :ffmpeg Module — Decoder Wrapper

```cpp
// decoder.cpp — Internal implementation, not exposed to app module
#include "decoder.h"
#include "api/ffmpeg_engine.h"

extern "C" {

FfmpegDecoder* ffmpeg_decoder_open(const char* video_path) {
    auto* dec = new FfmpegDecoder();
    // ... AVFormatContext, AVCodecContext, sws, etc.
    return dec;
}

FfmpegError ffmpeg_decoder_decode_next(FfmpegDecoder* dec, FfmpegFrame* out) {
    AVFrame* frame = dec->nextFrame();
    if (!frame) return FFMPEG_ERR_DECODE;

    out->width  = dec->width();
    out->height = dec->height();
    out->pts    = frame->pts;
    out->rgb_data = new uint8_t[out->width * out->height * 3];

    // libswscale: AVFrame (NV12/MediaCodec) → RGB24
    sws_scale(dec->swsCtx(),
              frame->data, frame->linesize, 0, dec->height(),
              &out->rgb_data, &out->width * 3);

    av_frame_free(&frame);
    return FFMPEG_OK;
}

} // extern "C"
```

### 4.10.3 :ffmpeg Module — Filter Graph

```cpp
// filter_graph.cpp
extern "C" {

FfmpegError ffmpeg_filter_graph_process(FfmpegFilterGraph* fg,
                                        const FfmpegFrame* input,
                                        FfmpegFrame* output) {
    AVFrame* av_in = av_frame_alloc();
    av_in->width  = input->width;
    av_in->height = input->height;
    av_in->format = AV_PIX_FMT_RGB24;
    av_image_fill_arrays(av_in->data, av_in->linesize,
                         input->rgb_data, AV_PIX_FMT_RGB24,
                         input->width, input->height, 1);

    av_buffersrc_add_frame(fg->src(), av_in);
    AVFrame* av_out = av_frame_alloc();
    int ret = av_buffersink_get_frame(fg->sink(), av_out);

    if (ret < 0) {
        av_frame_free(&av_in);
        av_frame_free(&av_out);
        return FFMPEG_ERR_FILTER;
    }

    output->width  = av_out->width;
    output->height = av_out->height;
    output->pts    = av_out->pts;
    output->rgb_data = new uint8_t[output->width * output->height * 3];

    // Convert back to RGB24 (filter may change pix_fmt)
    sws_scale(fg->outSwsCtx(),
              av_out->data, av_out->linesize, 0, av_out->height,
              &output->rgb_data, &output->width * 3);

    av_frame_free(&av_in);
    av_frame_free(&av_out);
    return FFMPEG_OK;
}

} // extern "C"
```

### 4.10.4 :app Module — FFmpeg Client (dlopen Consumer)

The app module cannot link to `libffmpeg.so` at CMake time (separate Gradle modules). Instead it uses `dlopen` + `dlsym` at runtime to resolve the C-API function pointers.

```cpp
// ffmpeg_client.h — :app module's consumer of :ffmpeg C-API
#include <dlfcn.h>
#include "ffmpeg_engine.h"  // Copied or symlinked from :ffmpeg module

class FfmpegClient {
public:
    bool load() {
        handle_ = dlopen("libffmpeg.so", RTLD_NOW | RTLD_LOCAL);
        if (!handle_) return false;

        #define LOAD_SYM(name) \
            name = reinterpret_cast<decltype(name)>(dlsym(handle_, #name))

        LOAD_SYM(ffmpeg_decoder_open);
        LOAD_SYM(ffmpeg_decoder_close);
        LOAD_SYM(ffmpeg_decoder_decode_next);
        LOAD_SYM(ffmpeg_encoder_open);
        LOAD_SYM(ffmpeg_encoder_close);
        LOAD_SYM(ffmpeg_encoder_encode_frame);
        LOAD_SYM(ffmpeg_encoder_finalize);
        LOAD_SYM(ffmpeg_filter_graph_create);
        LOAD_SYM(ffmpeg_filter_graph_destroy);
        LOAD_SYM(ffmpeg_filter_graph_parse);
        LOAD_SYM(ffmpeg_filter_graph_process);
        LOAD_SYM(ffmpeg_free_frame);

        #undef LOAD_SYM
        return true;
    }

    void unload() { if (handle_) dlclose(handle_); }

    // Typedefs for each function pointer
    using DecoderOpenFn     = FfmpegDecoder*(*)(const char*);
    using DecodeNextFn     = FfmpegError(*)(FfmpegDecoder*, FfmpegFrame*);
    using FilterProcessFn  = FfmpegError(*)(FfmpegFilterGraph*, const FfmpegFrame*, FfmpegFrame*);
    // ... etc for all API functions

    DecoderOpenFn     ffmpeg_decoder_open     = nullptr;
    DecodeNextFn      ffmpeg_decoder_decode_next = nullptr;
    FilterProcessFn   ffmpeg_filter_graph_process = nullptr;
    // ...

private:
    void* handle_ = nullptr;
};
```

## 4.11 Performance Budget

| Operation | Module | Target Latency | Hardware |
|---|---|---|---|
| FFmpeg decode (MediaCodec) | `:ffmpeg` | < 5ms per frame | GPU |
| SINet inference (224×224) | `:app` | < 5ms per frame | NPU (Qualcomm) / GPU |
| Mask upscale (bilinear) | `:app` | < 1ms | CPU |
| Temporal smoother | `:app` | < 1ms | CPU |
| Feather generator | `:app` | < 2ms | CPU |
| Style filter (FFmpeg) | `:ffmpeg` (via C-API) | < 5ms per frame | GPU |
| Composite | `:app` | < 1ms | CPU |
| FFmpeg encode (MediaCodec) | `:ffmpeg` | < 5ms per frame | GPU |
| **Total (export, per frame)** | | **~25ms** | → **40fps** |
| **Total (preview, less filters)** | | **~10ms** | → **~60fps** |

---

## 4.12 Filter Data Flow Notes

### 4.12.1 Data Movement Across Module Boundary

```
 ┌──────────────────────────────────────────────────────────┐
 │  :app module (libreelcraft.so)                            │
 │                                                          │
 │  Decode call ──────────────▶ ┌─────────────────────────┐ │
 │  ffmpeg_.decoder_decode_next │  :ffmpeg C-API           │ │
 │  ◀──── FfmpegFrame (RGB24) ──│  AVFrame → sws → RGB24  │ │
 │                              └─────────────────────────┘ │
 │       │                                                  │
 │       ▼                                                  │
 │  ┌──────────────┐  ┌────────────────┐  ┌──────────────┐  │
 │  │ SINet (TFLite)│  │ Temporal       │  │ Feather Gen  │  │
 │  │ 224×224 → mask│──│ Smoother       │──│ (distance     │  │
 │  └──────────────┘  └────────────────┘  │  transform)   │  │
 │                                        └───────┬──────┘  │
 │       ┌────────────────────────────────────────┘          │
 │       ▼                                                   │
 │  ┌──────────────┐                                         │
 │  │ Composite     │  ← mask × styled_person + (1-mask) × bg│
 │  └───────┬──────┘                                         │
 │          │                                                 │
 │          ▼                                                 │
 │  Encode call ───────────────▶ ┌─────────────────────────┐ │
 │  ffmpeg_.encoder_encode_frame │  :ffmpeg C-API           │ │
 │  (FfmpegFrame) ───────────────▶│  RGB24 → sws → AVFrame  │ │
 │                              │  → MediaCodec encode     │ │
 │                              └─────────────────────────┘ │
 └──────────────────────────────────────────────────────────┘
```

### 4.12.2 Key Rules

| Rule | Rationale |
|---|---|
| **No AVFrame types escape `:ffmpeg`** | The app module never includes FFmpeg headers. All frame data crosses the boundary as `FfmpegFrame` (POD struct with raw RGB24 pixel pointer). |
| **RGB24 is the canonical interchange format** | Both modules agree on RGB24 for pixel data. `:ffmpeg` converts from NV12 (MediaCodec) → RGB24 on decode, and RGB24 → NV12 on encode. |
| **Memory ownership is explicit** | The module that allocates a `FfmpegFrame::rgb_data` is responsible for freeing it via `ffmpeg_free_frame()`. The app module either copies out immediately or owns its own buffers. |
| **Filters run inside `:ffmpeg`** | `ffmpeg_filter_graph_process()` takes an input `FfmpegFrame` and allocates/outputs a new filtered `FfmpegFrame`. The filter graph string is built by the app module (it's just a string), but execution happens in `:ffmpeg`. |
| **No frame data is shared via JNI** | All FFmpeg↔app native communication stays in C/C++ across a single `dlopen` boundary. JNI is only used for Kotlin→app-native communication (UI → engine). |

### 4.12.3 Typical Frame Lifecycle

```
Frame N lifecycle:
─────────────────────────────────────────────────────────────
Step  Module  Action                                Buffer
────  ──────  ────────────────────────────────────  ─────────
1     :ffmpeg MediaCodec decode → NV12 frame        GPU/Codec
2     :ffmpeg sws_scale NV12→RGB24                  temp RGB24
3     :ffmpeg ←── C-API return ──→ FfmpegFrame      owned by caller
4     :app    Copy to frame_buffer_                 frame_buffer_
5     :app    ffmpeg_free_frame() (release temp)    freed
6     :app    Downscale 224×224 (sws)               resize_buf
7     :app    SINet inference → mask (float*)       mask_buffer_
8     :app    Upscale mask to full res (sws)        smooth_mask_
9     :app    Temporal median of last 5 masks       history ring
10    :app    Distance transform → feather mask     feather_buf
11    :app    Build filter description string       (string)
12    :ffmpeg → C-API: filter_graph_parse()         inside FG
13    :ffmpeg → C-API: filter_graph_process()       styled_frame
14    :app    Copy styled → styled_buffer_          styled_buf
15    :app    ffmpeg_free_frame()
16    :app    Composite: blend(orig, styled, mask)  output_buf
17    :ffmpeg → C-API: encoder_encode_frame()       encode input
18    :ffmpeg sws_scale RGB24→NV12                  temp NV12
19    :ffmpeg MediaCodec encode → bitstream         output file
─────────────────────────────────────────────────────────────
Total: 0 AVFrame objects cross the module boundary.
All FFmpeg internals stay sealed inside :ffmpeg.
```

### 4.12.4 Filter Graph Flow (Style Application)

When the app module wants to apply a style (e.g., Van Gogh):

```
App: "van_gogh" preset recipe
  │
  ▼
App: StyleTransfer::buildStyleFilterGraph(recipe, frame_number)
  │  Returns filter description string:
  │    "[0:v] edgedetect=low=0.1:high=0.3,
  │           blend=all_mode=screen:all_opacity=0.6,
  │           palettegen=max_colors=32,
  │           paletteuse=dither=bayer,
  │           curves=vintage [v]"
  │
  ▼  ──────────────────────────────────────────────────────────
  │  C-API call to :ffmpeg:
  │    ffmpeg_.ffmpeg_filter_graph_parse(fg, desc, w, h)
  │    ffmpeg_.ffmpeg_filter_graph_process(fg, &input, &output)
  │                                                          │
  ▼                                                          ▼
:ffmpeg module:                                        :ffmpeg module:
  AVFilterGraph* graph = avfilter_graph_alloc()           AVFrame* in = alloc()
  avfilter_graph_parse_ptr(graph, desc, ...)              av_buffersrc_add_frame(src, in)
  avfilter_graph_config(graph)                            AVFrame* out = alloc()
                                                          av_buffersink_get_frame(sink, out)
                                                          sws_scale(out → RGB24) → FfmpegFrame
```

The filter description is **built by the app module** (which understands preset recipes and animation timing) but **executed by the `:ffmpeg` module** (which has the FFmpeg headers and linked libraries). This cleanly separates the "what to do" from the "how to do it."

---

# 5. Implementation Snippet

## 5.1 JNI Bridge — Kotlin Side

```kotlin
// NativeEngine.kt
package com.palette.editor.bridge

class NativeEngine {
    private var nativeHandle: Long = 0

    companion object {
        init {
            System.loadLibrary("reelcraft")  // App module native library
        }
    }

    // Lifecycle
    external fun nativeCreate(modelPath: String): Long
    external fun nativeDestroy(handle: Long)

    // Video I/O
    external fun nativeOpenVideo(handle: Long, videoPath: String)
    external fun nativeDecodeNextFrame(handle: Long, bitmap: Bitmap): Boolean

    // Apply preset to a single frame (for real-time preview)
    external fun nativeRenderPreview(
        handle: Long,
        inputBitmap: Bitmap,
        outputBitmap: Bitmap,
        presetJson: String
    )

    // Full video export with preset
    external fun nativeExport(
        handle: Long,
        outputPath: String,
        presetJson: String,
        progressCallback: NativeProgressCallback
    )

    fun create(modelPath: String) {
        nativeHandle = nativeCreate(modelPath)
    }

    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }

    interface NativeProgressCallback {
        fun onProgress(percent: Int)
        fun onComplete()
        fun onError(message: String)
    }

    protected fun finalize() {
        destroy()
    }
}
```

## 5.2 JNI Bridge — C++ Side

```cpp
// jni_bridge.cpp

#include <jni.h>
#include <android/bitmap.h>
#include "core/engine.h"

static PaletteEngine* getEngine(JNIEnv* env, jlong handle) {
    return reinterpret_cast<PaletteEngine*>(handle);
}

extern "C" {

// ── Lifecycle ───────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeCreate(
    JNIEnv* env, jobject thiz, jstring model_path) {
    
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    auto* engine = new PaletteEngine();
    engine->initialize(std::string(path));
    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong handle) {
    
    delete getEngine(handle);
}

// ── Preview (single frame) ────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeRenderPreview(
    JNIEnv* env, jobject thiz, jlong handle,
    jobject input_bitmap, jobject output_bitmap,
    jstring preset_json) {
    
    auto* engine = getEngine(handle);
    
    // Lock Android bitmaps for direct pixel access
    AndroidBitmapInfo in_info, out_info;
    void* in_pixels = nullptr;
    void* out_pixels = nullptr;
    
    AndroidBitmap_getInfo(env, input_bitmap, &in_info);
    AndroidBitmap_getInfo(env, output_bitmap, &out_info);
    AndroidBitmap_lockPixels(env, input_bitmap, &in_pixels);
    AndroidBitmap_lockPixels(env, output_bitmap, &out_pixels);
    
    int width = in_info.width;
    int height = in_info.height;
    
    // Read preset JSON
    const char* preset_str = env->GetStringUTFChars(preset_json, nullptr);
    
    // Process frame through engine
    engine->renderPreview(
        static_cast<uint8_t*>(in_pixels),
        static_cast<uint8_t*>(out_pixels),
        width, height,
        std::string(preset_str));
    
    env->ReleaseStringUTFChars(preset_json, preset_str);
    AndroidBitmap_unlockPixels(env, input_bitmap);
    AndroidBitmap_unlockPixels(env, output_bitmap);
}

// ── Export ──────────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_palette_editor_bridge_NativeEngine_nativeExport(
    JNIEnv* env, jobject thiz, jlong handle,
    jstring output_path, jstring preset_json,
    jobject progress_callback) {
    
    auto* engine = getEngine(handle);
    
    const char* path = env->GetStringUTFChars(output_path, nullptr);
    const char* preset = env->GetStringUTFChars(preset_json, nullptr);
    
    // Get callback as global ref (must not be GC'd during export)
    jobject callback = env->NewGlobalRef(progress_callback);
    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_progress = env->GetMethodID(callback_class, "onProgress", "(I)V");
    jmethodID on_complete = env->GetMethodID(callback_class, "onComplete", "()V");
    jmethodID on_error = env->GetMethodID(callback_class, "onError", "(Ljava/lang/String;)V");
    
    // Progress reporter lambda
    auto progress_fn = [env, callback, on_progress](int percent) {
        env->CallVoidMethod(callback, on_progress, percent);
    };
    
    // Run export
    engine->exportVideo(
        std::string(path),
        std::string(preset),
        progress_fn);
    
    // Notify completion
    env->CallVoidMethod(callback, on_complete);
    env->DeleteGlobalRef(callback);
    
    env->ReleaseStringUTFChars(output_path, path);
    env->ReleaseStringUTFChars(preset_json, preset);
}

} // extern "C"
```

## 5.3 CMakeLists.txt

### ffmpeg/src/main/cpp/CMakeLists.txt — builds libffmpeg.so

```cmake
cmake_minimum_required(VERSION 3.22)
project("ffmpeg")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Prebuilt FFmpeg .so files ──────────────────────────
set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

macro(add_ffmpeg_lib NAME)
    add_library(${NAME} SHARED IMPORTED)
    set_target_properties(${NAME} PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/lib${NAME}.so)
endmacro()

add_ffmpeg_lib(avcodec)
add_ffmpeg_lib(avformat)
add_ffmpeg_lib(avfilter)
add_ffmpeg_lib(avutil)
add_ffmpeg_lib(swscale)
add_ffmpeg_lib(swresample)

# ── FFmpeg Engine Wrapper Library ──────────────────────
add_library(ffmpeg SHARED
    api/ffmpeg_engine.h      # Public C-API header (included for install)
    decoder.cpp
    encoder.cpp
    filter_graph.cpp
)

target_include_directories(ffmpeg PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ffmpeg/include           # FFmpeg prebuilt headers
)

target_link_libraries(ffmpeg
    avcodec avformat avfilter
    avutil swscale swresample
    android log
)
```

### app/src/main/cpp/CMakeLists.txt — builds libreelcraft.so

```cmake
cmake_minimum_required(VERSION 3.22)
project("reelcraft")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── TensorFlow Lite C++ ─────────────────────────────────
add_library(tensorflowlite SHARED IMPORTED)
set_target_properties(tensorflowlite PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../../third_party/tensorflow/lib/libtensorflowlite.so
)

# ── FFmpeg C-API header (compile only, no link) ────────
# The ffmpeg module's public header is included at compile time.
# At runtime, dlopen("libffmpeg.so") resolves symbols.
include_directories(
    ${CMAKE_SOURCE_DIR}/../../../ffmpeg/src/main/cpp/api
)

# ── Engine Native Library ───────────────────────────────
add_library(reelcraft SHARED
    # Core
    core/engine.cpp
    core/pipeline.cpp

    # FFmpeg client (dlopen wrapper)
    ffmpeg_client/ffmpeg_client.cpp

    # ML
    ml/sinet.cpp
    ml/style_transfer.cpp
    ml/tflite_backend.cpp
    ml/model_manager.cpp

    # Effects
    effects/preset.cpp
    effects/compositor.cpp
    effects/temporal.cpp

    # JNI
    jni/jni_bridge.cpp
)

target_link_libraries(reelcraft
    tensorflowlite
    android
    log      # __android_log_print
    z        # libgz for TFLite model decompression
)
```

## 5.4 Engine Core Loop (Export)

The app module's `PaletteEngine` uses `FfmpegClient` for all FFmpeg operations. No `AVFrame` or FFmpeg types appear in the app module — only `FfmpegFrame` POD structs cross the boundary.

```cpp
// engine.cpp — :app module

#include "ffmpeg_client/ffmpeg_client.h"
#include "ml/model_manager.h"
#include "effects/preset.h"
#include "effects/compositor.h"
#include "effects/temporal.h"

class PaletteEngine {
public:
    bool initialize(const std::string& model_dir);
    
    // Preview: process a single Bitmap frame
    void renderPreview(
        const uint8_t* input, uint8_t* output,
        int width, int height,
        const std::string& preset_json);
    
    // Export: process entire video file
    void exportVideo(
        const std::string& output_path,
        const std::string& input_path,
        const std::string& preset_json,
        std::function<void(int)> progress_callback);

private:
    FfmpegClient ffmpeg_;                  // dlopen handle to libffmpeg.so
    std::unique_ptr<ModelManager> models_;
    std::unique_ptr<TemporalSmoother> temporal_;
    
    // Frame buffers (owned by app module)
    std::vector<uint8_t> frame_buffer_;      // Decoded RGB frame
    std::vector<uint8_t> styled_buffer_;     // Styled RGB frame
    std::vector<float> mask_buffer_;          // Raw SINet mask
    std::vector<float> smooth_mask_buffer_;  // Temporally smoothed
    std::vector<float> feather_buffer_;       // Feather mask
    std::vector<uint8_t> output_buffer_;      // Final composited frame
};

// ──────────────────────────────────────────────────────────────────────

void PaletteEngine::exportVideo(
    const std::string& output_path,
    const std::string& input_path,
    const std::string& preset_json,
    std::function<void(int)> progress_callback) {
    
    // Load libffmpeg.so via dlopen
    ffmpeg_.load();
    
    // Parse preset
    Preset preset = Preset::fromJson(preset_json);
    
    // Open input via :ffmpeg C-API
    FfmpegDecoder* dec = ffmpeg_.ffmpeg_decoder_open(input_path.c_str());
    int width  = ffmpeg_.ffmpeg_decoder_width(dec);
    int height = ffmpeg_.ffmpeg_decoder_height(dec);
    int fps    = ffmpeg_.ffmpeg_decoder_fps(dec);
    int total  = ffmpeg_.ffmpeg_decoder_frame_count(dec);
    
    // Open output encoder via :ffmpeg C-API
    FfmpegEncoder* enc = ffmpeg_.ffmpeg_encoder_open(
        output_path.c_str(), width, height, fps, 8000000);
    
    // Create filter graph via :ffmpeg C-API
    FfmpegFilterGraph* fg = ffmpeg_.ffmpeg_filter_graph_create();
    
    // Allocate buffers
    frame_buffer_.resize(width * height * 3);
    styled_buffer_.resize(width * height * 3);
    mask_buffer_.resize(224 * 224);
    smooth_mask_buffer_.resize(width * height);
    feather_buffer_.resize(width * height);
    output_buffer_.resize(width * height * 3);
    
    // Process frames
    int frame_number = 0;
    FfmpegFrame frame;
    
    while (ffmpeg_.ffmpeg_decoder_decode_next(dec, &frame) == FFMPEG_OK) {
        // frame.rgb_data is now the decoded RGB24 frame
        std::copy(frame.rgb_data, frame.rgb_data + width * height * 3,
                  frame_buffer_.begin());
        
        // Run segmentation (every 15th frame = keyframe)
        if (frame_number % 15 == 0) {
            auto sinet_mask = models_->runSegmentation(
                ModelType::SINET, frame_buffer_.data(), width, height);
            temporal_->setKeyframe(sinet_mask);
        }
        
        // Propagate + smooth mask
        auto temp_mask = temporal_->smooth({}, frame_number);
        
        // Upscale mask from 224×224 to original resolution
        libswscale_upscale(temp_mask.data(), smooth_mask_buffer_.data(),
                           224, 224, width, height);
        
        // Generate feather mask
        feather_buffer_ = generateFeatherMask(
            smooth_mask_buffer_, width, height, {preset.feather_px});
        
        // Apply style via :ffmpeg filter graph (C-API)
        std::string filter_desc = buildStyleFilterGraph(preset, frame_number);
        ffmpeg_.ffmpeg_filter_graph_parse(fg, filter_desc.c_str(), width, height);
        
        FfmpegFrame styled_frame;
        ffmpeg_.ffmpeg_filter_graph_process(fg, &frame, &styled_frame);
        std::copy(styled_frame.rgb_data,
                  styled_frame.rgb_data + width * height * 3,
                  styled_buffer_.begin());
        ffmpeg_.ffmpeg_free_frame(&styled_frame);
        
        // Composite (app module, pure math, no FFmpeg)
        CompositorInput ci;
        ci.original_rgb = frame_buffer_;
        ci.feather_mask = feather_buffer_;
        ci.styled_bg_rgb = styled_buffer_;
        ci.width = width;
        ci.height = height;
        ci.style_person = preset.stylePerson();
        output_buffer_ = composite(ci);
        
        // Encode via :ffmpeg C-API
        FfmpegFrame out_frame;
        out_frame.rgb_data = output_buffer_.data();
        out_frame.width  = width;
        out_frame.height = height;
        out_frame.pts    = frame.pts;
        ffmpeg_.ffmpeg_encoder_encode_frame(enc, &out_frame);
        
        // Free decoded frame (C-API owned memory)
        ffmpeg_.ffmpeg_free_frame(&frame);
        
        // Progress
        int percent = (frame_number * 100) / total;
        progress_callback(percent);
        frame_number++;
    }
    
    ffmpeg_.ffmpeg_encoder_finalize(enc);
    ffmpeg_.ffmpeg_encoder_close(enc);
    ffmpeg_.ffmpeg_decoder_close(dec);
    ffmpeg_.ffmpeg_filter_graph_destroy(fg);
    ffmpeg_.unload();
}
```

## 5.5 Model Download & Management

```cpp
// model_manager.cpp

bool ModelManager::initialize(const std::string& model_dir) {
    model_dir_ = model_dir;
    
    // Load mandatory models (shipped with APK)
    ModelSpec sinet_spec;
    sinet_spec.path = model_dir_ + "/sinet_quant.tflite";
    sinet_spec.type = ModelType::SINET;
    sinet_spec.backend = ModelBackend::TFLITE;
    sinet_spec.quantized = true;
    sinet_spec.input_width = 224;
    sinet_spec.input_height = 224;
    sinet_spec.input_channels = 3;
    
    if (!loadModel(sinet_spec)) {
        LOGE("Failed to load SINet model");
        return false;
    }
    
    // Load MediaPipe Selfie as fallback
    ModelSpec mp_spec;
    mp_spec.path = model_dir_ + "/mediapipe_selfie.tflite";
    mp_spec.type = ModelType::MEDIAPIPE_SELFIE;
    mp_spec.backend = ModelBackend::TFLITE;
    mp_spec.quantized = true;
    mp_spec.input_width = 256;
    mp_spec.input_height = 256;
    mp_spec.input_channels = 3;
    
    if (!loadModel(mp_spec)) {
        LOGW("MediaPipe Selfie fallback not available");
    }
    
    // Optional: style base model (downloaded on demand)
    // This is a lightweight style transfer model
    // Used for neural style effects (not FFmpeg-based)
    // Check if it exists; if not, it's downloaded later
    ModelSpec style_spec;
    style_spec.path = model_dir_ + "/style_base.tflite";
    style_spec.type = ModelType::STYLE_BASE;
    style_spec.backend = ModelBackend::TFLITE;
    style_spec.quantized = true;
    style_spec.input_width = 256;
    style_spec.input_height = 256;
    style_spec.input_channels = 3;
    
    if (fileExists(style_spec.path)) {
        loadModel(style_spec);
    }
    
    return true;
}

// Called from Kotlin when user enables "Advanced Styles"
// Downloads the style transfer model via WorkManager
void ModelManager::downloadStyleModel(JNIEnv* env, jobject context) {
    // Kotlin handles the download via DownloadManager
    // Once complete, it calls nativeLoadStyleModel(path)
}
```

## 5.6 Preset Application Flow (Kotlin → Native)

```kotlin
// Usage in MainActivity.kt

class MainActivity : ComponentActivity() {
    private lateinit var engine: NativeEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize native engine
        val modelPath = "${filesDir}/models/"
        engine = NativeEngine()
        engine.create(modelPath)
    }
    
    // Called when user selects a preset from the picker
    fun onPresetSelected(presetJson: String) {
        // For real-time preview (camera feed or first frame)
        val inputBmp = loadCurrentFrameAsBitmap()
        val outputBmp = Bitmap.createBitmap(inputBmp.width, inputBmp.height, Bitmap.Config.ARGB_8888)
        
        engine.nativeRenderPreview(
            engine.nativeHandle,
            inputBmp, outputBmp, presetJson
        )
        
        previewView.setImageBitmap(outputBmp)
    }
    
    // Called when user taps export
    fun onExportClicked(presetJson: String) {
        val outputPath = "${cacheDir}/exports/output_${System.currentTimeMillis()}.mp4"
        
        engine.nativeExport(
            engine.nativeHandle,
            outputPath, presetJson,
            object : NativeEngine.NativeProgressCallback {
                override fun onProgress(percent: Int) {
                    runOnUiThread {
                        progressBar.progress = percent
                    }
                }
                
                override fun onComplete() {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Export complete!", Toast.LENGTH_LONG).show()
                        shareVideo(outputPath)
                    }
                }
                
                override fun onError(message: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error: $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    
    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }
}
```

## 5.7 Build System

### :app/build.gradle.kts

```kotlin
android {
    compileSdk = 35
    
    defaultConfig {
        minSdk = 26   // MediaCodec async API
        targetSdk = 35
        
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1+"
        }
    }
}

dependencies {
    implementation(project(":ffmpeg"))   // ← depends on ffmpeg module for dlopen

    // UI
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    
    // Background model download
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    
    // No ML dependencies on Kotlin side — all ML runs in C++
}
```

### :ffmpeg/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.palette.ffmpeg"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1+"
        }
    }
}
```

---

# A. Model Sourcing

| Model | HuggingFace | Size | Type | License |
|---|---|---|---|---|
| SINet (primary) | `qualcomm/SINet` | 241 KB (int8) | Portrait segmentation | MIT |
| MediaPipe Selfie (fallback) | `qualcomm/MediaPipe-Selfie-Segmentation` | ~1.5 MB (float16) | Portrait segmentation | Apache 2.0 |
| MobileSAM (future multi-class) | `qualcomm/MobileSam` | ~50 MB | Promptable segmentation | Apache 2.0 |

SINet shipped with APK. Style base model downloaded on-demand when user enables neural style effects (not all effects need it — most use FFmpeg-native filters).

---

# B. Key Design Decisions

| Decision | Rationale |
|---|---|---|
| **FFmpeg for style, not neural net** | 90% of artistic looks (Van Gogh, sketch, watercolor, glitch, pixel art) can be achieved with FFmpeg filter chains. Neural style model is optional for advanced effects. |
| **SINet over SAM** | 241KB vs 50MB+ for the same task (person segmentation). SAM's flexibility (segment anything) is overkill when you only need "person vs background." |
| **Keyframe mask propagation** | Running SINet every 15 frames + FFmpeg motion estimation saves 93% of inference cost while maintaining temporal stability. |
| **MediaCodec hwaccel** | Zero-copy decode/encode via Android hardware codec. Avoids expensive CPU-GPU transfers. |
| **FFmpeg in separate module with C-API** | All FFmpeg internals sealed in `:ffmpeg` module. App module never includes FFmpeg headers. Communication via `dlopen`/`dlsym` at runtime — no compile-time coupling across Gradle modules. |
| **RGB24 as interchange format** | Both modules agree on RGB24. The `:ffmpeg` module handles NV12↔RGB24 conversion internally. The app module only works with raw RGB24 pixel buffers — no AVFrame or FFmpeg types leak. |
| **One native process** | FFmpeg + TFLite + math all in C++ avoids JNI thrash. Only Kotlin↔C++ calls are: load video, trigger export, progress callback. All pixel processing stays native. |
| **Presets as JSON recipes** | New presets can be shipped server-side or created by users without app updates. The JSON defines mask target, style parameters, and animation behavior. |

---

# C. Potential Extensions

1. **Multi-person segmentation**: Replace SINet with MediaPipe Selfie (2-person support) or MobileSAM (promptable)
2. **Depth-aware effects**: Add on-device depth estimation model → background parallax, fog, bokeh
3. **Audio-reactive styles**: Synchronize animation parameters with audio amplitude/beat (FFmpeg `volume` detection)
4. **User fine-tuning**: Collect user corrections → fine-tune SINet adapter locally (LoRA on-device)
5. **Hybrid cloud fallback**: If device is too slow, send low-res frames to cloud for mask generation, keep pixel processing local
6. **Segmentation classes**: Upgrade to multi-class model (skin, hair, clothes, sky, ground) for per-region styles

---

*Generated: 2026-06-16*
*Project: PALETTE — Android AI Video Editor*
