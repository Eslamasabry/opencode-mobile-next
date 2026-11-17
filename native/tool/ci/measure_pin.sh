#!/usr/bin/env bash
# measure_pin.sh <ref>... : run the core/net TransportTrustTest class N times (default 30) in a fresh Gradle test JVM
# each time (--rerun), on this checkout (ref "HEAD") and on each other ref given (a detached worktree), and count the
# runs that failed. Run from native/. Scratch measuring tool of the pin-test-race branch; never merged.
set -uo pipefail
N=${N:-30}
CLASSES=${CLASSES:-"io.github.eslamasabry.oc.core.net.okhttp.TransportTrustTest io.github.eslamasabry.oc.core.net.okhttp.LiveCallsTest"}
out="$PWD/reports/measure"; mkdir -p "$out"
summary="$out/summary.txt"; : > "$summary"

measure() { # <label> <native dir>
  local label=$1 dir=$2 fails=0 broken=0 i
  for i in $(seq 1 "$N"); do
    (cd "$dir" && ./gradlew :core:net:desktopTest $(for c in $CLASSES; do printf -- '--tests %s ' "$c"; done) --rerun \
      --stacktrace > "$out/$label-$i.log" 2>&1)
    local rc=$?
    local verdict
    verdict=$(python3 -I - "$dir/core/net/build/test-results/desktopTest" "$out" "$label" "$i" "$CLASSES" <<'PY'
import glob, os, shutil, sys
import xml.etree.ElementTree as ET
src, out, label, i, classes = sys.argv[1:6]
tests = fails = 0; names = []
for c in classes.split():
    f = os.path.join(src, f"TEST-{c}.xml")
    if not os.path.exists(f):
        print("missing"); sys.exit()
    shutil.copy(f, os.path.join(out, f"{label}-{i}-{c.rsplit('.', 1)[-1]}.xml"))
    r = ET.parse(f).getroot()
    tests += int(r.get("tests")); fails += int(r.get("failures")) + int(r.get("errors"))
    for tc in r.findall("testcase"):
        for e in tc:
            if e.tag in ("failure", "error"):
                names.append(tc.get("name") + " :: " + (e.get("type") or e.get("message") or "")[:120])
print(f"tests={tests} failed={fails}" + ("".join("\n    " + n for n in names)))
PY
)
    echo "$label run $i rc=$rc $verdict" | tee -a "$summary"
    case "$verdict" in
      missing*) broken=$((broken + 1)) ;;
      *"failed=0"*) [ "$rc" = 0 ] || broken=$((broken + 1)) ;;
      *) fails=$((fails + 1)) ;;
    esac
  done
  echo "TOTAL $label: $fails of $N runs failed; $broken runs without a test result" | tee -a "$summary"
}

measure "$(git rev-parse --short=10 HEAD)" "$PWD"
for ref in "$@"; do
  git -C .. fetch --no-tags --depth=1 origin "$ref" || { echo "TOTAL $ref: could not fetch" | tee -a "$summary"; continue; }
  wt="$RUNNER_TEMP/measure-$ref"
  git -C .. worktree add --detach "$wt" FETCH_HEAD >/dev/null
  # The vendored, never-committed libraries the setup action fetched for this checkout.
  [ -d third_party/maven ] && cp -r third_party/maven "$wt/native/third_party/" 2>/dev/null
  [ -f local.properties ] && cp local.properties "$wt/native/"
  measure "$(git -C "$wt" rev-parse --short=10 HEAD)" "$wt/native"
done
echo; cat "$summary" | grep '^TOTAL'
