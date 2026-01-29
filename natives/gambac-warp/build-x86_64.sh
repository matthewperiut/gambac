#!/bin/bash
# Build libgambac-warp.so for x86_64 (native)
#
# Prerequisites (Arch Linux):
#   sudo pacman -S --needed gcc wayland
# Prerequisites (Ubuntu/Debian):
#   sudo apt install gcc libwayland-dev
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../../src/main/resources/natives/linux-x86_64"

mkdir -p "$OUTPUT_DIR"

gcc -shared -fPIC -fvisibility=hidden -O2 \
    -o "$OUTPUT_DIR/libgambac-warp.so" \
    "$SCRIPT_DIR/gambac-warp.c" \
    -lwayland-client

echo "=== BUILD SUCCESS ==="
file "$OUTPUT_DIR/libgambac-warp.so"
