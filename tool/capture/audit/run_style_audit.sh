#!/usr/bin/env bash
# AUDIT TOOLING ONLY (branch audit/style-coherence, 2026-10-21).
# Renders the audit's screens for one Style combination through the existing
# capture harnesses (see audit_env.dart), into
#   $ROOT/raw/<combo>/<harness dir>/<file>.png
# then tool/capture/audit/sort_renders.py files them as
#   $ROOT/<screen>/<combo>.png
#
#   tool/capture/audit/run_style_audit.sh <combo> <set>
#     combo: <style>-<mode>[-saver|-hc|-rtl|-fine]   e.g. soft-dark, solid-dark-hc
#     set:   all | matrix | rtl
set -euo pipefail
cd "$(dirname "$0")/../../.."
FLUTTER=${FLUTTER:-$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter}
ROOT=${ROOT:-/home/eslam/Storage2/launch-footage/audit-styles}
combo=$1
set_=${2:-all}
IFS=- read -r style mode extra <<<"$combo"
export TZ=UTC AUDIT_STYLE=$style AUDIT_MODE=$mode AUDIT_OUT="$ROOT/raw/$combo"
case "${extra:-}" in
  saver) export AUDIT_SAVER=1 ;;
  hc) export AUDIT_HC=1 ;;
  rtl) export AUDIT_RTL=1 ;;
  fine) export AUDIT_FINE=sharp,flat,compact,none ;;
  '') ;;
  *) echo "unknown extra $extra" >&2; exit 2 ;;
esac
case "$set_" in settings|patch|appearance) ;; *) rm -rf "$AUDIT_OUT" ;; esac; mkdir -p "$ROOT/raw"

matrix_files=(tool/capture/surfaces_test.dart)
surf="home $style $mode|chat $style $mode|sheet $style $mode|settings $style $mode"
matrix_names="$surf"

all_files=(
  tool/capture/surfaces_test.dart
  tool/capture/vision_chat_test.dart
  tool/capture/vision_chat_approval_test.dart
  tool/capture/vision_inbox_test.dart
  tool/capture/vision_settings_hub_test.dart
  tool/capture/vision_server_switcher_test.dart
  tool/capture/vision_model_picker_test.dart
  tool/capture/vision_files_test.dart
  tool/capture/vision_diff_git_test.dart
  tool/capture/vision_terminal_test.dart
  tool/capture/vision_preview_test.dart
  tool/capture/vision_start_test.dart
  tool/capture/vision_welcome_test.dart
  tool/capture/vision_this_phone_test.dart
  tool/capture/vision_scheduled_test.dart
  tool/capture/vision_report_problem_test.dart
  tool/capture/vision_states_test.dart
  tool/capture/vision_team_task_test.dart
  tool/capture/stopgear_test.dart
  tool/capture/audit/style_audit_extras_test.dart
  tool/capture/audit/style_s1_extras_test.dart
  tool/capture/audit/style_s4_fields_test.dart
  tool/capture/audit/style_s6_transcript_test.dart
  tool/capture/census_test.dart
)
all_names="$surf"'|working chat dark|question-dark|command-dark|main-dark|reader-dark|model sheet oc2-costs-dark|changes screen dark|git sheet dark|main dark|four-agents-dark|welcome-dark|running-dark|empty-dark|offline-home-dark|working-dark|tips-page|audit menu|audit dialog|audit undo|audit confirm|s1 folder|s1 term|s1 tip|s4 suggestions|s6 transcript dark|j1-settings-more · appearance-settings · loaded'

case "$set_" in
  matrix) files=("${matrix_files[@]}"); names=$matrix_names ;;
  rtl) files=(tool/capture/surfaces_test.dart tool/capture/vision_inbox_test.dart); names="$surf|main-dark" ;;
  settings) files=(tool/capture/surfaces_test.dart); names="settings $style $mode" ;;
  appearance) files=(tool/capture/census_test.dart); names="j1-settings-more · appearance-settings · loaded" ;;
  patch) files=(tool/capture/surfaces_test.dart tool/capture/vision_start_test.dart tool/capture/vision_this_phone_test.dart); names="settings $style $mode|four-agents-dark|running-dark" ;;
  all) files=("${all_files[@]}"); names=$all_names ;;
  *) echo "unknown set $set_" >&2; exit 2 ;;
esac

echo "audit $combo ($set_): ${#files[@]} files"
"$FLUTTER" test -j 2 --no-pub \
  --dart-define=CENSUS_OUT="$ROOT/raw/census-unused" \
  --name "^($names)\$" "${files[@]}" >"$ROOT/raw/$combo-$set_.log" 2>&1 || true
tail -3 "$ROOT/raw/$combo-$set_.log"
find "$AUDIT_OUT" -name '*.png' | wc -l
