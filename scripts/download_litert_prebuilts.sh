#!/usr/bin/env bash
set -euo pipefail

# Downloads the LiteRT v2.1.5 AAR from Google Maven and extracts it
# into segment/prebuilts/ for NDK integration.
# Run this on a fresh checkout to populate the gitignored prebuilts dir.
# Usage: ./scripts/download_litert_prebuilts.sh

LITERT_VERSION="${1:-2.1.5}"
AAR_URL="https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/$LITERT_VERSION/litert-$LITERT_VERSION.aar"
OUTDIR="segment/prebuilts"
TMPDIR=$(mktemp -d)

echo "Downloading LiteRT v$LITERT_VERSION AAR..."
curl -fsSL "$AAR_URL" -o "$TMPDIR/litert-$LITERT_VERSION.aar"

mkdir -p "$OUTDIR/litert-$LITERT_VERSION"
cp "$TMPDIR/litert-$LITERT_VERSION.aar" "$OUTDIR/litert-$LITERT_VERSION.aar"

echo "Extracting..."
unzip -q "$TMPDIR/litert-$LITERT_VERSION.aar" -d "$OUTDIR/litert-$LITERT_VERSION"

# Also copy .so to jniLibs if not already present
mkdir -p segment/src/main/jniLibs/arm64-v8a
cp "$OUTDIR/litert-$LITERT_VERSION/jni/arm64-v8a/libLiteRt.so" \
   "segment/src/main/jniLibs/arm64-v8a/libLiteRt.so"

rm -rf "$TMPDIR"
echo "Done. Files installed at $OUTDIR/litert-$LITERT_VERSION/"
ls -lh "$OUTDIR/litert-$LITERT_VERSION.aar"
