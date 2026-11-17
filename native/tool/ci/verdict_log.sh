#!/usr/bin/env bash
# verdict_log.sh [<reports dir>]: prints summary.py's inputs into the job log, one line per report file, so the verdict
# survives GitHub refusing artifact downloads (ci_verdict.sh reads these lines back). Compact JSON, marked OC-VERDICT.
#   OC-VERDICT <file.json> {...}
# A file over 200 KB is cut to its first 200 KB marked truncated by being invalid JSON on purpose (the reader skips it), so a
# log never balloons. Always exits 0.
dir=${1:-reports}
shopt -s nullglob
echo "::group::OC-VERDICT inputs (read by tool/ci/ci_verdict.sh when artifacts are refused)"
for f in "$dir"/*.json; do
  body=$(python3 -I -c "import json,sys; print(json.dumps(json.load(open(sys.argv[1])), separators=(',',':')))" "$f" 2>/dev/null) || continue
  [ "${#body}" -le 200000 ] && echo "OC-VERDICT $(basename "$f") $body"
done
echo "::endgroup::"
exit 0
