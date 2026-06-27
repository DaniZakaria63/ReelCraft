# ReelCraft

A fully-local, privacy-first Android video editor where every effect is powered by FFmpeg and on-device AI — running entirely in native C++ with zero cloud dependency.

## Mission

Video editing today means uploading footage to someone else's servers. ReelCraft flips that: your video never leaves the device. Every frame is decoded, analyzed, styled, and encoded right on your phone. Works in airplane mode. No uploads. No inference costs. No privacy tradeoffs.

## How it works

The engine is split into three independent modules, each with a single responsibility:

**ffmpeg** — All video processing lives here. Decoding, encoding, filter chains (color grade, pixel art, glitch, pencil sketch, transitions), and pixel-level compositing. No AI. No network. Just FFmpeg running against prebuilt shared libraries for arm64-v8a.

**segment** — All AI inference lives here. A 241KB SINet model segments person from background in ~5ms on NPU. The TFLite runtime runs in C++ with GPU delegate support. The model ships with the app — no download required for core features. An optional style transfer model (~300MB) can be downloaded on demand for neural effects.

**app** — The Kotlin/Compose layer that ties everything together. UI screens, pipeline orchestration, project state, file I/O, and export flow. It never touches FFmpeg or TFLite directly — it calls JNI APIs exposed by the other two modules.

The key insight: ~70% of features work with FFmpeg alone. Adding SINet unlocks selective person/background effects (Van Gogh portrait, cyberpunk, ghost trail, silhouette glow). The neural style model is entirely optional and adds ~5% more.

## Features

**Core editing** — Trim, split, speed ramp, reverse, transitions, text overlay, multi-track video and audio, color grading.

**AI-powered presets** — Van Gogh Portrait, Cyberpunk, Watercolor, Random Line Art, Pencil Sketch, Pixel Art, Glitch, Ink Splash, Silhouette Glow, Ghost Trail. Each preset is a JSON recipe combining mask target, style parameters, and animation behavior.

**Export** — H.264/H.265 via MediaCodec hardware encoding. 480p, 720p, 1080p. MP4 container. Share directly to Gallery, Instagram, TikTok, or Reels.

## Project structure

```
app/                  — Kotlin/Compose UI, pipeline orchestration, project state
ffmpeg/               — FFmpeg native C++ library (decode, encode, filter, composite, temporal)
segment/              — ML native C++ library (TFLite, SINet, model management)
```

Each library module is self-contained with its own JNI surface. The app never links native code directly — it calls `NativeFFmpeg.kt` and `NativeSegment.kt` through their public Kotlin APIs.

## Build

```sh
git clone https://github.com/danizakaria63/reelcraft
cd reelcraft
./gradlew assembleDebug
```

Requires:
- Android SDK 37
- NDK 27.0.12077973
- arm64-v8a target (only ABI)
- minSdk 27
