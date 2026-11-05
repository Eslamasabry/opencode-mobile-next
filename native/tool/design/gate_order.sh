#!/usr/bin/env bash
# gate_order.sh <NN> — the one gate of a native UI rebuild order (docs/design/native-ui-rebuild/GOLDEN-STANDARD.md §9).
# Run from anywhere inside the order's worktree on the VPS. Exits 0 only when every check passes:
#   1. static checks (write set, Flutter-cited numbers, kit-only, Material, copy, signatures, commits)  rb_gate.py static
#   2. the order's Flutter goldens copied into looks-gate/flutter/<ref9>/                                 rb_gate.py fetch
#   3. the order's gate cases: <module>:desktopTest --tests '*.rb.o<NN>.*' (FlutterGoldenGate v2 writes
#      looks-out/gate/rows/*.tsv, looks-out/gate/matrix/*.tsv and the flutter/native/diff PNGs)
#   4. every golden on its ink box against gate_thresholds.tsv, waivers, matrix; tracker updated          rb_gate.py evaluate
# RB_PARTS="part-a part-b": gate only those parts (their goldens, matrix, caps, write set and Gradle test classes).
# Gradle runs desktopTest only (no Android variants); configuration and build cache come from native/gradle.properties,
# Kotlin compiles in the Gradle daemon (kotlin.compiler.execution.strategy=in-process: no Kotlin daemon is started).
# Environment: RB_DEV=1 while iterating (skips the clean-tree and commit checks; never for the final run).
#   FLUTTER_REPO (default ~/wt/flutter-ref), GLASS_REPO (default ~/Storage/Code/oc_app), RB_BASE (default
#   native/looks-pass-2026-11-04), JAVA_HOME / ANDROID_HOME (defaults below), RB_GRADLE_SLOTS (default 2).
set -uo pipefail
# gate_order.sh --recheck <MM> (run by `rb_gate.py recheck`, with RB_PARTS and RB_RECHECK set): the render and evaluation
#   of a merged order's shots that waited for order RB_RECHECK (DEPENDS_ON); no static checks, the order is merged.
RECHECK_RUN=0
if [ "${1:-}" = "--recheck" ]; then RECHECK_RUN=1; shift; [ -n "${RB_RECHECK:-}" ] || { echo "--recheck needs RB_RECHECK" >&2; exit 2; }; fi
[ $# -ge 1 ] || { echo "usage: gate_order.sh <NN>" >&2; exit 2; }
case "$1" in *[!0-9]*) NN=$1 ;; *) NN=$(printf '%02d' "$((10#$1))") ;; esac
ROOT=$(git rev-parse --show-toplevel) || exit 2
cd "$ROOT" || exit 2
T=native/tool/design
PY="python3 -I $ROOT/$T/rb_gate.py"

if [ "$RECHECK_RUN" = 1 ]; then
  echo "== order $NN: recheck of shots that waited for order $RB_RECHECK (static checks skipped: merged order)"
else
  echo "== order $NN: static checks"
  $PY static --order "$NN" || { echo "GATE FAIL (static)"; exit 1; }
fi

echo "== order $NN: Flutter goldens"
$PY fetch --order "$NN" || { echo "GATE FAIL (fetch)"; exit 1; }

echo "== order $NN: gate cases"
rm -rf "$ROOT/looks-out/gate"
mkdir -p "$ROOT/looks-out/gate/rows" "$ROOT/looks-out/gate/matrix"
args=()
while IFS=$'\t' read -r m filters; do          # RB_PARTS: only those parts' test classes (rb_gate.py tests)
  [ -n "$m" ] || continue
  args+=("$m:desktopTest")
  for f in $filters; do args+=(--tests "$f"); done
done < <($PY tests --order "$NN")
[ -n "${RB_PARTS:-}" ] && echo "   parts: $RB_PARTS -> ${args[*]:-no gate cases}"
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
          "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g") \
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
