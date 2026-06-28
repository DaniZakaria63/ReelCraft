# FFmpeg C++ Engine — Design Decisions

## Architecture: Pure C API, JNI Bridge Separator

Each component is split into:
- **`*.h`** — function declarations using only raw C types (`const char*`, `uint8_t*`, `int`). Zero JNI dependency.
- **`*.cpp`** — implementation using FFmpeg C API, pure pixel math, no JNI types.
- **`jni_bridge.cpp`** — the ONLY file with `extern "C" JNIEXPORT` functions. Converts JNI types (`jstring`→`const char*`, `jobject` ByteBuffer→`uint8_t*`, `jlong`→C++ pointer) and calls the C API.

**Why:** JNIEnv pointers are thread-local and only valid within a JNI call. Mixing JNI types into the engine would leak platform-specific coupling into every function. The bridge pattern means the engine can be unit-tested without Android, and the JNI surface is one file to audit for leaks.

**Alternative rejected:** Passing `JNIEnv*` to every engine function — would make testing impossible outside Android and clutter every signature.

## Opaque Handles (`jlong`) Instead of Direct Objects

Every stateful component returns an opaque handle (`FFDecoder*` cast to `jlong`). Kotlin stores this as a `Long` and passes it back to every subsequent call.

**Why:** JNI provides no safe way to hold C++ objects in Java/Kotlin object fields. A `long` is atomic, won't leak if garbage-collected (it just becomes invalid), and costs one register transfer per call.

**Alternative rejected:** Using `jobject` global references — requires manual `NewGlobalRef`/`DeleteGlobalRef` management, prone to leaks, and GC pauses can free them at bad times.

## Direct ByteBuffers for Frame Data

All frame pixel data passes through `java.nio.ByteBuffer` allocated with `allocateDirect()` in Kotlin. The JNI bridge calls `GetDirectBufferAddress()` to get a raw `uint8_t*` pointer.

**Why:** Zero-copy on most Android devices. Direct buffers are allocated in native memory, so `memcpy` from the FFmpeg AVFrame buffer to the direct buffer is the only copy. There's no JNI marshaling of individual pixel arrays.

**Alternative rejected:** Returning `jbyteArray` — JNI would memcpy twice (once into the jbyteArray, once when Kotlin reads it). Passing Bitmaps via `NewGlobalRef` — couples the engine to Android graphics, prevents headless processing (export).

## Each Component Has One Responsibility

| Component | Owns | Single Job |
|---|---|---|
| `decoder` | `AVFormatContext`, `AVCodecContext`, `SwsContext` | Read file → produce RGBA bytes |
| `encoder` | `AVFormatContext`, `AVCodecContext`, `SwsContext` | Accept RGBA/NV12 bytes → write file |
| `filter_graph` | `AVFilterGraph`, `buffersrc`/`buffersink` | Apply FFmpeg filter string to RGBA bytes |
| `compositor` | Nothing (stateless functions) | Blend pixel arrays |
| `temporal` | Nothing (stateless functions) | Reorder/blend frame arrays |

**Why:** Each component manages distinct FFmpeg resource lifetimes. AVFormatContext needs `avformat_close_input`, AVCodecContext needs `avcodec_free_context`, AVFilterGraph needs `avfilter_graph_free`. Mixing them would create unclear ownership and leak paths.

**Alternative rejected:** A monolithic `VideoPipeline` class — would require conditional cleanup for every combination of open/closed sub-systems, making error handling fragile.

---

## Decoder Design

### API Surface
```c
FFDecoder* decoder_open(const char* path);
void decoder_close(FFDecoder* d);
int decoder_width(FFDecoder* d);
int decoder_height(FFDecoder* d);
int64_t decoder_duration_us(FFDecoder* d);
int64_t decoder_frame_rate_millifps(FFDecoder* d);
int decoder_rotation(FFDecoder* d);
bool decoder_seek(FFDecoder* d, int64_t timestamp_us);
bool decoder_read_frame(FFDecoder* d, uint8_t* out_rgba, int buffer_size);
int decoder_frame_size(FFDecoder* d);
```

### Key Decisions

**RGBA output always.** The decoder converts every frame to `AV_PIX_FMT_RGBA` (8-bit, 4 bytes/pixel) via `sws_scale`. Why not NV12 (smaller) or keep the source format? RGBA is the lowest-common-denominator format the Kotlin layer can render via `android.graphics.Bitmap` or a Compose `Canvas`. NV12 would require a shader to display. RGBA is also the input format for compositor and filter_graph — no conversion step between decode and edit.

**`av_seek_frame` with `AVSEEK_FLAG_BACKWARD`.** Seeking to a non-keyframe would produce garbage. `BACKWARD` finds the nearest keyframe at or before the target, which is safe. After seek, `avcodec_flush_buffers` drains stale frames from the internal codec buffer.

**Frame rate as `millifps` (long).** Returns `frame_rate.num * 1000 / frame_rate.den`. Example: 29.97 fps → 29970. The Kotlin wrapper converts to `Double`. Why not return a `double`? JNI has no `jdouble` precision concern, but FFmpeg represents frame rate as a rational (`AVRational`). Keeping the rational multiplication in C avoids floating-point drift from `30 * 1000 / 1001` in Kotlin.

**Runtime rotation detection.** Reads both the `rotate` metadata tag and `AV_PKT_DATA_DISPLAYMATRIX` side data. Some files have one, some have the other, some have both with potential conflicts. The function returns degrees (0/90/180/270) for the Kotlin layer to apply as a Compose `Modifier.rotate()` instead of swscale rotation (which re-encodes).

**Decoder reuses `SwsContext` via `sws_getCachedContext`.** A fresh `sws_allocContext`/`sws_initContext` every frame would be wasteful. `sws_getCachedContext` compares the source/destination parameters and only recreates the context if something changed (e.g., source format switches between frames, which can happen in some video files).

---

## Encoder Design

### API Surface
```c
FFEncoder* encoder_open(const char* path, int width, int height,
                         int frame_rate_num, int frame_rate_den,
                         int bit_rate);
void encoder_close(FFEncoder* e);
bool encoder_encode_frame_rgba(FFEncoder* e, const uint8_t* rgba, int buffer_size);
bool encoder_encode_frame_nv12(FFEncoder* e, const uint8_t* nv12, int buffer_size);
bool encoder_finalize(FFEncoder* e);
```

### Key Decisions

**Internal format is `AV_PIX_FMT_YUV420P`.** H.264 requires planar YUV input. The encoder accepts either RGBA or NV12 and uses `sws_scale` to convert to YUV420P internally. Two entry points (`_rgba` / `_nv12`) let the caller skip the conversion if they already have NV12 (common from camera sources or MediaCodec decoders).

**Encoder auto-select: MediaCodec → libx264 → software.** `find_h264_encoder()` tries `h264_mediacodec` first (hardware encoder, faster, less battery), falls back to `libx264` (software, widely compatible), and finally `avcodec_find_encoder(AV_CODEC_ID_H264)` (generic, may be MPEG-4 if H.264 unavailable).

**Why not always MediaCodec?** The prebuilt FFmpeg includes `mediacodec.h/jni.h`, so `h264_mediacodec` should work. But fallbacks exist for devices where the hardware encoder doesn't support the resolution, or for emulators where MediaCodec isn't available.

**`preset=fast`, `tune=zerolatency`.** Fast preset balances encoding speed vs file size for mobile. Zerolatency tune disables frame lookahead, essential for real-time encoding (without it, the encoder buffers several frames before emitting the first packet, causing preview lag).

**`bit_rate` default 8 Mbps.** Conservative for 1080p30. Users will configure this per project later; 8 Mbps is the default in the Kotlin wrapper.

**`gop_size=12`, `max_b_frames=1`.** Keyframe every 12 frames (~0.4s at 30fps) for reasonable seeking granularity. 1 B-frame gives compression efficiency without the decoding complexity of multiple B-frames on mobile.

**`encoder_finalize` flushes delayed packets.** `avcodec_send_frame(nullptr)` drains the encoder's internal frame buffer. Without this, the last few frames would be lost.

---

## Filter Graph Design

### API Surface
```c
FFFilterGraph* filter_graph_create(int width, int height, const char* filter_desc);
void filter_graph_close(FFFilterGraph* fg);
bool filter_graph_process(FFFilterGraph* fg,
                           const uint8_t* in_rgba, int in_width, int in_height,
                           uint8_t* out_rgba, int out_width, int out_height);
```

### Key Decisions

**FFmpeg filter strings.** The hardest part of FFmpeg filters is building the graph. Rather than wrapping every filter in a C++ class (which would be hundreds of classes), the API accepts an arbitrary filter graph description string and runs it. Example filter strings Kotlin will pass:

| Effect | Filter String |
|---|---|
| Black & White | `hue=s=0,colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3` |
| Pixelate | `pixelize=w=16:h=16` |
| Glitch | `hue=s=0` + `edgedetect=mode=colormix` |
| Vignette | `drawbox=y=0:color=black@0.5:w=iw:h=ih:t=fill` |
| Sepia | `colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131` |

**Why filter strings instead of a builder pattern?** FFmpeg has 400+ filters. A C++ builder would need to be regenerated every FFmpeg version. Filter strings delegate the complexity to FFmpeg's parser (`avfilter_graph_parse_ptr`) and let us add new effects without recompiling the native library — just send a new string from Kotlin.

**Input/output format RGBA throughout.** The filter graph is configured for `AV_PIX_FMT_RGBA`. Input frames are already RGBA from the decoder. Output frames are converted back to RGBA via a second `sws_scale` (in case the filter chain changed the pixel format internally).

**`time_base=1/30` — hardcoded.** The filter graph needs a time_base for timestamp calculations. 1/30 is reasonable for video editing. This matches the default 30fps assumption elsewhere and can be parameterized later if needed.

**Independent `sws_in`/`sws_out`.** Separate cached SwsContexts for input and output scaling. This handles the case where input resolution differs from filter graph resolution (e.g., reducing to 720p for processing) or output resolution differs from filter graph resolution (e.g., filter graph produces 1080p, but preview needs 360p).

---

## Compositor Design

### API Surface
```c
bool composite_frame(uint8_t* bg_rgba, int bg_w, int bg_h,
                      const uint8_t* fg_rgba, int fg_w, int fg_h,
                      int pos_x, int pos_y, float opacity);

bool composite_with_mask(uint8_t* bg_rgba, const uint8_t* fg_rgba,
                          const float* mask, int width, int height,
                          float intensity);

bool composite_checkerboard(uint8_t* rgba, int width, int height,
                             int tile_size);
```

### Key Decisions

**Stateless, no FFmpeg dependency.** Compositing is pure pixel math — no AVFrame, no SwsContext. This means it could be moved to a header-only utility or even to Kotlin (via RenderScript/AGX) without changing the pipeline. Keeping it in the native layer avoids per-pixel JNI overhead.

**`composite_frame` — overlay with position.** Places `fg` onto `bg` at `(pos_x, pos_y)` with per-pixel opacity blending. Used for: watermark overlay, PiP (picture-in-picture), sticker/text overlay backgrounds. The clamp function prevents integer overflow from repeated compositing passes.

**`composite_with_mask` — mask-guided blend.** Each pixel of `mask` (as `float`, range 0-1) controls the blend factor for the corresponding pixel. `intensity` is a master opacity multiplier on the mask. Used for: background replacement (mask from SINet), selective effects (mask → apply effect only to detected person), transitions (wipe mask).

**`composite_checkerboard` — utility for debugging.** Fills the buffer with a checkerboard pattern. Used during development to verify that pixel buffers are correctly sized, aligned, and rendering — much faster to debug than loading a video file.

**No alpha compositing on alpha channel (index 3).** The RGBA buffers use index 3 for alpha, but `composite_frame` and `composite_with_mask` only blend RGB. Alpha is preserved from `bg`. This avoids alpha accumulation issues during multi-pass compositing (e.g., layer 10 effects on one clip).

---

## Temporal Design

### API Surface
```c
int temporal_speed_change(const uint8_t* src_frames, int src_count,
                           float speed, uint8_t* dst_frames,
                           int dst_capacity_bytes, int width, int height);

int temporal_reverse(const uint8_t* src_frames, int src_count,
                      uint8_t* dst_frames, int dst_capacity_bytes,
                      int width, int height);

void temporal_blend_frames(const uint8_t* a, const uint8_t* b,
                            uint8_t* out, float factor,
                            int width, int height);
```

### Key Decisions

**Batch processing on frame arrays, not individual frames.** Speed change needs to see multiple source frames to compute output frames. The API accepts contiguous frame arrays (packed RGBA buffers) and returns a packed output array. The JNI passes these as large direct ByteBuffers containing multiple frames.

**Why batch instead of streaming?** For preview, you need to seek to a position and see a few frames at the new speed — that requires temporal context. For export, the whole clip's frames are available. Batch processing lets both paths share the same code.

**`speed >= 1.0` → frame skip/stride.** Drops frames to achieve fast motion. `speed=2` takes every 2nd frame. This is lossy but requires zero computation — just `memcpy`. Quality degrades gracefully (choppy motion at extreme speeds, e.g., 4×), which is expected.

**`speed < 1.0` → frame blending (linear interpolation).** Slow motion needs frames that don't exist in the source. Linear blend between adjacent frames at fractional time positions produces smooth slo-mo without motion vectors. The blend is weighted: `out = a * (1-frac) + b * frac`.

**Why not motion-interpolated slo-mo (optical flow)?** That requires dense motion estimation (neural network or OpenCV's `calcOpticalFlowFarneback`). It looks better but is 100-1000× slower and would block real-time preview. Linear blend is the standard trade-off for mobile video editors (CapCut, InShot do the same).

**`temporal_reverse` — simple mirror copy.** Copies frames in reverse order. No frame blending needed since no new frames are synthesized. `dst_count = min(src_count, capacity)` handles the buffer-size check without overflow.

**`temporal_blend_frames` — cross-fade between two frames.** Used for transitions (dissolve), gain/trim handles, and multi-frame persistence effects. Factor `0` returns frame A, factor `1` returns frame B.

---

## JNI Bridge Design

### Single-file pattern

All 20 `extern "C"` functions live in `jni_bridge.cpp`. The naming convention follows the JNI mangling scheme: `Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeFunctionName`.

**Why one file instead of one per component?** JNI function names are tied to the Kotlin package and class name. Splitting them would require duplicating the package declaration conventions or creating fragile `#include` dependencies. A single file is the convention used by NDK samples and production apps (ExoPlayer, ijkPlayer).

### Handle Pattern

```cpp
jlong handle = reinterpret_cast<jlong>(decoder_open(c_path));
// later:
FFDecoder* d = reinterpret_cast<FFDecoder*>(handle);
decoder_close(d);
```

**Risks and mitigations:**
- Passing a wrong handle (e.g., encoder handle to decoder function) → crash. Mitigated by consistent naming: every `nativeDecoder*` function casts to `FFDecoder*`.
- Double-close → crash. The engine functions are idempotent: `decoder_close(nullptr)` is a no-op.
- Use after close → undefined behavior. Kotlin must nullify handles after close. The Kotlin API does NOT automatically nullify — the caller is responsible. Future improvement: wrap in a `Closeable` class.

### Buffer Access Pattern

```cpp
void* buf = env->GetDirectBufferAddress(buffer);
jlong cap = env->GetDirectBufferCapacity(buffer);
decoder_read_frame(d, (uint8_t*)buf, (int)cap);
```

**Why check capacity?** Guards against Kotlin passing an undersized buffer. A crash in native code from writing past the buffer would be a security issue (buffer overflow). The decoder returns `false` if `buffer_size < frame_size`.

**Why not `GetByteArrayRegion` for non-direct buffers?** Direct buffers are the intended pattern for high-throughput pixel data. Non-direct (heap) ByteBuffers would require a temporary allocation and copy.

---

## Kotlin API Design

```kotlin
object NativeFFmpeg {
    init { System.loadLibrary("ffmpeg") }
    
    fun decoderOpen(path: String): Long = nativeDecoderOpen(path)
    fun decoderClose(handle: Long) = nativeDecoderClose(handle)
    // ... wrapper functions ...
    
    private external fun nativeDecoderOpen(path: String): Long
    // ... native declarations ...
}
```

**Singleton `object` instead of `class`.** Only one native library load is needed. Making it an `object` with `init { loadLibrary }` ensures the library loads exactly once, on first access, and is never accidentally re-loaded.

**Wrapper functions with defaults.** The native functions are `private external`. Public wrapper functions add:
- Clear naming without `native` prefix
- Default parameters (`bitRate = 8_000_000`, `opacity = 1f`)
- Type conversions (`millifps` → `Double` fps)
- Documentation surface for IDE tooltips

**Why not instance methods on a class?** The handles (`Long`) are already the instance identity. Wrapping them in a Kotlin object adds no safety (you can still pass the wrong handle) but adds allocation overhead and `close()` boilerplate. A `class NativeDecoder(handle: Long)` wrapper could be added later as syntactic sugar.

---

## Data Flow Summary

### Preview Path (decode single frame → display)
```
Kotlin: allocateDirect ByteBuffer(w*h*4)
     → decoderReadFrame(handle, buffer)
C++:    av_read_frame → avcodec_send_packet → avcodec_receive_frame
     → sws_scale to RGBA → memcpy(buffer, rgb_buffer)
Kotlin: Bitmap.copyPixelsFromBuffer(buffer) → ImageBitmap → Canvas/compose
```

### Export Path (decode → filter → encode, all in C++)
```
Kotlin: decoderOpen(input) → encoderOpen(output, width, height, fps, bitrate)
     → createFilterGraph(width, height, filterDesc)
     → while(decoderReadFrame(handle, inBuffer)) {
         filterGraphProcess(fgHandle, inBuffer, w, h, outBuffer, w, h)
         encoderEncodeFrameRgba(encHandle, outBuffer)
       }
     → encoderFinalize(encHandle)
     → decoderClose(decHandle) → filterGraphClose(fgHandle) → encoderClose(encHandle)
```

### Compositor Path (mask-guided blend)
```
Kotlin: compositeWithMask(bgBuffer, fgBuffer, maskBuffer, w, h, intensity)
C++:    for each pixel: bg[i] = bg[i] * (1 - mask[i]*intensity) + fg[i] * (mask[i]*intensity)
Kotlin: the blended bgBuffer is ready for display or encode
```

---

## Future Considerations

- **Hardware decoder (MediaCodec).** Replace `avcodec_find_decoder` with `avcodec_find_decoder_by_name("h264_mediaplayer")` or the newer `mediacodec` wrapper. Requires the prebuilt FFmpeg to include `--enable-mediacodec`. Currently falls back to software decode.

- **Audio pipeline.** The current ffmpeg module ignores audio streams. Add `AudioDecoder`/`AudioEncoder` structs using `libswresample` for mixing, ducking, and encoding AAC via MediaCodec.

- **Thread safety.** None of the structures use locks. Each handle should be used from one thread at a time. Multi-track editing will need either per-track handles or a reader-writer lock.

- **Frame pool.** `decoder_read_frame` allocates/frees an `AVPacket` every call. A pre-allocated packet pool would reduce GC pressure during export. Not critical for single-frame preview.

- **Error strings.** All functions return `bool`. No error details propagate to Kotlin. Add `decoder_last_error(handle)` returning a string for UI display ("File not found", "Codec not supported", etc.).
