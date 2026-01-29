#!/bin/bash
# Build libgambac-warp.so for aarch64 (cross-compile or native)
#
# Prerequisites (Arch Linux):
#   sudo pacman -S --needed aarch64-linux-gnu-gcc
#   # Plus aarch64 wayland-client library
# Prerequisites (native aarch64):
#   sudo pacman -S --needed gcc wayland
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../../src/main/resources/natives/linux-aarch64"

mkdir -p "$OUTPUT_DIR"

# Use cross-compiler if available, otherwise native gcc
CC="${CROSS_CC:-aarch64-linux-gnu-gcc}"
if ! command -v "$CC" &>/dev/null; then
    CC=gcc
fi

$CC -shared -fPIC -fvisibility=hidden -O2 \
    -o "$OUTPUT_DIR/libgambac-warp.so" \
    "$SCRIPT_DIR/gambac-warp.c" \
    -lwayland-client

echo "=== BUILD SUCCESS ==="
file "$OUTPUT_DIR/libgambac-warp.so"
