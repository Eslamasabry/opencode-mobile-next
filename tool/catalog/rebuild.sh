#!/usr/bin/env bash
# Rebuilds the app catalog when the code it shows has changed, and publishes it.
# Run by the systemd user timer oc-catalog-rebuild.timer (every 10 minutes);
# costs one `git rev-parse` when nothing changed. See widgetbook/README.md.
#
#   tool/catalog/rebuild.sh            # build if a ref moved
#   tool/catalog/rebuild.sh --force    # build even if nothing moved
#
# What is built: the app code at APP_REF (default preview/fb-usage) plus the
# catalog package from CATALOG_REF (default feat/widgetbook-poc), merged in a
# detached checkout of the dedicated worktree /home/eslam/Storage2/wt/oc_app-
# catalog. Once the catalog is merged into APP_REF the merge is a no-op.
# Only local refs are read (no fetch) and nothing is ever committed to them.
set -uo pipefail

APP_REF="${APP_REF:-preview/fb-usage}"
CATALOG_REF="${CATALOG_REF:-feat/widgetbook-poc}"
WT="${CATALOG_WORKTREE:-/home/eslam/Storage2/wt/oc_app-catalog}"
HOME_DIR="${CATALOG_HOME:-/home/eslam/Storage2/catalog}"
FLUTTER="${FLUTTER:-$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter}"
DART="$(dirname "$FLUTTER")/dart"
NODE="${NODE:-$(command -v node || echo /home/eslam/.nvm/versions/node/v24.11.1/bin/node)}"
SWEEP_PORT="${SWEEP_PORT:-8770}"
KEEP=3
LOCK_TOOL="$WT/tool/qa/machine_lock.sh"

mkdir -p "$HOME_DIR/releases"
exec 9>"$HOME_DIR/rebuild.lock"
flock -n 9 || { echo "another rebuild is running"; exit 0; }

app_sha="$(git -C "$WT" rev-parse "$APP_REF")" || exit 1
catalog_sha="$(git -C "$WT" rev-parse "$CATALOG_REF")" || exit 1
key="${app_sha:0:12}-${catalog_sha:0:8}"
if [[ "${1:-}" != "--force" && -f "$HOME_DIR/last_attempt" \
      && "$(cat "$HOME_DIR/last_attempt")" == "$key" ]]; then
  exit 0
fi
echo "$key" > "$HOME_DIR/last_attempt"
started="$(date +%s)"
stage="$HOME_DIR/releases/$key"
log="$HOME_DIR/last_build.log"
server_pid=""

fail() {  # fail <step>: keep serving the previous release, record why
  echo "FAILED at: $1" | tee -a "$log"
  python3 - "$HOME_DIR" "$key" "$1" "$app_sha" "$catalog_sha" <<'PY'
import json, os, sys, time
home, key, step, app, cat = sys.argv[1:6]
failure = {"key": key, "appSha": app, "catalogSha": cat, "step": step,
           "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "log": "last_build.log"}
json.dump(failure, open(os.path.join(home, "last_failure.json"), "w"), indent=1)
cur = os.path.join(home, "current", "status.json")
if os.path.exists(cur):
    status = json.load(open(cur))
    status["lastFailure"] = failure
    tmp = cur + ".tmp"
    json.dump(status, open(tmp, "w"), indent=1)
    os.replace(tmp, cur)
PY
  [[ -n "$server_pid" ]] && kill "$server_pid" 2>/dev/null
  rm -rf "$stage"
  exit 1
}

: > "$log"
exec > >(tee -a "$log") 2>&1
echo "== $(date -Is) building $key ($APP_REF=$app_sha, $CATALOG_REF=$catalog_sha)"

# 1. The code: app at APP_REF, catalog package merged in.
cd "$WT" || fail "worktree missing"
git reset -q --hard && git clean -qfd || fail "clean worktree"
git checkout -q --detach "$app_sha" || fail "checkout $app_sha"
if ! git merge -q --no-edit --no-ff "$catalog_sha" -m "catalog build merge" ; then
  git merge --abort 2>/dev/null
  fail "merge of $CATALOG_REF into $APP_REF conflicts"
fi

# 2. Web build (one build slot: never alongside an APK build).
cd "$WT/widgetbook" || fail "no widgetbook package"
nice -n 15 ionice -c3 "$LOCK_TOOL" build -- bash -c \
  "'$FLUTTER' pub get && '$FLUTTER' build web --release --no-wasm-dry-run --no-web-resources-cdn" \
  || fail "flutter build web"
mkdir -p "$stage" && cp -r build/web/. "$stage/" || fail "copy build"

# 2b. The screen standard on every use-case (VM widget test, no browser).
nice -n 15 "$LOCK_TOOL" test -- bash -c \
  "cd '$WT/widgetbook' && '$FLUTTER' test --concurrency=1 test/standard_check_test.dart"
standard_rc=$?
cp "$WT/widgetbook/standard_report.json" "$stage/standard_report.json" 2>/dev/null

# 3. Coverage ratchet, inventory, map, catalog list.
nice -n 15 "$DART" run tool/coverage_check.dart --json "$stage/coverage.json"
coverage_rc=$?
nice -n 15 "$DART" run tool/catalog_scan.dart "$stage/catalog.json" || fail "catalog scan"
nice -n 15 "$DART" run tool/app_map.dart --out "$stage" || fail "app map"

# 4. Screenshot sweep against the new build, on a private port.
python3 -m http.server "$SWEEP_PORT" --bind 127.0.0.1 --directory "$stage" \
  >/dev/null 2>&1 &
server_pid=$!
sleep 1
nice -n 10 "$LOCK_TOOL" test -- "$NODE" tool/sweep.mjs \
  --base "http://127.0.0.1:$SWEEP_PORT" --catalog "$stage/catalog.json" \
  --out "$stage/shots" --jobs 3 --timeout 120 \
  --standard "$stage/standard_report.json" || echo "sweep reported errors"
kill "$server_pid" 2>/dev/null
server_pid=""

# 5. Status, then switch `current` atomically.
python3 - "$stage" "$app_sha" "$catalog_sha" "$started" "$coverage_rc" "$standard_rc" <<'PY'
import json, os, sys, time
stage, app, cat, started, rc, std_rc = sys.argv[1:7]
def load(p, default):
    try:
        return json.load(open(os.path.join(stage, p)))
    except Exception:
        return default
coverage = load("coverage.json", {})
coverage["passed"] = rc == "0"
report = load("shots/report.json", None)
std = load("standard_report.json", None)
standard = ({"ran": False} if std is None else {
    "ran": True, "passed": std_rc == "0", "useCases": std.get("useCases"),
    "screensWithFindings": std.get("screensWithFindings"),
    "findingsPerRule": std.get("findingsPerRule")})
mp = load("map/map.json", {"counts": {}})
json.dump({
    "buildPassed": True,
    "appRef": os.environ.get("APP_REF", "preview/fb-usage"),
    "appSha": app,
    "catalogSha": cat,
    "builtAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    "buildSeconds": int(time.time()) - int(started),
    "coverage": coverage,
    "standard": standard,
    "map": mp.get("counts", {}),
    "sweep": ({"ran": False} if report is None else
              {"ran": True, "total": report.get("total", 0),
               "failed": report.get("failed", 0),
               "failures": report.get("failures", [])}),
}, open(os.path.join(stage, "status.json"), "w"), indent=1)
PY
ln -sfn "releases/$key" "$HOME_DIR/current.tmp" \
  && mv -T "$HOME_DIR/current.tmp" "$HOME_DIR/current" || fail "switch current"
echo "$key" > "$HOME_DIR/last_built"
rm -f "$HOME_DIR/last_failure.json"

# 6. Keep the newest KEEP releases; delete older ones by exact directory name.
cur_name="$(basename "$(readlink "$HOME_DIR/current")")"
i=0
while IFS= read -r name; do
  i=$((i + 1))
  [[ $i -le $KEEP || "$name" == "$cur_name" ]] && continue
  [[ "$name" =~ ^[0-9a-f]{12}-[0-9a-f]{8}$ ]] || continue
  rm -rf -- "$HOME_DIR/releases/$name"
done < <(ls -1t "$HOME_DIR/releases")
echo "== published $key in $(( $(date +%s) - started ))s"
