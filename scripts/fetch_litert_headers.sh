#!/usr/bin/env bash
set -euo pipefail

# Fetches LiteRT C API headers from the official google-ai-edge/LiteRT repo.
# Run this to update headers when upgrading the LiteRT runtime library.
# Usage: ./scripts/fetch_litert_headers.sh

LITERT_REF="${1:-v2.1.5}"
BASE_URL="https://raw.githubusercontent.com/google-ai-edge/LiteRT/$LITERT_REF"
OUTDIR="segment/src/main/cpp/tflite_include"

HEADERS=(
  tflite/core/c/c_api.h
  tflite/core/c/c_api_types.h
  tflite/core/c/common.h
  tflite/core/c/operator.h
  tflite/builtin_ops.h
  tflite/core/async/c/types.h
  # transitive dep of c_api_types.h
  tflite/converter/core/c/tflite_types.h
  # delegate support
  tflite/delegates/xnnpack/xnnpack_delegate.h
)

echo "Fetching LiteRT C API headers (ref: $LITERT_REF)..."

for header in "${HEADERS[@]}"; do
  mkdir -p "$OUTDIR/$(dirname "$header")"
  echo "  $header"
  curl -fsSL "$BASE_URL/$header" -o "$OUTDIR/$header"
done

echo "Done. $(find "$OUTDIR" -type f | wc -l) headers written to $OUTDIR/"
