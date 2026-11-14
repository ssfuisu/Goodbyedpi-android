#!/usr/bin/env bash
# Fetches the pinned native tun2socks dependency.
set -euo pipefail
HEV_VERSION="2.9.3"
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jni/hev-socks5-tunnel"
if [ -f "$DIR/Android.mk" ]; then
  exit 0
fi
git clone --depth 1 --branch "$HEV_VERSION" --recurse-submodules --shallow-submodules \
  https://github.com/heiher/hev-socks5-tunnel "$DIR"
