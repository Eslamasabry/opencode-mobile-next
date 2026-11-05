#!/usr/bin/env bash
# gate_order.sh <NN> — the one gate of a native UI rebuild order (docs/design/native-ui-rebuild/GOLDEN-STANDARD.md §9).
# Run from anywhere inside the order's worktree on the VPS. Exits 0 only when every check passes:
#   1. static checks (write set, Flutter-cited numbers, kit-only, Material, copy, signatures, commits)  rb_gate.py static
#   2. the order's Flutter goldens copied into looks-gate/flutter/<ref9>/                                 rb_gate.py fetch
#   3. the order's gate cases: <module>:desktopTest --tests '*.rb.o<NN>.*' (FlutterGoldenGate v2 writes
#      looks-out/gate/rows/*.tsv, looks-out/gate/matrix/*.tsv and the flutter/native/diff PNGs)
#   4. every golden on its ink box against gate_thresholds.tsv, waivers, matrix; tracker updated          rb_gate.py evaluate
# Environment: RB_DEV=1 while iterating (skips the clean-tree and commit checks; never for the final run).
#   FLUTTER_REPO (default ~/wt/flutter-ref), GLASS_REPO (default ~/Storage/Code/oc_app), RB_BASE (default
#   native/looks-pass-2026-11-04), JAVA_HOME / ANDROID_HOME (defaults below), RB_GRADLE_SLOTS (default 2).
set -uo pipefail
[ $# -ge 1 ] || { echo "usage: gate_order.sh <NN>" >&2; exit 2; }
NN=$(printf '%02d' "$((10#$1))")
ROOT=$(git rev-parse --show-toplevel) || exit 2
cd "$ROOT" || exit 2
T=native/tool/design
PY="python3 -I $ROOT/$T/rb_gate.py"

echo "== order $NN: static checks"
$PY static --order "$NN" || { echo "GATE FAIL (static)"; exit 1; }

echo "== order $NN: Flutter goldens"
$PY fetch --order "$NN" || { echo "GATE FAIL (fetch)"; exit 1; }

echo "== order $NN: gate cases"
rm -rf "$ROOT/looks-out/gate"
mkdir -p "$ROOT/looks-out/gate/rows" "$ROOT/looks-out/gate/matrix"
MODS=$($PY modules --order "$NN")
args=()
for m in $MODS; do args+=("$m:desktopTest" --tests "*.rb.o$NN.*"); done
if [ ${#args[@]} -gt 0 ]; then
  export JAVA_HOME=${JAVA_HOME:-$HOME/wt/native-repair-20261102/tools/jdk}
  export PATH=$JAVA_HOME/bin:$PATH
  export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
  export LD_LIBRARY_PATH=${LD_LIBRARY_PATH:-$HOME/wt/native-repair-20261102/tools/runtime-libs/root/usr/lib/x86_64-linux-gnu}
  slots=${RB_GRADLE_SLOTS:-2}
  rc=99
  for _ in $(seq 1 720); do                      # wait up to 2 h for a free Gradle slot on the shared VPS
    for s in $(seq 1 "$slots"); do
      exec 9>"$HOME/.oc-rb-gradle.$s.lock"
      if flock -n 9; then
        (cd native && ./gradlew "${args[@]}" --continue -Dorg.gradle.workers.max=2 \
          "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g" -Pkotlin.daemon.jvmargs=-Xmx4g) \
          > "$ROOT/looks-out/gate/gradle.log" 2>&1
        rc=$?
        flock -u 9; exec 9>&-
        break 2
      fi
      exec 9>&-
    done
    sleep 10
  done
  if [ "$rc" -eq 99 ]; then echo "GATE FAIL (no Gradle slot within 2 h)"; exit 1; fi
  if [ "$rc" -ne 0 ]; then
    grep -E "FAILED|error:|e: |AssertionError|No tests found" "$ROOT/looks-out/gate/gradle.log" | head -40
    echo "GATE FAIL (Gradle exit $rc; full log looks-out/gate/gradle.log)"; exit 1
  fi
fi

echo "== order $NN: evaluation"
$PY evaluate --order "$NN"; rc=$?
[ $rc -eq 0 ] && echo "GATE PASS" || echo "GATE FAIL (evaluation)"
exit $rc
