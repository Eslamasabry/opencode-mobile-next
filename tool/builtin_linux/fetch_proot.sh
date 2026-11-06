#!/usr/bin/env bash
# Fetches the pinned proot, its loader and the two libraries it needs from the
# Termux package archive, checks every download against its SHA-256, and
# installs them as the app's native libraries.
#
# Why native libraries: an app targeting Android 10+ may not run programs from
# its own storage, but it may run what the APK ships under jniLibs, which the
# system unpacks into a read-only, executable folder. proot then runs Ubuntu's
# programs itself through its loader, so nothing is executed from app storage.
#
# Android only unpacks files named lib*.so, so the files are renamed, and the
# one dependency declared under another name (libtalloc.so.2) is rewritten
# with patchelf. The Termux RUNPATH is dropped: the app sets LD_LIBRARY_PATH.
#
# Usage: tool/builtin_linux/fetch_proot.sh [patchelf]
set -euo pipefail

PATCHELF=${1:-patchelf}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT="$ROOT/android/app/src/main/jniLibs"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

MIRROR=https://packages-cf.termux.dev/apt/termux-main/pool/main

# name|path in the archive|sha256, per architecture.
declare -A PACKAGES=(
  [aarch64]="
p/proot/proot_5.1.107.102_aarch64.deb|10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030
libt/libtalloc/libtalloc_2.5.0_aarch64.deb|556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7
liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb|0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6"
  [x86_64]="
p/proot/proot_5.1.107.102_x86_64.deb|2497064226c15971be8131c7facead94a06c225347796326a383c832dca57758
libt/libtalloc/libtalloc_2.5.0_x86_64.deb|b8c6d95f20075dc1f9ec6573575b2444e8d526e48e0d8d6d5cf4e071e6e06530
liba/libandroid-shmem/libandroid-shmem_0.7_x86_64.deb|ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797"
)
declare -A ABI=([aarch64]=arm64-v8a [x86_64]=x86_64)

for arch in aarch64 x86_64; do
  unpacked="$WORK/$arch"
  mkdir -p "$unpacked"
  while IFS='|' read -r path sha; do
    [ -n "$path" ] || continue
    deb="$WORK/$(basename "$path")"
    curl -fsSL --retry 3 -o "$deb" "$MIRROR/$path"
    echo "$sha  $deb" | sha256sum -c --quiet -
    (cd "$unpacked" && ar p "$deb" data.tar.xz | tar xJ)
  done <<<"${PACKAGES[$arch]}"

  usr="$unpacked/data/data/com.termux/files/usr"
  dest="$OUT/${ABI[$arch]}"
  mkdir -p "$dest"
  install -m 0755 "$usr/bin/proot" "$dest/libproot.so"
  install -m 0755 "$usr/libexec/proot/loader" "$dest/libproot-loader.so"
  install -m 0755 "$usr/lib/libtalloc.so.2.5.0" "$dest/libtalloc.so"
  install -m 0755 "$usr/lib/libandroid-shmem.so" "$dest/libandroid-shmem.so"

  "$PATCHELF" --replace-needed libtalloc.so.2 libtalloc.so "$dest/libproot.so"
  "$PATCHELF" --remove-rpath "$dest/libproot.so"
  "$PATCHELF" --set-soname libtalloc.so "$dest/libtalloc.so"
  "$PATCHELF" --remove-rpath "$dest/libtalloc.so"
  "$PATCHELF" --remove-rpath "$dest/libandroid-shmem.so" 2>/dev/null || true
done

echo "Installed into $OUT:"
find "$OUT" -name '*.so' -printf '  %P %s bytes\n' | sort
