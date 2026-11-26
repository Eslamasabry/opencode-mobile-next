#!/usr/bin/env bash
# Bookkeeping preflight: the cheap checks that most often fail a full run.
#
#   tool/qa/preflight.sh [<base>]     # base defaults to $CHECK_COMMITS_BASE, else master
#
# Run it before handing a branch back and before every merge. It takes about 1-2
# minutes, while a full split run takes about 13; the repo-scanning tests below
# caused most of the repeated full runs (docs/design/test-workflow-research-2026-10-15.md).
#
# Steps, all of them run even when one fails, then exit 1 if any failed:
#   0. the proposed change class (informational, never fails; docs/release/change-classes.md);
#   1. commit rules and dart format of the changed Dart files (G33);
#   2. generated localizations match the ARB files (gen-l10n leaves no diff);
#   3. flutter analyze;
#   4. the QA tool unit tests (Python);
#   5. the repo-scanning Flutter tests (ledgers, ratchets, manifests, glossary).
# It changes nothing in the worktree except regenerating lib/l10n output, and
# says so when that output was stale.
set -uo pipefail
root="$(git rev-parse --show-toplevel)"
cd "$root"
base="${1:-${CHECK_COMMITS_BASE:-master}}"
failed=()

step() { printf '\n==> %s\n' "$1"; }

step "Commit rules and format ($base..HEAD)"
tool/qa/check_commits.sh "$base" HEAD || failed+=("commit rules")

step "Proposed change class ($base..HEAD, informational)"
python3 tool/qa/classify_change.py "$base" HEAD 2>&1 | sed -n '1,/^required gates:/p' | sed '$d' || true

step "Generated localizations"
flutter gen-l10n >/dev/null || failed+=("gen-l10n")
if ! git diff --quiet -- lib/l10n; then
  git diff --stat -- lib/l10n
  echo "lib/l10n output was stale and has been regenerated; commit it."
  failed+=("l10n output")
fi

step "Analyze"
# A fresh worktree has no resolved Widgetbook catalog; analyze reads it too.
if [ ! -f widgetbook/.dart_tool/package_config.json ]; then
  (cd widgetbook && { flutter pub get --enforce-lockfile >/dev/null 2>&1 || flutter pub get >/dev/null 2>&1; })
  git checkout -q -- widgetbook/pubspec.lock 2>/dev/null || true # resolving must not edit the lock
fi
# Merges have broken compilation in files no repo-scanning test loads
# (generated localizations, vendored packages); the analyzer sees them all.
tool/qa/machine_lock.sh analyze flutter analyze --no-pub 2>&1 | tail -3
[ "${PIPESTATUS[0]}" -eq 0 ] || failed+=("analyze")

step "QA tool tests"
python3 -m unittest discover -s tool/qa -p 'test_*.py' 2>&1 | grep -E '^(ERROR|FAIL):|^Ran |^OK|^FAILED'
[ "${PIPESTATUS[0]}" -eq 0 ] || failed+=("QA tool tests")

step "Repo-scanning tests"
tests=(
  test/ui_glossary_test.dart
  test/ui_ledger_coverage_test.dart
  test/file_size_ratchet_test.dart
  test/coverage/settings_preferences_coverage_test.dart
  test/kit/kit_manifest_test.dart
  test/kit/kit_draft_manifest_test.dart
  test/kit_ratchet_test.dart
  test/kit_map_gate_test.dart
  test/golden_harness_test.dart
  test/architecture_boundaries_test.dart
  test/analyzer_suppressions_test.dart
  test/l10n_coverage_test.dart
  test/design_standard_test.dart
  test/team_design_standard_test.dart
  test/release_metadata_test.dart
  test/voice_model_manifest_test.dart
)
tool/qa/machine_lock.sh test flutter test --no-pub --concurrency="${OC_PREFLIGHT_JOBS:-4}" \
  "${tests[@]}" 2>&1 | grep -E '^[0-9:]+ \+[0-9]+.*(All tests passed|Some tests failed)|\[E\]$' | tail -20
[ "${PIPESTATUS[0]}" -eq 0 ] || failed+=("repo-scanning tests")

echo
if [ "${#failed[@]}" -gt 0 ]; then
  printf 'preflight: FAILED: %s\n' "$(IFS=,; echo "${failed[*]}" | sed 's/,/, /g')"
  exit 1
fi
echo "preflight: OK"
