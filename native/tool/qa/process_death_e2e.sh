#!/usr/bin/env bash
# Process-death restore gate (foundations audit gap 2): what comes back after Android kills the app in the background.
#
#   PC_HEAVY_EMULATOR=1 /home/eslam/Storage2/tools/pc-heavy.sh process-death -- \
#       native/tool/qa/process_death_e2e.sh --serial emulator-5584 [--package <id>] [--out <dir>]
#
# EMULATOR ONLY. The script refuses any serial that is not a running emulator (ro.kernel.qemu), never touches a phone,
# and always puts "Don't keep activities" back to 0 on exit, whatever happens.
#
# Why this is not the Maestro restart flow: `restart-app.yaml` force-stops the app, and Android drops the saved state of
# a force-stopped task, so it can never prove restoration. Here the task stays in Recents:
#   Part 1  "Don't keep activities" (always_finish_activities=1): Home destroys the activity and the system rebuilds it
#           from its saved state when the person comes back (the activity-recreate path).
#   Part 2  `am kill` while the app is in the background: the whole process dies, the task stays, and the next launch
#           builds a new process from the saved state (the real process-death path).
# Each part opens Settings -> the page with the Language row -> the Language sheet, leaves, comes back, and asserts:
# the sheet is open again, and Back (which pops the sheet, then the page) lands on the page and then on Settings,
# which proves the pages under the top one were restored, not only the top.
#
# Exit: 0 every assertion passed, 1 one failed, 2 could not run (not an emulator, no app, a text not found).
set -uo pipefail

SERIAL="" PKG="io.github.eslamasabry.opencode_mobile.native" OUT="${TMPDIR:-/tmp}/process-death-$$"
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --package) PKG="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    -h | --help) sed -n '2,23p' "$0"; exit 0 ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
done
[ -n "$SERIAL" ] || { echo "FAIL --serial is required (an emulator, e.g. emulator-5584)" >&2; exit 2; }
ADB=(adb -s "$SERIAL")
mkdir -p "$OUT"

[ "$("${ADB[@]}" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" = 1 ] ||
  { echo "FAIL $SERIAL is not an emulator: refusing (this gate changes a global setting)" >&2; exit 2; }
"${ADB[@]}" shell pm list packages "$PKG" | grep -q "package:$PKG\$" || { echo "FAIL $PKG is not installed on $SERIAL" >&2; exit 2; }

restore_setting() { "${ADB[@]}" shell settings put global always_finish_activities 0 >/dev/null 2>&1; }
trap restore_setting EXIT INT TERM

fails=0
say() { printf '%s\n' "$*"; }
pass() { say "PASS $*"; }
fail() { say "FAIL $*"; fails=$((fails + 1)); }

dump() { # $1 = name; writes $OUT/$1.xml and prints its path
  "${ADB[@]}" shell uiautomator dump /sdcard/pd.xml >/dev/null 2>&1
  "${ADB[@]}" pull /sdcard/pd.xml "$OUT/$1.xml" >/dev/null 2>&1
  printf '%s' "$OUT/$1.xml"
}
has_text() { grep -q "text=\"$2\"\|content-desc=\"$2\"" "$1"; }
center_of() { # prints "x y" of the first node whose text or content-desc is $2 in dump $1
  python3 -I - "$1" "$2" <<'PY'
import re, sys
xml, want = open(sys.argv[1], encoding="utf-8").read(), sys.argv[2]
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    if re.search(r'(?:text|content-desc)="%s"' % re.escape(want), node):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
}
tap_text() { # taps the node showing $1; exit 2 with the screen's texts when it is not there
  local d xy
  for _ in 1 2 3 4 5 6 7 8; do
    d="$(dump "tap-$(printf '%s' "$1" | tr -c 'A-Za-z0-9' _)")"
    if xy="$(center_of "$d" "$1")"; then
      # shellcheck disable=SC2086
      "${ADB[@]}" shell input tap $xy
      sleep 1
      return 0
    fi
    sleep 1
  done
  say "FAIL could not find '$1' on screen; the texts there:" >&2
  grep -o 'text="[^"]\+"' "$d" | sort -u | head -40 >&2
  exit 2
}

launch() { "${ADB[@]}" shell am start -n "$PKG/io.github.eslamasabry.opencode_mobile.MainActivity" >/dev/null 2>&1; sleep 3; }
settle() { sleep "${1:-3}"; }

# Language sheet content: the Arabic name only the open sheet shows (the row beside it shows the current language).
SHEET_MARK="العربية"

open_language_sheet() {
  tap_text "Settings"
  tap_text "Language"   # a group row, then the sheet's own row if the group is another page
  local d
  d="$(dump opened)"
  if ! has_text "$d" "$SHEET_MARK"; then tap_text "Language"; fi
  d="$(dump opened-sheet)"
  has_text "$d" "$SHEET_MARK" || { say "FAIL the Language sheet did not open (is the flow the same in this build?)"; exit 2; }
}

assert_back_stack() { # $1 = label; Back pops the sheet, then the page: Settings must be under it
  local d
  d="$(dump "after-$1")"
  has_text "$d" "$SHEET_MARK" && pass "$1: the Language sheet is open again" || fail "$1: the Language sheet did not come back"
  "${ADB[@]}" shell input keyevent KEYCODE_BACK; settle 2
  d="$(dump "back1-$1")"
  has_text "$d" "$SHEET_MARK" && fail "$1: Back left the sheet open" || pass "$1: Back closed the sheet"
  has_text "$d" "Language" && pass "$1: the page under the sheet came back" || fail "$1: the page under the sheet is gone"
  "${ADB[@]}" shell input keyevent KEYCODE_BACK; settle 2
  d="$(dump "back2-$1")"
  has_text "$d" "Settings" && pass "$1: Settings is under that page" || fail "$1: the stack under the page is gone (landed elsewhere)"
}

say "== $SERIAL $PKG, evidence in $OUT"
"${ADB[@]}" shell settings put global always_finish_activities 1
"${ADB[@]}" shell am force-stop "$PKG"; launch

say "-- Part 1: Don't keep activities (activity rebuilt from saved state)"
open_language_sheet
"${ADB[@]}" shell input keyevent KEYCODE_HOME; settle 3
"${ADB[@]}" shell dumpsys activity activities | grep -q "MainActivity" || true
launch
assert_back_stack "recreate"

say "-- Part 2: the process is killed while the app is in the background"
launch
open_language_sheet
"${ADB[@]}" shell input keyevent KEYCODE_HOME; settle 3
"${ADB[@]}" shell am kill "$PKG"; settle 2
if "${ADB[@]}" shell pidof "$PKG" >/dev/null 2>&1; then say "FAIL the process survived am kill; part 2 proves nothing"; exit 2; fi
launch
assert_back_stack "process-death"

if [ "$fails" -eq 0 ]; then say "PASS process-death restore"; exit 0; fi
say "$fails assertion(s) failed; screens in $OUT"; exit 1
