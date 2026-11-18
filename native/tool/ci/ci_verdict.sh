#!/usr/bin/env bash
# ci_verdict.sh <run id>: the real verdict of a Native CI run. On work branches every job is continue-on-error, so the run
# and its jobs read "success" even when tests fail or a test source set does not compile: the truth is only in the reports.
# This downloads the `report-*` artifacts (by name, never the APK) and runs summary.py on them.
#
# When GitHub refuses the download (HTTP 503 "Egress is over the account limit") it does NOT guess:
#   * it reads the OC-VERDICT lines every report step prints into the job logs (tool/ci/verdict_log.sh) and says so plainly
#     ("verdict read from logs"); the verdict is then summary.py's, from the same inputs;
#   * when the logs cannot be read either, it says "verdict unreadable: download refused (egress limit)" and exits 4.
# Exit: 0 PASS, 1 FAIL, 2 integrity (summary.py), 3 the run is not finished, 4 unreadable (never a pass, never a fail).
# Environment: REPO, TREE (repo with summary.py; default: this checkout), OC_VERDICT_DIR (work folder), GH (the gh command;
# the self-test swaps it for a stand-in).
set -uo pipefail
RUN=${1:?usage: ci_verdict.sh <run id>}
REPO=${REPO:-Eslamasabry/opencode-mobile-next}
GH=${GH:-gh}
TREE=${TREE:-$(cd "$(dirname "$0")/../../.." && pwd)}
D=${OC_VERDICT_DIR:-${TMPDIR:-/tmp}}/round-$RUN
rm -rf "$D"; mkdir -p "$D/reports" "$D/base"
jq_py() { python3 -I -c "import json,sys; d=json.load(sys.stdin); $1"; }

st=$("$GH" run view "$RUN" -R "$REPO" --json status 2>/dev/null | jq_py "print(d.get('status',''))")
[ "$st" = completed ] || { echo "run $RUN is ${st:-unknown}"; exit 3; }

source_of=artifacts
err=$("$GH" run download "$RUN" -R "$REPO" -p "report-*" -D "$D/reports" 2>&1 >/dev/null); dl=$?
if [ "$dl" != 0 ]; then
  if printf '%s' "$err" | grep -qiE 'egress|HTTP 503|account limit'; then
    echo "NOTE: GitHub refused the artifact download (egress limit); trying the job logs."
    logs=$("$GH" run view "$RUN" -R "$REPO" --log 2>&1); lg=$?
    n=0
    if [ "$lg" = 0 ]; then
      n=$(printf '%s\n' "$logs" | python3 -I -c '
import re, sys, os
out, n = sys.argv[1], 0
for line in sys.stdin:
    m = re.search(r"OC-VERDICT (\S+\.json) (\{.*\})\s*$", line)
    if m:
        open(os.path.join(out, os.path.basename(m.group(1))), "w").write(m.group(2)); n += 1
print(n)' "$D/reports")
    fi
    if [ "$lg" != 0 ] || [ "${n:-0}" = 0 ]; then
      echo "verdict unreadable: download refused (egress limit)"
      echo "verdict exit 4 (0 pass, 1 fail, 2 integrity, 3 not finished, 4 unreadable: neither artifacts nor logs could be read)"
      exit 4
    fi
    source_of=logs
    echo "verdict read from logs ($n report file(s) taken from the OC-VERDICT lines of the job logs; no artifact was downloaded)"
  else
    echo "no report artifacts for $RUN"; exit 2
  fi
fi

jobs_json=$("$GH" run view "$RUN" -R "$REPO" --json jobs,displayTitle 2>/dev/null)
res() { printf '%s' "$jobs_json" | python3 -I -c "
import json,sys
d=json.load(sys.stdin); c=[j.get('conclusion') for j in d.get('jobs',[]) if (j.get('name') or '').startswith(sys.argv[1])]
print('failure' if 'failure' in c else ('skipped' if not c else (c[0] or 'skipped')))" "$1"; }
title=$(printf '%s' "$jobs_json" | jq_py "print(d.get('displayTitle',''))")
lane=$(python3 -I -c "import json,glob; r=[json.load(open(p)) for p in glob.glob('$D/reports/**/report-*.json',recursive=True)]; print(next((x.get('lane') for x in r if x.get('lane')),'fast'))" 2>/dev/null || echo fast)
RC=$(res compile) RT=$(res "desktop tests") RA=$(res apk) RR=$(res "record goldens") RCU=$(res "custom tasks") RD=$(res detekt)
# The lane as the workflow's own summary job names it: a custom-tasks or goldens run has no compile/test jobs by design
# (summary.py treats skipped compile/test jobs in a fast or full lane run as an integrity failure).
[ "$RCU" != skipped ] && lane="custom tasks"; [ "$RR" != skipped ] && lane="goldens"
[ "$RC" = skipped ] && [ "$RT" = skipped ] && [ "$RCU" = skipped ] && [ "$RR" = skipped ] && [ "$RA" != skipped ] && \
  printf '%s' "$title" | grep -qi "apk" && lane="apk only"
out=$(RESULT_COMPILE=$RC RESULT_TEST=$RT RESULT_APK=$RA RESULT_RECORD=$RR RESULT_CUSTOM=$RCU RESULT_DETEKT=$RD \
  python3 -I "$TREE/native/tool/ci/summary.py" "$D/reports" "$D/base" "$lane")
rc=$?; printf '%s\n' "$out"
# A guard that could not run (its reference picture was not fetched) is never a pass: always one line with the count.
printf '%s\n' "$out" | grep -q 'guards skipped (references not fetched)' || \
  python3 -I -c "import json,glob; print(sum(json.load(open(p)).get('guards_skipped',0) for p in glob.glob('$D/reports/**/report-*.json',recursive=True)), 'guards skipped (references not fetched)')"
[ "$source_of" = logs ] && echo "(verdict read from logs)"
echo "verdict exit $rc (0 pass, 1 fail, 2 integrity, 3 not finished, 4 unreadable)"; exit $rc
