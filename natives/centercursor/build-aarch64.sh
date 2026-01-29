#!/bin/bash
# Build libcenter.so for aarch64 via Docker
#
# Prerequisites (Arch Linux):
#   sudo pacman -S --needed docker qemu-user-static qemu-user-static-binfmt
#   sudo systemctl restart systemd-binfmt
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../../src/main/resources/natives/linux-aarch64"
mkdir -p "$OUTPUT_DIR"

# Ensure Docker prerequisites
if ! command -v docker &>/dev/null; then
    echo "Installing docker..."
    sudo pacman -S --needed --noconfirm docker
fi
if ! pacman -Qi qemu-user-static &>/dev/null; then
    echo "Installing qemu-user-static..."
    sudo pacman -S --needed --noconfirm qemu-user-static qemu-user-static-binfmt
fi
sudo systemctl restart systemd-binfmt
if ! systemctl is-active --quiet docker; then
    sudo systemctl start docker
fi

sudo docker run --rm --platform linux/arm64 \
    -v "$SCRIPT_DIR":/build \
    -v "$(cd "$OUTPUT_DIR" && pwd)":/output \
    debian:bookworm bash -c "
apt-get update &&
apt-get install -y gcc pkg-config libwayland-dev wayland-protocols &&
cd /build &&
wayland-scanner client-header /usr/share/wayland-protocols/unstable/pointer-constraints/pointer-constraints-unstable-v1.xml pointer-constraints-client-protocol.h &&
wayland-scanner private-code /usr/share/wayland-protocols/unstable/pointer-constraints/pointer-constraints-unstable-v1.xml pointer-constraints-protocol.c &&
cc -shared -fPIC -fvisibility=hidden -o /output/libcenter.so main.c pointer-constraints-protocol.c \$(pkg-config --cflags --libs wayland-client) -lrt &&
rm -f pointer-constraints-client-protocol.h pointer-constraints-protocol.c
"

echo ""
echo "=== BUILD SUCCESS ==="
file "$OUTPUT_DIR/libcenter.so"
