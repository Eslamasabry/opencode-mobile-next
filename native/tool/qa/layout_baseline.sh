#!/usr/bin/env bash
# The layout guard's baseline ratchet (E02-S13, native/docs/kit/LAYOUT-RULES.md §8). The guard itself runs inside each
# module's desktopTest (LayoutGuard via FlutterGoldenGate.check); this script keeps its baselines from growing.
#
#   native/tool/qa/layout_baseline.sh [--base <ref>]
#
#   --base <ref>  the ref to compare with (default: $OC_BASE, native/main, origin/native/main)
#   --kind <k>    which guard's baselines: layout (default, native/config/layout/baseline) or align (the alignment
#                 gate, native/config/align/baseline; tool/qa/align_baseline.sh)
#
# Fails when a file in native/config/layout/baseline/ gains a line, or a baseline file appears for a module that had
# none, compared with merge-base(<base>, HEAD). Before the base carries that folder at all (the commit that adopts the
# guard) it reports "bootstrap" and passes. Shrinking is always allowed and reported.
# Exit: 0 pass, 1 a baseline grew, 2 could not run (no git, unknown base).
set -uo pipefail
# shellcheck source=../lib/common.sh
source "$(dirname "$0")/../lib/common.sh"

base_arg="" kind="layout"
while (($#)); do
  case "$1" in
    --base) base_arg="${2:-}"; shift 2 ;;
    --kind) kind="${2:-}"; shift 2 ;;
    -h | --help) sed -n '2,15p' "$0"; exit 0 ;;
    *) oc_usage "layout_baseline.sh [--base R] [--kind layout|align]" ;;
  esac
done
case "$kind" in layout | align) ;; *) oc_usage "layout_baseline.sh [--base R] [--kind layout|align]" ;; esac

baseline_dir="$OC_NATIVE/config/$kind/baseline"
top="$(oc_repo_root)" || { echo "$kind ratchet: could not run (needs git)"; exit 2; }
base="$(oc_resolve_base "$base_arg")" || { echo "$kind ratchet: could not run (unknown base '${base_arg:-${OC_BASE:-native/main}}')"; exit 2; }
mb="$(git -C "$top" merge-base "$base" HEAD)" || { echo "$kind ratchet: could not run (no merge-base with $base)"; exit 2; }
rel="${baseline_dir#"$top/"}"
if [[ -z "$(git -C "$top" ls-tree -d --name-only "$mb" -- "$rel")" ]]; then
  echo "$kind ratchet: bootstrap ($base has no $rel yet; this change adopts the $kind guard)"
  exit 0
fi
python3 -I - "$top" "$mb" "$rel" "$kind" <<'PY'
import os, subprocess, sys
top, mb, rel, kind = sys.argv[1:5]
def lines(text):
    return {l for l in text.splitlines() if l.strip() and not l.startswith("#")}
def at_base(path):
    r = subprocess.run(["git", "-C", top, "show", f"{mb}:{path}"], capture_output=True, text=True)
    return lines(r.stdout) if r.returncode == 0 else None
grown = 0
here = os.path.join(top, rel)
for name in sorted(os.listdir(here)) if os.path.isdir(here) else []:
    if not name.endswith(".tsv"):
        continue
    path = f"{rel}/{name}"
    with open(os.path.join(here, name), encoding="utf-8") as f:
        now = lines(f.read())
    before = at_base(path)
    if before is None:
        if now:
            grown += 1
            print(f"{kind} ratchet: NEW baseline {path} with {len(now)} lines (a module without one starts at zero findings)")
        continue
    added = sorted(now - before)
    if added:
        grown += 1
        print(f"{kind} ratchet: {path} GREW by {len(added)} (fix the screen; baselines only shrink):")
        for a in added[:20]:
            print(f"  + {a}")
    elif len(now) < len(before):
        print(f"{kind} ratchet: {path} shrank {len(before)} -> {len(now)}")
print(f"{kind} ratchet: " + ("FAIL" if grown else "ok") + f" (compared with {mb[:12]})")
sys.exit(1 if grown else 0)
PY
