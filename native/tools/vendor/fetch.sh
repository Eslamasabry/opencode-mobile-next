#!/usr/bin/env bash
# Fetches the vendored, hash-pinned third-party artifacts into native/third_party/maven (plan 10 §2.2).
# Run once in every fresh worktree. The AAR is git-ignored (49 MB); its POM and SHA-256 are committed.
#
# sherpa-onnx 2.0.1 (the version the Flutter app pins in pubspec.lock): the Android AAR as published by the upstream
# release, mirrored by JitPack. Its libonnxruntime.so is byte-identical to the Flutter plugin's.
set -euo pipefail
here="$(cd "$(dirname "$0")/../.." && pwd)"
sums="$here/third_party/SHA256SUMS"

fetch() { # <relative path under third_party> <url>
  local rel=$1 url=$2 dest="$here/third_party/$1"
  local want; want=$(awk -v p="$rel" '$2 == p { print $1 }' "$sums")
  [ -n "$want" ] || { echo "no pinned hash for $rel in $sums" >&2; exit 1; }
  if [ -f "$dest" ] && [ "$(sha256sum "$dest" | cut -d' ' -f1)" = "$want" ]; then
    echo "ok   $rel"; return 0
  fi
  mkdir -p "$(dirname "$dest")"
  curl -fSL --retry 4 --retry-delay 2 -o "$dest.part" "$url"
  local got; got=$(sha256sum "$dest.part" | cut -d' ' -f1)
  if [ "$got" != "$want" ]; then
    rm -f "$dest.part"
    echo "SHA-256 mismatch for $rel: got $got, want $want" >&2; exit 1
  fi
  mv "$dest.part" "$dest"
  echo "got  $rel"
}

fetch maven/com/k2fsa/sherpa/onnx/sherpa-onnx-android/2.0.1/sherpa-onnx-android-2.0.1.aar \
  https://jitpack.io/com/github/k2-fsa/sherpa-onnx/sherpa-onnx/v2.0.1/sherpa-onnx-v2.0.1.aar

# Same Termux PTY/emulator release used by the Flutter app. No substitute renderer or shell pipes.
fetch maven/com/github/termux/termux-app/terminal-emulator/v0.118.3/terminal-emulator-v0.118.3.aar \
  https://jitpack.io/com/github/termux/termux-app/terminal-emulator/v0.118.3/terminal-emulator-v0.118.3.aar
fetch maven/com/github/termux/termux-app/terminal-view/v0.118.3/terminal-view-v0.118.3.aar \
  https://jitpack.io/com/github/termux/termux-app/terminal-view/v0.118.3/terminal-view-v0.118.3.aar

# Reproducible external-I/O base: remove only upstream TerminalSession classes; the native module compiles its licensed source fork.
python3 -I "$here/tools/vendor/termux_external.py" "$here"
