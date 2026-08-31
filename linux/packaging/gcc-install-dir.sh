#!/usr/bin/env bash
# Print a GCC installation directory that clang can actually link C++ against,
# or print nothing when clang's own default is already fine.
#
# Some hosts (Pop!_OS 24.04 among them) ship clang 18 alongside a GCC 14 tree
# that has the headers but not `libstdc++.so`. clang auto-selects the newest
# GCC tree it finds, so `flutter build linux` dies with `cannot find -lstdc++`
# even though a complete older toolchain is installed next to it. Passing
# `--gcc-install-dir=<dir>` in CXXFLAGS and LDFLAGS points clang at the
# complete tree.
#
# Usage:
#   dir="$(linux/packaging/gcc-install-dir.sh)"
#   if [ -n "$dir" ]; then
#     export CXXFLAGS="${CXXFLAGS:-} --gcc-install-dir=$dir"
#     export LDFLAGS="${LDFLAGS:-} --gcc-install-dir=$dir"
#   fi
#
# Prints nothing (exit 0) when the default toolchain links cleanly, so the
# flag is never added on a normal CI runner that does not need it.
set -euo pipefail

probe="$(mktemp -d)"
trap 'rm -rf "$probe"' EXIT

cat > "$probe/probe.cc" <<'EOF'
#include <string>
int main() { return std::string("ok").size() == 2 ? 0 : 1; }
EOF

compiler="${CXX:-clang++}"
command -v "$compiler" >/dev/null 2>&1 || compiler="c++"
command -v "$compiler" >/dev/null 2>&1 || exit 0

# Default toolchain works: say nothing.
if "$compiler" "$probe/probe.cc" -o "$probe/probe" >/dev/null 2>&1; then
  exit 0
fi

# Otherwise take the newest GCC tree that actually ships libstdc++.so and
# prove that clang can link with it before recommending it.
for candidate in $(printf '%s\n' /usr/lib/gcc/*/* | sort -V -r); do
  [ -e "$candidate/libstdc++.so" ] || continue
  if "$compiler" "--gcc-install-dir=$candidate" \
       "$probe/probe.cc" -o "$probe/probe" >/dev/null 2>&1; then
    printf '%s\n' "$candidate"
    exit 0
  fi
done

echo "WARNING: C++ linking fails and no usable --gcc-install-dir was found;" >&2
echo "         install libstdc++-<version>-dev matching your clang." >&2
exit 0
