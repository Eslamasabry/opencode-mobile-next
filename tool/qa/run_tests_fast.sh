#!/usr/bin/env bash
# Full local Flutter suite, fast: N timing-balanced shards run side by side.
#
#   tool/qa/run_tests_fast.sh                 # 4 shards x --concurrency 2
#   tool/qa/run_tests_fast.sh --shards 3 --concurrency 2
#
# Every shard is one tool/qa/run_serial_tests.py run (--shard-index i
# --shard-count N --json-report), so together they run every
# test/**/*_test.dart exactly once, balanced by tool/qa/test_timings.json.
# Each shard takes one machine_lock.sh test slot (OC_TEST_SLOTS, default 4), so
# with fewer free slots the shards queue instead of overloading the machine.
# At the end it writes summary.txt and summary.json (status per shard, the
# failing files) into the output folder and exits non-zero unless all passed.
#
# Options (or environment): --shards (OC_FAST_SHARDS, 4), --concurrency
# (OC_FAST_CONCURRENCY, 2), --chunk-size (OC_FAST_CHUNK_SIZE, 1000 = one
# chunk per shard), --chunk-timeout (OC_FAST_CHUNK_TIMEOUT, 3600 s), --flutter
# (FLUTTER, the pinned Shorebird Flutter), --output-root (default
# build/traycer/fast-<UTC time>). Ctrl-C stops every shard and the Flutter
# processes it started (by captured process group, never by name).
set -euo pipefail
# Goldens print clock times from UTC fixtures in the local zone; CI runs in
# UTC, so every machine does.
export TZ=UTC

root="$(cd "$(dirname "$0")/../.." && pwd)"
shards="${OC_FAST_SHARDS:-4}"
concurrency="${OC_FAST_CONCURRENCY:-2}"
chunk_size="${OC_FAST_CHUNK_SIZE:-1000}"
chunk_timeout="${OC_FAST_CHUNK_TIMEOUT:-3600}"
flutter="${FLUTTER:-$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter}"
output=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --shards) shards="$2"; shift 2 ;;
    --concurrency) concurrency="$2"; shift 2 ;;
    --chunk-size) chunk_size="$2"; shift 2 ;;
    --chunk-timeout) chunk_timeout="$2"; shift 2 ;;
    --flutter) flutter="$2"; shift 2 ;;
    --output-root) output="$2"; shift 2 ;;
    -h | --help) sed -n '2,21p' "$0"; exit 0 ;;
    *) echo "run_tests_fast: unknown argument $1" >&2; exit 64 ;;
  esac
done
for value in "$shards" "$concurrency" "$chunk_size" "$chunk_timeout"; do
  [[ "$value" =~ ^[0-9]+$ ]] || { echo "run_tests_fast: not a number: $value" >&2; exit 64; }
done
((shards >= 1)) || { echo "run_tests_fast: --shards must be at least 1" >&2; exit 64; }
output="${output:-$root/build/traycer/fast-$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$output"
output="$(cd "$output" && pwd)"

# Job control gives every shard its own process group, so Ctrl-C reaches only
# this script, which then interrupts each shard group it started. The Python
# runner turns that SIGINT into a clean stop of its own Flutter group.
set -m
pids=()
stop_shards() {
  trap - INT TERM
  echo "run_tests_fast: stopping ${#pids[@]} shard(s)" >&2
  for pid in "${pids[@]}"; do kill -INT -- "-$pid" 2>/dev/null || true; done
  wait || true
  exit 130
}
trap stop_shards INT TERM

started=$(date +%s)
echo "run_tests_fast: $shards shards x --concurrency $concurrency -> $output"
for ((index = 1; index <= shards; index++)); do
  "$root/tool/qa/machine_lock.sh" test -- \
    python3 "$root/tool/qa/run_serial_tests.py" \
    --root "$root" \
    --flutter "$flutter" \
    --shard-index "$index" \
    --shard-count "$shards" \
    --concurrency "$concurrency" \
    --chunk-size "$chunk_size" \
    --chunk-timeout "$chunk_timeout" \
    --json-report \
    --output-root "$output/shard-$index" \
    >"$output/shard-$index.log" 2>&1 &
  pids+=("$!")
done

statuses=()
for pid in "${pids[@]}"; do
  status=0
  wait "$pid" || status=$?
  statuses+=("$status")
done
trap - INT TERM
elapsed=$(($(date +%s) - started))

python3 - "$root" "$output" "$shards" "$elapsed" "${statuses[@]}" <<'PY'
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(sys.argv[1]) / "tool" / "qa"))
import run_serial_tests as runner

root, output = Path(sys.argv[1]), Path(sys.argv[2])
shard_count, elapsed = int(sys.argv[3]), int(sys.argv[4])
exit_codes = [int(value) for value in sys.argv[5:]]
manifest = [entry["path"] for entry in runner.collect_test_manifest(root)]
shards, covered, failed = [], [], set()
for index in range(1, shard_count + 1):
    folder = output / f"shard-{index}"
    runs = sorted(path for path in folder.iterdir() if path.is_dir()) if folder.is_dir() else []
    entry = {"shard": index, "exit_code": exit_codes[index - 1], "log": f"shard-{index}.log"}
    if runs:
        summary = json.loads((runs[-1] / "summary.json").read_text(encoding="utf-8"))
        run = json.loads((runs[-1] / "run.json").read_text(encoding="utf-8"))
        tests = [path for chunk in run["chunks"] for path in chunk["tests"]]
        covered += tests
        entry.update(
            status=summary["status"],
            files=len(tests),
            seconds=round(sum(chunk.get("duration_seconds", 0) for chunk in summary["chunks"])),
            run=str(runs[-1].relative_to(output)),
            failed_files=summary.get("failed_files", []),
        )
        failed.update(entry["failed_files"])
    else:
        entry.update(status="not_started", files=0, seconds=0, failed_files=[])
    shards.append(entry)

complete = sorted(covered) == manifest
passed = complete and all(s["status"] == "passed" and s["exit_code"] == 0 for s in shards)
result = {
    "status": "passed" if passed else "failed",
    "elapsed_seconds": elapsed,
    "manifest_files": len(manifest),
    "covered_files": len(covered),
    "every_file_exactly_once": complete,
    "shards": shards,
    "failed_files": sorted(failed),
}
(output / "summary.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
lines = [
    f"{result['status'].upper()}: {len(covered)}/{len(manifest)} files in {elapsed // 60}m{elapsed % 60:02d}s"
    + ("" if complete else " (coverage incomplete: shards did not run every file exactly once)"),
]
for s in shards:
    lines.append(
        f"  shard {s['shard']}/{shard_count}: {s['status']} ({s['files']} files, {s['seconds']}s, exit {s['exit_code']}) {s['log']}"
    )
if failed:
    lines.append("Failing files:")
    lines += [f"  {path}" for path in sorted(failed)]
(output / "summary.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
print("\n".join(lines))
print(f"Summary: {output / 'summary.txt'}")
sys.exit(0 if passed else 1)
PY
