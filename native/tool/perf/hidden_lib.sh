# Shared steps of the hidden runs PR-01 (hidden_run.sh) and PR-04 (offline_run.sh), plan 13 §3.5 and §3.7.
# Sourced after tool/lib/common.sh and oc_run_init. Owner: S2-BACKOFF.
#
# Contract kept from §3.7: the emulator lock is held for the whole run; restore_device.sh runs first and again at exit
# (a second layer only: run these scripts with --detach, rule 7); test servers are stopped by their exact PID, never by
# pattern (AGENTS.md); nothing here prints a password or a URL with a query.
# shellcheck shell=bash

PERF_DIR="$OC_TOOL_DIR/perf"
HR_ADB="${ADB:-${ANDROID_HOME:+$ANDROID_HOME/platform-tools/}adb}"
HR_PKG="${OC_BENCH_PKG:-io.github.eslamasabry.opencode_mobile.native.bench}"
# WP-13.0 adds the receiver; host id and port only (plan 13 §3.2 rule 3).
HR_SEED_RECEIVER="${OC_BENCH_SEED_RECEIVER:-$HR_PKG/io.github.eslamasabry.oc.app.bench.BenchSeedReceiver}"
HR_PROXY_PID=""

hr_now_ms() { echo $(( $(date +%s%N) / 1000000 )); }

# hr_mark <name>: one line in marks.jsonl on the proxy's clock (host wall clock, ms).
hr_mark() { printf '{"t": %s, "mark": "%s"}\n' "$(hr_now_ms)" "$1" >>"$OC_RUN_DIR/marks.jsonl"; oc_say "mark: $1"; }

hr_adb() { "$HR_ADB" -s "$HR_SERIAL" "$@"; }

# Fallback restore for the state these two scripts change, used only until WP-13.0's restore_device.sh exists.
hr_restore_minimal() {
  hr_adb shell cmd connectivity airplane-mode disable &&
    hr_adb shell dumpsys deviceidle unforce >/dev/null &&
    hr_adb shell input keyevent KEYCODE_WAKEUP
}

hr_restore() {
  if [[ -x "$PERF_DIR/restore_device.sh" ]]; then
    "$PERF_DIR/restore_device.sh" --serial "$HR_SERIAL"
  else
    hr_restore_minimal
  fi
}

hr_stop_proxy() {
  [[ -n "$HR_PROXY_PID" ]] || return 0
  kill "$HR_PROXY_PID" 2>/dev/null
  HR_PROXY_PID=""
}

hr_cleanup() {
  local rc=$?
  hr_stop_proxy
  [[ -n "${HR_SERIAL:-}" ]] && hr_restore >/dev/null 2>&1
  oc_finish "$rc"
}

# hr_prepare <serial>: emulator only, lock, device online, restore first. Exits 2 when the run cannot happen.
hr_prepare() {
  HR_SERIAL="$1"
  [[ "$HR_SERIAL" =~ ^emulator-[0-9]+$ ]] || { oc_say "serial $HR_SERIAL is not an emulator"; oc_status 2; exit 2; }
  local lock="${OC_EMULATOR_LOCK:-${TMPDIR:-/tmp}/oc-emulator.lock}"
  exec {HR_LOCK_FD}>"$lock"
  flock -w "${OC_LOCK_WAIT:-1800}" "$HR_LOCK_FD" || { oc_say "lock: emulator lock busy"; oc_status 2; exit 2; }
  [[ "$(hr_adb get-state 2>/dev/null)" == device ]] || { oc_say "device $HR_SERIAL not online"; oc_status 2; exit 2; }
  trap hr_cleanup EXIT
  oc_step restore hr_restore || { oc_say "restore failed"; oc_status 2; exit 2; }
}

# hr_start_proxy <listen-port> <upstream-port>: the counting proxy (conn_log_proxy.py), remembered by exact PID.
hr_start_proxy() {
  local listen="$1" upstream="$2"
  python3 "$PERF_DIR/conn_log_proxy.py" --listen-port "$listen" --upstream-port "$upstream" \
    --log "$OC_RUN_DIR/proxy.jsonl" --down-flag "$OC_RUN_DIR/server-down" >"$OC_RUN_DIR/proxy.log" 2>&1 &
  HR_PROXY_PID=$!
  sleep 1
  kill -0 "$HR_PROXY_PID" 2>/dev/null || { oc_say "proxy did not start (see proxy.log)"; oc_status 2; exit 2; }
  oc_say "proxy: pid $HR_PROXY_PID, :$listen -> :$upstream"
}

# hr_seed_and_open <port> [extra am args...]: seed the bench profile through the proxy port, open the app.
hr_seed_and_open() {
  local port="$1"; shift
  # `am broadcast` reports success even for a missing component, so look for the receiver first.
  hr_adb shell dumpsys package "$HR_PKG" | grep -qF "${HR_SEED_RECEIVER##*/}" ||
    { oc_say "seed: no ${HR_SEED_RECEIVER##*/} in $HR_PKG (WP-13.0 adds it)"; oc_status 2; exit 2; }
  hr_adb shell am broadcast -n "$HR_SEED_RECEIVER" --es hostId emulator --ei port "$port" "$@" >"$OC_RUN_DIR/seed.log" 2>&1 ||
    { oc_say "seed: broadcast failed; see seed.log"; oc_status 2; exit 2; }
  hr_adb shell monkey -p "$HR_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 ||
    { oc_say "open: $HR_PKG not installed"; oc_status 2; exit 2; }
}

# hr_wait_stream <seconds>: wait until an event stream request reaches the proxy (the app is connected).
hr_wait_stream() {
  local deadline=$(( $(date +%s) + $1 ))
  while (( $(date +%s) < deadline )); do
    grep -q '"path": "/api/event' "$OC_RUN_DIR/proxy.jsonl" 2>/dev/null && return 0
    sleep 1
  done
  oc_say "connect: no event stream within $1 s"; oc_status 2; exit 2
}

hr_app_pid() { hr_adb shell pidof "$HR_PKG" | tr -d '\r' | awk '{print $1}'; }

# utime + stime of the app process in clock ticks (/proc/<pid>/stat fields 14 and 15).
hr_cpu_ticks() {
  local pid; pid="$(hr_app_pid)"
  [[ -n "$pid" ]] || { echo -1; return; }
  hr_adb shell cat "/proc/$pid/stat" | tr -d '\r' | sed 's/^.*) //' | awk '{print $12 + $13}'
}

# Fired wake-up alarms of the package so far, from `dumpsys alarm` ("…:<pkg> +…ms running, N wakeups, …").
hr_wakeups() {
  hr_adb shell dumpsys alarm | tr -d '\r' | grep -F ":$HR_PKG " | grep -o '[0-9]* wakeups' | head -1 |
    awk '{print $1 + 0} END {if (NR == 0) print 0}'
}

hr_sleep_min() { oc_say "waiting $1 min"; sleep $(( $1 * 60 )); }
