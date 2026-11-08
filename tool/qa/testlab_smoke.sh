#!/usr/bin/env bash
# Firebase Test Lab ARM smoke on the free Spark plan, fail closed (story E02-S92).
#
#   tool/qa/testlab_smoke.sh <firebase-project-id> <apk>
#
# Runs one Robo smoke of <apk> on 3 ARM (arm64-v8a) physical devices with
# `gcloud firebase test android run`. Before anything is uploaded it reads the
# project's billing state and refuses unless the project is on the free Spark
# plan: no billing account attached and billing not enabled. A project on
# Blaze (billing attached) could be charged, so the script never runs there.
# If the state cannot be read it refuses too.
#
# Output markers (last line), exit status:
#   TESTLAB: PASS                      0  all 3 devices passed
#   TESTLAB: FAIL ...                  1  a device failed or the run broke
#   REFUSED: ...                       2  precondition failed, nothing uploaded
#   NOT RUN: free quota used           0  Spark's daily physical-device quota is spent
#
# Environment:
#   GCLOUD                 gcloud binary (default: gcloud on PATH)
#   TESTLAB_DEVICES        exactly 3 entries "model=ID,version=API" separated by
#                          spaces; default: picked from the device catalog
#   TESTLAB_DAILY_LIMIT    physical-device tests per UTC day (default 5, Spark's limit)
#   TESTLAB_LEDGER_DIR     where this machine counts today's runs
#                          (default build/testlab); the server's quota is the authority
#   TESTLAB_DRY_RUN=1      print the run command instead of running it
set -uo pipefail

GCLOUD="${GCLOUD:-gcloud}"
DEVICE_COUNT=3
DAILY_LIMIT="${TESTLAB_DAILY_LIMIT:-5}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LEDGER_DIR="${TESTLAB_LEDGER_DIR:-$root/build/testlab}"

refuse() { echo "REFUSED: $*"; exit 2; }
not_run() { echo "NOT RUN: free quota used ($*)"; exit 0; }

[[ $# -eq 2 ]] || refuse "usage: tool/qa/testlab_smoke.sh <firebase-project-id> <apk>"
project="$1"
apk="$2"
[[ "$project" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] || refuse "not a valid project id: $project"
[[ -f "$apk" ]] || refuse "no APK at $apk"
command -v "$GCLOUD" >/dev/null 2>&1 || refuse "gcloud is not installed (see tool/qa/README.md)"
[[ "$DAILY_LIMIT" =~ ^[0-9]+$ ]] || refuse "TESTLAB_DAILY_LIMIT must be a number"

# 1. Signed in at all.
account="$("$GCLOUD" auth list --filter=status:ACTIVE --format='value(account)' 2>/dev/null)" \
  || refuse "gcloud auth list failed; run: gcloud auth login"
[[ -n "$account" ]] || refuse "no active gcloud account; run: gcloud auth login"

# 2. The plan: Spark means no billing account. Anything else, or no answer, refuses.
billing="$("$GCLOUD" billing projects describe "$project" \
  --format='value[separator="|"](projectId,billingEnabled,billingAccountName)' 2>/dev/null)" \
  || refuse "could not read billing for $project (enable the Cloud Billing API, see tool/qa/README.md); not running"
[[ "$billing" == *$'\n'* ]] && refuse "unexpected billing answer for $project; not running"
IFS='|' read -r billing_project billing_enabled billing_account <<<"$billing"
billing_enabled="$(printf '%s' "${billing_enabled:-}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
billing_account="$(printf '%s' "${billing_account:-}" | tr -d '[:space:]')"
# The API leaves billingEnabled out when it is false; the project id must echo back.
[[ "$billing_project" == "$project" ]] || refuse "could not confirm the billing state of $project; not running"
if [[ -n "$billing_account" ]]; then
  refuse "$project has a billing account attached (Blaze plan); this smoke runs only on the free Spark plan"
fi
if [[ "$billing_enabled" != "false" && -n "$billing_enabled" ]]; then
  refuse "$project reports billingEnabled=$billing_enabled; this smoke runs only on the free Spark plan"
fi
echo "plan: Spark (no billing account on $project)"

# 3. This machine's count for today (UTC). The server's quota error is the authority.
today="$(date -u +%Y-%m-%d)"
mkdir -p "$LEDGER_DIR" || refuse "cannot write $LEDGER_DIR"
ledger="$LEDGER_DIR/physical-$project-$today"
used="$(cat "$ledger" 2>/dev/null || echo 0)"
[[ "$used" =~ ^[0-9]+$ ]] || used=0
if (( used + DEVICE_COUNT > DAILY_LIMIT )); then
  not_run "$used of $DAILY_LIMIT physical-device tests already started today from this machine"
fi

# 4. Three ARM physical devices.
devices=()
if [[ -n "${TESTLAB_DEVICES:-}" ]]; then
  read -r -a devices <<<"$TESTLAB_DEVICES"
  for d in "${devices[@]}"; do
    [[ "$d" =~ ^model=[A-Za-z0-9_.-]+,version=[0-9]+$ ]] || refuse "bad TESTLAB_DEVICES entry: $d"
  done
else
  catalog="$("$GCLOUD" firebase test android models list --project "$project" \
    --filter='form=PHYSICAL AND supportedAbis:arm64-v8a' \
    --format='value(id,supportedVersionIds.list(separator=";"))' 2>/dev/null)" \
    || refuse "could not read the Test Lab device catalog for $project"
  # One line per model: "<max api> <model>", newest first, then the first 3 models.
  while read -r api model; do
    devices+=("model=$model,version=$api")
    (( ${#devices[@]} == DEVICE_COUNT )) && break
  done < <(printf '%s\n' "$catalog" | awk -F'\t' 'NF >= 2 && $1 ~ /^[A-Za-z0-9_.-]+$/ {
      n = split($2, v, ";"); best = 0
      for (i = 1; i <= n; i++) if (v[i] ~ /^[0-9]+$/ && v[i] + 0 > best) best = v[i] + 0
      if (best > 0) print best, $1
    }' | sort -k1,1nr -k2,2)
fi
(( ${#devices[@]} == DEVICE_COUNT )) || refuse "need exactly $DEVICE_COUNT ARM physical devices, have ${#devices[@]}"

cmd=("$GCLOUD" firebase test android run --project "$project" --type robo --app "$apk"
  --timeout 5m --no-record-video --no-performance-metrics --no-auto-google-login
  --results-history-name opencode-mobile-arm-smoke)
for d in "${devices[@]}"; do cmd+=(--device "$d"); done

if [[ "${TESTLAB_DRY_RUN:-0}" == "1" ]]; then
  printf 'DRY RUN:'; printf ' %q' "${cmd[@]}"; echo
  exit 0
fi

# Count before starting: a run that dies half-way may still have used quota.
echo $(( used + DEVICE_COUNT )) >"$ledger"
log="$(mktemp "${TMPDIR:-/tmp}/testlab-smoke.XXXXXX")"
trap 'rm -f "$log"' EXIT
"${cmd[@]}" >"$log" 2>&1
status=$?
# Print gcloud's own report (device outcomes and the results link); it carries no secrets.
cat "$log"

if [[ $status -ne 0 ]] && grep -qiE 'quota|RESOURCE_EXHAUSTED|\b429\b' "$log"; then
  not_run "Test Lab reported the daily quota is spent"
fi
case "$status" in
  0) echo "TESTLAB: PASS (${devices[*]})"; exit 0 ;;
  10) echo "TESTLAB: FAIL (a device failed the Robo smoke)" ;;
  15) echo "TESTLAB: FAIL (inconclusive)" ;;
  18) echo "TESTLAB: FAIL (a device is not compatible with the APK)" ;;
  *) echo "TESTLAB: FAIL (gcloud exited $status)" ;;
esac
exit 1
