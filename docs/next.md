# ReelCraft — Architecture & Build Plan

Current branch: `feat/ffmpeg`
Spec reference: `palette-video-editor.md`

---

## 1. Module Architecture

### Three Modules, Clear Boundaries

```
┌──────────────────────────────────────────────────────────────────┐
│  :app  (Android Application)                                     │
│  Package: id.my.daniza.reelcraft                                  │
│                                                                  │
│  Kotlin/Compose UI, ViewModels, Navigation                       │
│  Pipeline orchestration (calls :ffmpeg + :segment JNI APIs)      │
│  Preset JSON parser, project state, file I/O, export flow        │
│  JNI bridge to C++ components in both modules                    │
│                                                                  │
│  No FFmpeg code.  No ML/TFLite code.                             │
│  Pure higher-level orchestration in Kotlin.                      │
└──────────────────────────┬───────────────────────────────────────┘
                           │ depends on
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
┌─────────────────────────┐  ┌─────────────────────────┐
│  :ffmpeg (Android Lib)   │  │  :segment (Android Lib) │
│  Package: id.my.daniza   │  │  Package: id.my.daniza  │
│            .ffmpeg       │  │            .segment     │
│                          │  │                          │
│  ALL FFmpeg operations   │  │  ALL ML operations       │
│  in C++ + own JNI:       │  │  in C++ + own JNI:       │
│                          │  │                          │
│  • Decoder (hwaccel)     │  │  • TFLiteBackend         │
│  • Encoder               │  │  • SINet segmentation    │
│  • FilterGraph           │  │  • ModelManager          │
│  • Compositor (pixel)    │  │  • StyleTransfer (opt.)  │
│  • TemporalSmoother      │  │  • MediaPipe selfie      │
│  • Feather (blur-based)  │  │                          │
│                          │  │  Assets: .tflite models  │
│  Prebuilt: FFmpeg .so    │  │  Depends: TFLite runtime │
│  No ML deps              │  │  No FFmpeg deps           │
└─────────────────────────┘  └─────────────────────────┘
```

### Why This Separation

| Concern | Owner | Why |
|---------|-------|-----|
| Video decode/encode | `:ffmpeg` | FFmpeg-native, no ML needed |
| Filter chains (edgedetect, curves, palettegen, etc.) | `:ffmpeg` | Pure FFmpeg pixel math |
| Mask-based compositing | `:ffmpeg` | Pixel blending, no ML |
| Temporal mask smoothing | `:ffmpeg` | Uses FFmpeg `mestimate` for motion vectors |
| Feather/blur mask edges | `:ffmpeg` | Pixel manipulation, blur kernels |
| TFLite model loading/inference | `:segment` | Direct TFLite runtime dependency |
| Person/background segmentation | `:segment` | Runs SINet via TFLite |
| Neural style transfer | `:segment` | Runs style model via TFLite |
| Model lifecycle (load/unload/download) | `:segment` | Manages .tflite files |
| UI screens | `:app` | Jetpack Compose |
| Pipeline orchestration | `:app` | Kotlin calls ffmpeg + segment JNI |
| Preset JSON parsing | `:app` | Pure data, no native needed |
| Project save/load | `:app` | File I/O in Kotlin |
| Export flow | `:app` | Progress, sharing, WorkManager |

### Kotlin-Side JNI Surfaces

Each module exposes its own Kotlin class with `external` native methods. The app never touches JNI/C++ directly — it calls these Kotlin facades.

#### `:ffmpeg` — `NativeFFmpeg.kt`

```kotlin
package id.my.daniza.ffmpeg

class NativeFFmpeg {
    companion object { init { System.loadLibrary("ffmpeg") } }

    // Lifecycle
    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)

    // Video I/O
    external fun nativeOpenVideo(handle: Long, videoPath: String): Boolean
    external fun nativeCloseVideo(handle: Long)
    external fun nativeGetVideoInfo(handle: Long): VideoInfo

    // Decode
    external fun nativeDecodeFrame(handle: Long, frameNumber: Int, outputBitmap: Bitmap): Boolean
    external fun nativeDecodeNextFrame(handle: Long, outputBitmap: Bitmap): Boolean

    // Encode
    external fun nativeOpenEncoder(handle: Long, outputPath: String, width: Int, height: Int, fps: Float, bitrate: Int): Boolean
    external fun nativeEncodeFrame(handle: Long, inputBitmap: Bitmap): Boolean
    external fun nativeCloseEncoder(handle: Long)

    // Filter
    external fun nativeApplyFilter(handle: Long, inputBitmap: Bitmap, outputBitmap: Bitmap, filterDesc: String): Boolean

    // Composition (mask × frame1 + (1-mask) × frame2)
    external fun nativeComposite(handle: Long, frameBitmap: Bitmap, styledBitmap: Bitmap, maskBuffer: FloatArray, featherRadius: Int, outputBitmap: Bitmap): Boolean

    // Temporal mask smoothing
    external fun nativeTemporalSmooth(handle: Long, rawMask: FloatArray, maskWidth: Int, maskHeight: Int, frameNumber: Int): FloatArray
    external fun nativeResetTemporal(handle: Long)

    // Feather
    external fun nativeFeather(handle: Long, binaryMask: FloatArray, width: Int, height: Int, radius: Int): FloatArray

    // Preset (filter graph builder — builds FFmpeg filter string from preset JSON)
    external fun nativeBuildPresetFilter(handle: Long, presetJson: String, frameNumber: Int): String
}
```

#### `:segment` — `NativeSegment.kt`

```kotlin
package id.my.daniza.segment

class NativeSegment {
    companion object { init { System.loadLibrary("segment") } }

    // Lifecycle
    external fun nativeCreate(modelDir: String): Long
    external fun nativeDestroy(handle: Long)

    // Model management
    external fun nativeLoadModel(handle: Long, type: Int, path: String): Boolean
    external fun nativeUnloadModel(handle: Long, type: Int): Boolean
    external fun nativeIsModelLoaded(handle: Long, type: Int): Boolean

    // Segmentation (SINet)
    external fun nativeRunSegmentation(handle: Long, rgbBuffer: ByteArray, width: Int, height: Int): FloatArray
    // Returns float array of size width × height, values 0.0 (bg) to 1.0 (person)

    // Style transfer (neural model, optional)
    external fun nativeRunStyleTransfer(handle: Long, rgbBuffer: ByteArray, width: Int, height: Int, params: StyleParams): ByteArray

    data class StyleParams(
        val styleName: String,
        val intensity: Float,
        val animationSpeed: Float,
        val seed: Int
    )
}
```

### Pipeline Orchestration (in `:app` Kotlin)

The app orchestrates frame processing by calling both modules:

```kotlin
// In a ViewModel or use case
fun processFrame(videoPath: String, frameNumber: Int, preset: Preset, outputBitmap: Bitmap) {
    // 1. Decode frame via FFmpeg
    ffmpeg.nativeDecodeFrame(ffmpegHandle, frameNumber, frameBitmap)

    // 2. Segment person via SINet
    val rgbBytes = frameBitmap.toByteArray()
    val rawMask = segment.nativeRunSegmentation(segmentHandle, rgbBytes, width, height)

    // 3. Temporal smooth mask via FFmpeg (motion propagation)
    val smoothMask = ffmpeg.nativeTemporalSmooth(ffmpegHandle, rawMask, width, height, frameNumber)

    // 4. Feather mask edges via FFmpeg
    val featherMask = ffmpeg.nativeFeather(ffmpegHandle, smoothMask, width, height, preset.featherRadius)

    // 5. Build filter graph string from preset
    val filterDesc = ffmpeg.nativeBuildPresetFilter(ffmpegHandle, preset.toJson(), frameNumber)

    // 6. Apply style filter via FFmpeg
    ffmpeg.nativeApplyFilter(ffmpegHandle, frameBitmap, styledBitmap, filterDesc)

    // 7. Composite mask × styled + (1-mask) × original via FFmpeg
    ffmpeg.nativeComposite(ffmpegHandle, frameBitmap, styledBitmap, featherMask, preset.featherRadius, outputBitmap)
}
```

---

## 2. AI Dependency Map

### TIER 0: FFmpeg-Only (zero AI needed)

Work on the **full frame** — no person/background segmentation. Buildable immediately.

| Feature | FFmpeg Filter | Details |
|---------|--------------|---------|
| **Trim / Split** | `trim` + `concat` | Cut clips at timestamps. `trim=start=10:duration=5`. No AI. |
| **Multi-track video** | `overlay` | `overlay=x=0:y=0` layers video. Position, scale, opacity. No AI. |
| **Speed ramp** | `setpts` | `setpts=0.5*PTS` (2x), `setpts=2.0*PTS` (0.5x). No AI. |
| **Reverse clip** | `reverse` | Reverses video. `areverse` for audio. No AI. |
| **Transitions** | `xfade` | `xfade=transition=fade:duration=1:offset=10`. Also `wipeleft`, `fade`, etc. No AI. |
| **Text overlay** | `drawtext` | `drawtext=text='Hello':fontsize=48:x=100:y=100`. Outline, shadow, alpha. No AI. |
| **Color grading** | `curves`, `colorbalance`, `eq` | `curves=vintage` or `colorbalance=rs=.1:gs=-.05:bs=-.1`. No AI. |
| **Pixel Art** | `palettegen` + `paletteuse` | `palettegen=max_colors=16`. `paletteuse=dither=bayer`. Full-frame quantize. No AI. |
| **Pencil Sketch (full)** | `edgedetect` + `blend` | `edgedetect=low=0.1:high=0.3:mode=colormix`. Blend as multiply. No AI. No mask. |
| **Glitch (full frame)** | `chromashift` + `noise` | Channel offset: `chromashift=crh=10:cbv=5`. Add noise for corruption. No AI. |
| **Audio mixing** | `amix` | `amix=inputs=2:duration=first`. Volume normalization. No AI. |
| **Volume envelopes** | `volume` with expressions | `volume='if(gte(t,5),1,0)'`. Expression-driven keyframes. No AI. |
| **Fade in/out** | `fade`, `afade` | `fade=in:st=0:d=1` video. `afade` audio. No AI. |
| **Rotate / Crop** | `rotate`, `crop` | `rotate=45*PI/180`. `crop=iw/2:ih/2`. No AI. |
| **Export** | MediaCodec h264/h265 | Resolution, bitrate, codec. No AI. |

**Tier 0 total: ~70% of a functional video editor. Zero AI required.**

### TIER 1: Segmentation Only (needs SINet, 241KB)

The **mask** from SINet controls WHERE the effect applies. The look is still FFmpeg.

| Feature | Mask Target | Effect |
|---------|-------------|--------|
| **Van Gogh Portrait** | `:segment` SINet → `:ffmpeg` composite | BG gets impasto + warm curves. Person untouched. |
| **Watercolor** | `:segment` SINet → `:ffmpeg` composite | BG gets color bleed + blur. Person crisp. |
| **Random Line Art** | `:segment` SINet → `:ffmpeg` composite | BG gets regenerating edge lines. Person clean. |
| **Cyberpunk** | `:segment` SINet → `:ffmpeg` composite | Person gets neon + contrast. BG dark + grid. |
| **Ink Splash** | `:segment` SINet → `:ffmpeg` composite | BG gets expanding ink drops. Person clean. |
| **Silhouette Glow** | `:segment` SINet → `:ffmpeg` composite | Person edge → neon glow. Interior dark. |
| **Ghost Trail** | `:segment` SINet → `:ffmpeg` composite | Person layer with decaying opacity trail. |
| **Differential Sketch** | `:segment` SINet → `:ffmpeg` composite | Person: light sketch. BG: heavy sketch. |
| **Selective Glitch** | `:segment` SINet → `:ffmpeg` composite | Glitch clipped to person mask. |

**Key: The data flow is `:segment` produces the mask → `:app` sends mask + frame to `:ffmpeg` for composite.**

### TIER 2: Neural Style (needs style_base.tflite, ~300MB, on-demand)

| Feature | What neural style enables |
|---------|--------------------------|
| Neural style transfer (e.g., Monet painting effect) | Learned filter instead of FFmpeg approximation |
| Custom style from reference image | User image → style model adapts |

**Tier 2: entirely optional. Not needed for v1. Download on demand.**

---

## 3. Phased Build Plan

### Phase 1: `:ffmpeg` C++ Engine

Build all FFmpeg-native C++ code in the `:ffmpeg` module.

| # | Component | File | What It Does |
|---|-----------|------|-------------|
| 1.1 | **Decoder** | `ffmpeg/src/main/cpp/decoder.h/.cpp` | Opens video, decodes frames via MediaCodec hwaccel. `nextFrame()`, `width()`, `height()`, `fps()`, `frameCount()`. Outputs RGB. |
| 1.2 | **Encoder** | `ffmpeg/src/main/cpp/encoder.h/.cpp` | Encodes frames to file via MediaCodec. `open(path, w, h, pix_fmt)`, `encodeFrame(rgb_data)`, `finalize()`. |
| 1.3 | **FilterGraph** | `ffmpeg/src/main/cpp/filter_graph.h/.cpp` | Builds AVFilterGraph from string. `build(desc)`, `process(AVFrame*)`. Also `buildPresetPipeline(w, h, preset, frame)` for preset filter chains. |
| 1.4 | **Compositor** | `ffmpeg/src/main/cpp/compositor.h/.cpp` | `composite(frame, styled, mask, w, h, style_person)` → blended output. Per-pixel: `mask × person + (1-mask) × bg`. No AI. |
| 1.5 | **TemporalSmoother** | `ffmpeg/src/main/cpp/temporal.h/.cpp` | Keyframe mask propagation via FFmpeg `mestimate` + `mcompensate`. 5-frame ring buffer median filter. |
| 1.6 | **Feather** | `ffmpeg/src/main/cpp/feather.h/.cpp` | Box blur + sigmoid on mask edge. Replaces OpenCV distanceTransform. |
| 1.7 | **JNI Bridge** | `ffmpeg/src/main/cpp/jni_bridge.cpp` | All JNI methods for NativeFFmpeg.kt. |
| 1.8 | **NativeFFmpeg.kt** | `ffmpeg/src/main/java/.../NativeFFmpeg.kt` | Kotlin JNI facade. |
| 1.9 | **CMakeLists.txt** | `ffmpeg/src/main/cpp/CMakeLists.txt` | Add decoder.cpp, encoder.cpp, filter_graph.cpp, compositor.cpp, temporal.cpp, feather.cpp, jni_bridge.cpp. Link FFmpeg prebuilts. |

### Phase 2: `:segment` C++ Engine

Build all ML/TFLite-dependent C++ code in the `:segment` module.

| # | Component | File | What It Does |
|---|-----------|------|-------------|
| 2.1 | **TFLiteBackend** | `segment/src/main/cpp/tflite_backend.h/.cpp` | Wraps TFLite C API: `load(path)`, `run(input, size)`, `unload()`. GPU delegate. Input normalization. |
| 2.2 | **SINet** | `segment/src/main/cpp/sinet.h/.cpp` | Segmentation: downscale to 256×256 → TFLite inference → sigmoid → float mask. `run(rgb_data, w, h)` → mask. Links `:ffmpeg` for libswscale downscale. |
| 2.3 | **ModelManager** | `segment/src/main/cpp/model_manager.h/.cpp` | Load SINet + MediaPipe at startup. Fallback logic. Style model download trigger. |
| 2.4 | **StyleTransfer** | `segment/src/main/cpp/style_transfer.h/.cpp` | Neural style inference (optional tier). Animation state per frame. |
| 2.5 | **JNI Bridge** | `segment/src/main/cpp/jni_bridge.cpp` | All JNI methods for NativeSegment.kt. |
| 2.6 | **NativeSegment.kt** | `segment/src/main/java/.../NativeSegment.kt` | Kotlin JNI facade. |
| 2.7 | **CMakeLists.txt** | `segment/src/main/cpp/CMakeLists.txt` | Add tflite_backend, sinet, model_manager, style_transfer, jni_bridge. Link TFLite runtime. |

### Phase 3: Full-Frame Presets (`:ffmpeg` only, test on `:app`)

Build and test FFmpeg-only presets. These test FilterGraph without needing the mask pipeline.

| # | Preset | Filter Graph Core |
|---|--------|-------------------|
| 3.1 | **Pixel Art** | `palettegen=max_colors=16:stats_mode=diff, paletteuse=dither=bayer:bayer_scale=5` |
| 3.2 | **Pencil Sketch** | `edgedetect=low=0.1:high=0.3:mode=colormix, blend=all_mode=multiply` |
| 3.3 | **Full Glitch** | `chromashift=crh=10:cbv=5, noise=alls=15:allf=t+u` |
| 3.4 | **Color Grade** | `curves=vintage` |
| 3.5 | **Style Animation** | Add noise seed per frame for rotating/animated params |

Each preset: implement as filter graph string in `NativeFFmpeg.nativeBuildPresetFilter()`, preview via `nativeApplyFilter()`, export via `nativeDecodeFrame()` → `nativeApplyFilter()` → `nativeEncodeFrame()` loop.

### Phase 4: Selective Presets (`:segment` + `:ffmpeg`)

Now the mask comes in. `:app` orchestrates: decode → segment → smooth → feather → style → composite.

| # | Preset | Mask Path |
|---|--------|-----------|
| 4.1 | **Van Gogh Portrait** | SINet → BG styled, person untouched |
| 4.2 | **Cyberpunk** | SINet → Person styled, BG dark |
| 4.3 | **Watercolor** | SINet → BG styled, person untouched |
| 4.4 | **Random Line Art** | SINet → BG styled, person clean |
| 4.5 | **Ink Splash** | SINet → BG styled, expanding drops |
| 4.6 | **Silhouette Glow** | SINet → edge detect on mask → glow |
| 4.7 | **Ghost Trail** | SINet → person overlay, decaying ring buffer |
| 4.8 | **Differential Sketch** | SINet → person light, BG heavy |
| 4.9 | **Selective Glitch** | SINet → glitch clipped to person |

### Phase 5: Core Editing Features (in `:app` Kotlin)

| # | Feature | Implementation |
|---|---------|---------------|
| 5.1 | Project model + persistence | JSON schema — clips, tracks, timeline, presets, export settings |
| 5.2 | Trim/split | UI: trim handles on timeline. Calls `:ffmpeg` `trim` filter on export. |
| 5.3 | Speed ramp | UI: speed slider (0.25x–4x). Calls `:ffmpeg` `setpts` on export. |
| 5.4 | Reverse | Calls `:ffmpeg` `reverse` on export. |
| 5.5 | Transitions | UI: transition picker + duration. Calls `:ffmpeg` `xfade` on export. |
| 5.6 | Text overlay | UI: text input, font, position drag, keyframes. Calls `:ffmpeg` `drawtext` on export. |
| 5.7 | Undo/redo | Command pattern: every edit pushes an inverse action. Pure Kotlin. |

### Phase 6: Jetpack Compose UI (in `:app`)

| # | Screen | Purpose |
|---|--------|---------|
| 6.1 | **MainActivity.kt** | Compose entry, NavHost, theme |
| 6.2 | **TimelineScreen.kt** | Clip tracks, scrubber, playhead, zoom, trim handles |
| 6.3 | **PresetPicker.kt** | Grid of thumbnail presets, category filter, intensity/feather/animation sliders |
| 6.4 | **PreviewView.kt** | Real-time preset preview. Calls `:ffmpeg` decode + `:segment` segment + `:ffmpeg` composite via app orchestration. |
| 6.5 | **ExportDialog.kt** | Resolution, codec, bitrate selection. Progress bar. Share button. |
| 6.6 | **MediaPicker.kt** | Gallery import, camera capture. ActivityResultContract. |

### Phase 7: Audio (in `:ffmpeg` + `:app`)

| # | Feature | Where | Implementation |
|---|---------|-------|---------------|
| 7.1 | Audio stream extraction | `:ffmpeg` | Extract audio stream as separate track |
| 7.2 | Waveform rendering | `:app` | Analyze PCM samples → Canvas waveform |
| 7.3 | Volume envelopes | `:ffmpeg` | `volume` with expression keyframes |
| 7.4 | Audio ducking | `:ffmpeg` | Speech amplitude detection → duck music |
| 7.5 | Multi-track mixing | `:ffmpeg` | `amix` for N audio tracks |

### Phase 8: Export Flow (in `:app`)

| # | Component | Details |
|---|-----------|---------|
| 8.1 | Foreground service | Keeps export alive when app is backgrounded |
| 8.2 | WorkManager | Survives process death, persists progress |
| 8.3 | Progress callbacks | C++ → JNI → Coroutine → Notification |
| 8.4 | Share sheet | Export complete → share to Instagram, TikTok, Gallery |

### Phase 9: Polish & Testing

| # | Task | Scope |
|---|------|-------|
| 9.1 | C++ unit tests | Google Test for compositor, temporal, feather, tflite_backend |
| 9.2 | JNI instrumented tests | NativeFFmpeg.kt ↔ jni_bridge.cpp round-trip |
| 9.3 | Compose UI tests | Screenshot + interaction tests |
| 9.4 | Error handling | OOM, corrupt video, model load failure, disk full |
| 9.5 | Performance profiling | Verify 25ms/frame budget |
| 9.6 | CI pipeline | GitHub Actions: build + test |

---

## 4. Data Flow (Per Frame, Export)

```
┌─────────────────────────────────────────────────────────────────────┐
│  :app (Kotlin)                                        │
│                                                                    │
│  for frame in 0..total_frames:                                     │
│    │                                                               │
│    ├── 1. ffmpeg.nativeDecodeFrame(handle, frame, frameBitmap)     │
│    │                                                               │
│    ├── 2. segment.nativeRunSegmentation(                           │
│    │        handle, frameBitmap.bytes, w, h) → rawMask              │
│    │                                                               │
│    ├── 3. ffmpeg.nativeTemporalSmooth(                             │
│    │        handle, rawMask, w, h, frame) → smoothMask              │
│    │                                                               │
│    ├── 4. ffmpeg.nativeFeather(                                    │
│    │        handle, smoothMask, w, h, radius) → featherMask         │
│    │                                                               │
│    ├── 5. ffmpeg.nativeBuildPresetFilter(                          │
│    │        handle, presetJson, frame) → filterDesc                 │
│    │                                                               │
│    ├── 6. ffmpeg.nativeApplyFilter(                                │
│    │        handle, frameBitmap, styledBitmap, filterDesc)          │
│    │                                                               │
│    ├── 7. ffmpeg.nativeComposite(                                  │
│    │        handle, frameBitmap, styledBitmap,                      │
│    │        featherMask, radius, outputBitmap)                      │
│    │                                                               │
│    └── 8. ffmpeg.nativeEncodeFrame(handle, outputBitmap)           │
│                                                                    │
│  ffmpeg.nativeCloseEncoder(handle)                                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 5. Module File Layout

```
reelcraft/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/id/my/daniza/reelcraft/
│       │   ├── MainActivity.kt
│       │   ├── ui/
│       │   │   ├── TimelineScreen.kt
│       │   │   ├── PresetPicker.kt
│       │   │   ├── PreviewView.kt
│       │   │   ├── ExportDialog.kt
│       │   │   └── MediaPicker.kt
│       │   ├── pipeline/
│       │   │   ├── FrameProcessor.kt          # Orchestrates ffmpeg + segment calls
│       │   │   └── ExportPipeline.kt          # Frame loop for video export
│       │   ├── model/
│       │   │   ├── Project.kt                 # Project data classes
│       │   │   ├── Preset.kt                  # JSON recipe parser
│       │   │   └── Clip.kt                    # Clip/track model
│       │   └── viewmodel/
│       │       ├── TimelineViewModel.kt
│       │       ├── PresetViewModel.kt
│       │       └── ExportViewModel.kt
│       └── res/                               # Resources
│
├── ffmpeg/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── decoder.h / .cpp
│       │   ├── encoder.h / .cpp
│       │   ├── filter_graph.h / .cpp
│       │   ├── compositor.h / .cpp
│       │   ├── temporal.h / .cpp
│       │   ├── feather.h / .cpp
│       │   ├── jni_bridge.cpp
│       │   └── ffmpeg/include/               # FFmpeg C headers
│       ├── java/id/my/daniza/ffmpeg/
│       │   └── NativeFFmpeg.kt
│       └── jniLibs/arm64-v8a/                # Prebuilt FFmpeg .so files
│
├── segment/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/models/
│       │   ├── sinet.tflite
│       │   ├── sinet_metadata.json
│       │   ├── mediapipe_selfie.tflite
│       │   └── mediapipe_selfie_metadata.json
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── tflite_backend.h / .cpp
│       │   ├── sinet.h / .cpp
│       │   ├── model_manager.h / .cpp
│       │   ├── style_transfer.h / .cpp
│       │   └── jni_bridge.cpp
│       └── java/id/my/daniza/segment/
│           └── NativeSegment.kt
│
├── settings.gradle.kts                       # include(":app", ":ffmpeg", ":segment")
├── build.gradle.kts                          # Root
└── gradle/libs.versions.toml                 # Version catalog
```

---

## 6. Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| OpenCV | Not used | Feather via box blur + sigmoid. ~50 lines header-only. Saves ~50MB APK. |
| TFLite runtime | Maven AAR (`org.tensorflow:tensorflow-lite`) | Official distribution. Adds `libtensorflowlite_jni.so`. C API headers extractable. |
| Segmentation model | SINet (241KB, float32, 256×256) | Already downloaded. Ships with APK. MediaPipe as fallback. |
| Mask propagation | FFmpeg `mestimate` + `mcompensate` every 15 frames | Saves 93% inference cost. Temporal median filter prevents flicker. |
| MediaCodec hwaccel | FFmpeg `mediacodec` | Zero-copy GPU decode/encode. Requires minSdk 26 (done). |
| Project format | Flat JSON v1 | Debuggable, diff-friendly, easy to share. Room DB if performance demands it later. |
| Export flow | Foreground service + WorkManager | Survives process death + backgrounding. Progress via notification. |
| Compose version | BOM 2025.06.00, Kotlin compiler ext 2.1.0 | Latest compatible with AGP 9.2.1. |

---

## 7. Current Status

### Phase 0 — Done

- [x] Raised `:app` minSdk: 24 → 26
- [x] Compose deps in version catalog + `app/build.gradle.kts`
- [x] Removed ffmpeg test stubs
- [x] Created C++ directory structure in `:app` (to be migrated to `:ffmpeg` and `:segment`)
- [x] Wrote `feather.h` (to move to `:ffmpeg` module)
- [x] `next.md` written with full architecture

### Next

→ Phase 1: Build `:ffmpeg` C++ engine (decoder, encoder, filter_graph, compositor, temporal, feather, JNI bridge)
