# Shared helpers for the native check ladder (plan 02 §8 "Script contract").
# Sourced, never run. Every tN.sh / gate script uses these so the contract holds in one place:
#   - exit codes: 0 pass, 1 a test or gate failed, 2 the run could not happen (never reported as a pass);
#   - build/native-t<N>/<id>/summary.txt with commands, durations, failing tests and report paths;
#   - a done-file `exit-code` written last, then the summary's last 20 lines printed.
# shellcheck shell=bash

OC_TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OC_NATIVE="$(cd "$OC_TOOL_DIR/.." && pwd)"
export OC_TOOL_DIR OC_NATIVE

oc_die2() { echo "error: $*" >&2; exit 2; }
oc_usage() { echo "usage: $*" >&2; exit 2; }

# Repository top (needs git; the VPS copy has none, plan 02 §7.3).
oc_repo_root() { git -C "$OC_NATIVE" rev-parse --show-toplevel 2>/dev/null; }

# Resolve the integration base: the argument, else $OC_BASE, else native/main, else origin/native/main.
oc_resolve_base() {
  local want="${1:-${OC_BASE:-}}" c
  if [[ -n "$want" ]]; then
    git -C "$OC_NATIVE" rev-parse --verify --quiet "$want^{commit}" >/dev/null && { echo "$want"; return 0; }
    return 1
  fi
  for c in native/main origin/native/main; do
    git -C "$OC_NATIVE" rev-parse --verify --quiet "$c^{commit}" >/dev/null && { echo "$c"; return 0; }
  done
  return 1
}

# Files changed on this branch since merge-base(<base>, HEAD), plus staged, unstaged and untracked files.
# Paths are relative to the repository top. Deleted files are included (deleting is a change).
oc_changed_files() {
  local base="$1" mb top
  top="$(oc_repo_root)" || return 2
  mb="$(git -C "$top" merge-base "$base" HEAD)" || return 2
  {
    git -C "$top" diff --name-only "$mb" HEAD
    git -C "$top" diff --name-only --cached
    git -C "$top" diff --name-only
    git -C "$top" ls-files --others --exclude-standard
  } | sed '/^$/d' | sort -u
}

# Map repository-relative paths (stdin, "native/..." ) to Gradle module paths (stdout, unique).
# native/build-logic/** maps to "build-logic". Paths outside a module (docs, catalog, tool, Flutter) map to nothing.
oc_paths_to_modules() {
  awk '
    BEGIN { split("core protocol engine ui feature android tools", L, " "); for (i in L) layer[L[i]] = 1
            split("androidApp desktopApp quality", T, " "); for (i in T) top[T[i]] = 1 }
    {
      # Repository-relative paths only: the Flutter tree also has android/, so "native/" is required.
      if ($0 !~ /^native\//) next
      p = $0; sub(/^native\//, "", p)
      n = split(p, s, "/")
      if (s[1] == "build-logic") { print "build-logic"; next }
      if ((s[1] in top) && n >= 2) { print ":" s[1]; next }
      if ((s[1] in layer) && n >= 3) { print ":" s[1] ":" s[2] }
    }' | sort -u | while read -r m; do
      if [[ "$m" == build-logic ]]; then echo "$m"; continue; fi
      local d="$OC_NATIVE/${m#:}"; d="${d//://}"
      [[ -f "$d/build.gradle.kts" ]] && echo "$m"
    done
}

# Normalise ":ui:kit", "ui:kit", "ui/kit", "native/ui/kit" to ":ui:kit".
oc_norm_module() {
  local m="${1#native/}"
  m="${m%/}"; m="${m//\//:}"
  [[ "$m" == :* ]] || m=":$m"
  echo "$m"
}
oc_module_dir() { local m="${1#:}"; echo "$OC_NATIVE/${m//://}"; }

# Test tasks for one module, from its convention plugin and source folders (overrides: tool/ladder/tasks.tsv).
# Prints task names without the module prefix. $2 = "record" to re-record goldens instead of verifying them.
oc_module_test_tasks() {
  local m="$1" mode="${2:-}" dir bf over
  over="$(awk -F'\t' -v m="$m" '$1 == m { print $2 }' "$OC_TOOL_DIR/ladder/tasks.tsv" 2>/dev/null)"
  if [[ -n "$over" ]]; then echo "$over" | tr ' ' '\n'; return 0; fi
  dir="$(oc_module_dir "$m")"; bf="$dir/build.gradle.kts"
  [[ -f "$bf" ]] || return 1
  if grep -qE 'id\("oc\.kmp\.' "$bf"; then
    echo desktopTest
    if [[ -d "$dir/src/androidHostTest" ]]; then
      if grep -q 'libs.plugins.roborazzi' "$bf"; then
        [[ "$mode" == record ]] && echo recordRoborazziAndroidHostTest || echo verifyRoborazziAndroidHostTest
      else
        echo testAndroidHostTest
      fi
    fi
  elif grep -qE 'id\("oc\.jvm\.library"\)|id\("oc\.desktop\.application"\)|kotlin\("jvm"\)' "$bf"; then
    echo test
  elif grep -qE 'id\("oc\.android\.library"\)' "$bf"; then
    echo testDebugUnitTest
  else
    echo test
  fi
}

# --- run directory, summary and done-file --------------------------------------------------------------------------

# oc_run_init <tier-dir-name> <run-id>  e.g. oc_run_init native-t1 "$(git rev-parse --short HEAD)-$(date +%s)"
oc_run_init() {
  OC_RUN_DIR="$OC_NATIVE/build/$1/$2"
  mkdir -p "$OC_RUN_DIR" || exit 2
  OC_SUMMARY="$OC_RUN_DIR/summary.txt"
  : >"$OC_SUMMARY"
  rm -f "$OC_RUN_DIR/exit-code"
  touch "$OC_RUN_DIR/.start"
  OC_RUN_START=$(date +%s)
  OC_STATUS=0
  export OC_RUN_DIR OC_SUMMARY
  trap 'oc_finish $?' EXIT
  oc_say "run: $1 $2"
  oc_say "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)  host-role: ${OC_HOST_ROLE:-unset}  cwd: native/"
}

oc_say() { printf '%s\n' "$*" | tee -a "${OC_SUMMARY:-/dev/null}"; }

# Raise the run status: 1 beats 0, 2 beats both (a run that could not happen is never a pass).
oc_status() { local s="$1"; (( s > OC_STATUS )) && OC_STATUS=$s; return 0; }

oc_finish() {
  local rc="$1"
  trap - EXIT
  # An unexpected shell exit (set -e, a crash) is "could not happen", never a pass.
  if [[ "$rc" -ne 0 && "$OC_STATUS" -eq 0 ]]; then OC_STATUS=$(( rc == 1 ? 1 : 2 )); fi
  local word=PASS; ((OC_STATUS == 1)) && word=FAIL; ((OC_STATUS == 2)) && word="COULD NOT RUN"
  oc_say "result: $word (exit $OC_STATUS) in $(( $(date +%s) - OC_RUN_START )) s"
  oc_say "summary: ${OC_SUMMARY#"$OC_NATIVE/"}"
  echo "$OC_STATUS" >"$OC_RUN_DIR/exit-code"
  echo "----- last 20 lines of summary.txt -----"
  tail -n 20 "$OC_SUMMARY"
  exit "$OC_STATUS"
}

# oc_step <name> <cmd...>: runs the command, logs it to <name>.log, records command, duration and exit code.
# Returns the command's exit code.
oc_step() {
  local name="$1"; shift
  local log="$OC_RUN_DIR/$name.log" t0 rc
  oc_say "step $name: $(oc_quote "$@")"
  t0=$(date +%s)
  "$@" >"$log" 2>&1; rc=$?
  oc_say "step $name: exit $rc in $(( $(date +%s) - t0 )) s (log: ${log#"$OC_NATIVE/"})"
  return $rc
}

oc_quote() { local a out=""; for a in "$@"; do out+="$(printf '%q' "$a") "; done; echo "${out% }"; }

# Classify a failed Gradle log: 2 when the build could not happen (network, disk, lock, missing SDK/JDK), else 1.
oc_gradle_class() {
  local log="$1"
  if grep -qE "Could not (resolve|GET|HEAD|download)|could not resolve plugin artifact|Received status code 429|No space left on device|Timeout waiting to lock|SDK location not found|Unsupported class file major version|requires a JVM 21|Gradle could not start your build|Connection refused|UnknownHostException" "$log"; then
    echo 2
  else
    echo 1
  fi
}

# Tasks the log says do not exist (so they are reported as MISSING, never as passed).
oc_gradle_missing_tasks() {
  grep -oE "(Task '[^']+' not found in [^.]+|Cannot locate tasks that match '[^']+')" "$1" | sort -u
}

# Failing test cases from JUnit XML written during this run (newer than .start), as "Class > method".
oc_failing_tests() {
  local root="${1:-$OC_NATIVE}"
  command -v python3 >/dev/null || { echo "(python3 missing: failing tests not listed; read the logs)"; return 0; }
  find "$root" -path '*/build/test-results/*' -name 'TEST-*.xml' -newer "$OC_RUN_DIR/.start" 2>/dev/null |
    python3 -I -c '
import sys, xml.etree.ElementTree as ET
for path in sys.stdin.read().split():
    try: root = ET.parse(path).getroot()
    except Exception: continue
    for tc in root.iter("testcase"):
        if tc.find("failure") is not None or tc.find("error") is not None:
            print("%s > %s" % (tc.get("classname"), tc.get("name")))
'
}

# Test cases in JUnit XML written during this run.
oc_tests_count() {
  find "$OC_NATIVE" -path '*/build/test-results/*' -name 'TEST-*.xml' -newer "$OC_RUN_DIR/.start" -print0 2>/dev/null |
    xargs -0 -r grep -ho '<testsuite [^>]*tests="[0-9]*"' | grep -o 'tests="[0-9]*"' | tr -dc '0-9\n' |
    awk '{ s += $1 } END { print s + 0 }'
}

oc_report_paths() {
  find "$OC_NATIVE" -path '*/build/reports/tests/*' -name index.html -newer "$OC_RUN_DIR/.start" 2>/dev/null |
    sed "s|^$OC_NATIVE/||" | grep -E 'build/reports/tests/[^/]+/index.html$' | sort
}

# --- host checks (plan 02 §7.8) -----------------------------------------------------------------------------------

oc_check_memory() {
  local min="${OC_MIN_FREE_MB:-7168}" avail
  avail="$(free -m 2>/dev/null | awk '/^Mem:/ { print $7 }')"
  [[ -n "$avail" ]] || { oc_say "memory: free -m unavailable, not checked"; return 0; }
  oc_say "memory: ${avail} MB available (need >= $min)"
  ((avail >= min))
}

oc_check_disk() {
  local path="$1" min_gb="$2" avail
  avail="$(df -Pk "$path" 2>/dev/null | awk 'NR == 2 { print int($4 / 1048576) }')"
  [[ -n "$avail" ]] || { oc_say "disk: df failed for $path"; return 1; }
  oc_say "disk: ${avail} GB free at $path (need >= $min_gb)"
  ((avail >= min_gb))
}

oc_check_jdk() {
  local j="${JAVA_HOME:+$JAVA_HOME/bin/}java" v
  v="$("$j" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ { print $2 }')"
  [[ -n "$v" ]] || { oc_say "jdk: no java found (set JAVA_HOME to a JDK 21)"; return 1; }
  oc_say "jdk: $v (Gradle needs >= 21, native/AGENTS.md)"
  ((${v%%.*} >= 21))
}

# The one build slot on the PC (plan 02 §7.8: the same lock Flutter fixers use, never a separate native lock).
# Elsewhere (VPS, cloud) a local lock file. OC_LOCK_WAIT seconds (default 1800) before giving up with exit 2.
oc_build_lock_file() {
  local f="${OC_BUILD_LOCK_FILE:-/home/eslam/Storage/tmp/oc-build.lock}"
  if [[ -d "$(dirname "$f")" ]]; then echo "$f"; else echo "${TMPDIR:-/tmp}/oc-build.lock"; fi
}

# oc_with_lock <lockfile> <cmd...>: run under flock -w; returns 75 when the lock could not be taken.
oc_with_lock() {
  local f="$1"; shift
  mkdir -p "$(dirname "$f")" 2>/dev/null
  flock -w "${OC_LOCK_WAIT:-1800}" -E 75 "$f" "$@"
}


# Re-exec the calling script detached (plan 02 §8: T1–T4 run detached). Prints where to wait.
# Usage at the top of a script: oc_maybe_detach <tier-dir> "$@"
oc_maybe_detach() {
  local tier="$1"; shift
  local a want=0 rest=()
  for a in "$@"; do if [[ "$a" == --detach ]]; then want=1; else rest+=("$a"); fi; done
  ((want)) || return 0
  local id; id="detached-$(date +%Y%m%d-%H%M%S)-$$"
  mkdir -p "$OC_NATIVE/build/$tier"
  OC_RUN_ID="$id" setsid nohup "$0" "${rest[@]}" >"$OC_NATIVE/build/$tier/$id.out" 2>&1 </dev/null &
  echo "detached: pid $! ; wait for build/$tier/$id/exit-code (output: build/$tier/$id.out)"
  exit 0
}
