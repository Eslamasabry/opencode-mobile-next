#!/usr/bin/env bash
# Commit rules check (STANDARDS.md G33; rules PROC-14 and the format step of PROC-5).
#
# Enforced in the Android quality checks job; also callable locally. The repository installs no git hook: a hook in this checkout's
# .git (or core.hooksPath) would run on every agent's commits in every
# worktree. Run the range mode by hand before asking for a merge; a person
# who wants it on each of their own commits can call the message mode from a
# commit-msg hook in a checkout they alone use.
#
# Range mode (before a merge; exit 1 on any violation):
#   tool/qa/check_commits.sh [<base> [<head>]]      # checks <base>..<head>
#       base defaults to $CHECK_COMMITS_BASE, else feat/phone-setup-v2; head to HEAD.
#   --no-format     skip the dart format check of the range's changed Dart files.
#
# Message mode (for a personal commit-msg hook; $1 is the message file):
#   tool/qa/check_commits.sh --message-file <file> [--merge] [--staged-format]
#
# Rules for every commit message:
#   - contains the literal text "[skip ci]";
#   - the subject (first line) is not empty, and a blank line separates it from
#     anything after it;
#   - a non-merge commit has a body: at least one line between the subject and
#     the trailer block that is not blank and not only "[skip ci]";
#   - a non-merge commit ends with a trailer block (its last paragraph, every
#     line "Key: value", or a bare "[skip ci]" line) that holds a "Co-Authored-By: Name <email>" trailer;
#     a "Claude-Session:" trailer, when present, is an https URL. Nothing
#     follows the trailer block.
# Format rule: every Dart file added or changed in merge-base(<base>,<head>)..<head>
# (or staged, in the hook) passes
#   dart format --output=none --set-exit-if-changed --language-version=3.10
# using the pinned Flutter's dart ($DART overrides).
#
# A human committing by hand may set OC_COMMIT_HUMAN=1 to skip only the
# trailer rule; "[skip ci]", the body and the format rules still apply.
set -uo pipefail

top="$(git rev-parse --show-toplevel)" || exit 2
cd "$top" || exit 2

PINNED_DART="$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart"
SKIP_CI='[skip ci]'

find_dart() {
  if [[ -n "${DART:-}" ]]; then
    echo "$DART"
  elif [[ -x "$PINNED_DART" ]]; then
    echo "$PINNED_DART"
  elif command -v dart >/dev/null 2>&1; then
    command -v dart
  fi
}

# check_message <label> <is_merge 0|1>  (message on stdin; prints problems;
# returns 1 when any rule fails).
check_message() {
  local label="$1" is_merge="$2"
  local human="${OC_COMMIT_HUMAN:-0}"
  awk -v label="$label" -v is_merge="$is_merge" -v human="$human" -v skip="$SKIP_CI" '
    { lines[NR] = $0 }
    END {
      n = NR
      # Drop trailing blank lines.
      while (n > 0 && lines[n] ~ /^[ \t]*$/) n--
      bad = 0
      if (n == 0) { print label ": empty message"; exit 1 }
      all = ""
      for (i = 1; i <= n; i++) all = all lines[i] "\n"
      if (index(all, skip) == 0) { print label ": missing \"" skip "\""; bad = 1 }
      if (lines[1] ~ /^[ \t]*$/) { print label ": empty subject line"; bad = 1 }
      if (n >= 2 && lines[2] !~ /^[ \t]*$/) {
        print label ": no blank line after the subject"; bad = 1
      }
      if (is_merge == 1) exit bad
      if (lines[1] !~ /^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([A-Za-z0-9_.\/-]+\))?!?: [^ ]/) {
        print label ": malformed subject (use type(scope): plain sentence)"; bad = 1
      }
      # Last paragraph = candidate trailer block.
      start = n
      while (start > 1 && lines[start - 1] !~ /^[ \t]*$/) start--
      trailers = 1
      if (start == 1) trailers = 0
      for (i = start; i <= n && trailers; i++) {
        if (lines[i] !~ /^[A-Za-z0-9][A-Za-z0-9-]*: [^ ]/ && lines[i] != skip) trailers = 0
      }
      body_end = trailers ? start - 1 : n
      body = 0
      for (i = 2; i <= body_end; i++) {
        l = lines[i]
        gsub(/[ \t]/, "", l)
        if (l != "" && l != "[skipci]") { body = 1; break }
      }
      if (!body) { print label ": no body (say what changed and why between the subject and the trailers)"; bad = 1 }
      if (human != 1) {
        if (!trailers) {
          print label ": does not end with a trailer block (Co-Authored-By: ..., Claude-Session: ...)"; bad = 1
        } else {
          co = 0
          for (i = start; i <= n; i++) {
            if (lines[i] ~ /^Co-Authored-By: [^<]*[^ <][^<]* <[^<>@ ]+@[^<> ]+>$/) co = 1
            if (lines[i] ~ /^Claude-Session: / && lines[i] !~ /^Claude-Session: https:\/\/[^ ]+$/) {
              print label ": Claude-Session trailer is not an https URL"; bad = 1
            }
          }
          if (!co) { print label ": trailer block has no \"Co-Authored-By: Name <email>\""; bad = 1 }
        }
      }
      exit bad
    }'
}

# check_format <dart files...>: reads each file's content with $CONTENT_CMD
# semantics set by the caller through the function show_file.
format_failures=0
check_format_files() {
  local mode="$1"
  shift
  local dart
  dart="$(find_dart)"
  if [[ -z "$dart" ]]; then
    echo "format: no dart found (set DART or install the pinned Flutter)" >&2
    return 2
  fi
  local f out rc bad=0
  if [[ "$mode" == "worktree" ]]; then
    out="$("$dart" format --output=none --set-exit-if-changed --language-version=3.10 "$@" 2>&1)"
    rc=$?
    if [[ $rc -ne 0 ]]; then
      echo "$out" | grep -E '^Changed ' | sed 's/^Changed /format: not formatted: /'
      echo "$out" | grep -vE '^(Changed |Formatted )' | sed 's/^/format: /'
      bad=1
    fi
  else
    for f in "$@"; do
      if [[ "$mode" == "index" ]]; then
        git show ":$f" >"$tmp_file" 2>/dev/null || continue
      else
        git show "$mode:$f" >"$tmp_file" 2>/dev/null || continue
      fi
      out="$("$dart" format --output=none --set-exit-if-changed --language-version=3.10 \
        --stdin-name="$f" <"$tmp_file" 2>&1)"
      rc=$?
      if [[ $rc -ne 0 ]]; then
        echo "format: not formatted: $f"
        echo "$out" | grep -vE '^(Changed |Formatted )' | sed 's/^/format: /'
        bad=1
      fi
    done
  fi
  return $bad
}

tmp_file="$(mktemp "${TMPDIR:-/tmp}/check_commits.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

fix_hint() {
  echo "fix: dart format --language-version=3.10 <files>; commit messages need \"$SKIP_CI\", a body and the session's trailers (STANDARDS.md PROC-14)." >&2
}

# ---------------------------------------------------------------- message mode
if [[ "${1:-}" == "--message-file" ]]; then
  msg_file="${2:-}"
  shift 2 || true
  is_merge=0 staged=0
  for a in "$@"; do
    case "$a" in
      --merge) is_merge=1 ;;
      --staged-format) staged=1 ;;
      *) echo "unknown option: $a" >&2; exit 64 ;;
    esac
  done
  [[ -f "$msg_file" ]] || { echo "no message file: $msg_file" >&2; exit 64; }
  # What git will store: cut at the scissors line, strip comment lines.
  cleaned="$(sed '/^# -\{24\} >8 -\{24\}$/,$d' "$msg_file" | git stripspace --strip-comments)"
  status=0
  printf '%s\n' "$cleaned" | check_message "commit message" "$is_merge" || status=1
  if [[ $staged -eq 1 ]]; then
    mapfile -t files < <(git diff --cached --name-only --diff-filter=ACMR -- '*.dart')
    if [[ ${#files[@]} -gt 0 ]]; then
      check_format_files index "${files[@]}"
      rc=$?
      if [[ $rc -eq 2 ]]; then
        echo "commit-msg: warning: dart not found, staged Dart files not format-checked" >&2
      elif [[ $rc -ne 0 ]]; then
        status=1
      fi
    fi
  fi
  if [[ $status -ne 0 ]]; then
    echo "G33: commit refused." >&2
    fix_hint
  fi
  exit $status
fi

# ------------------------------------------------------------------ range mode
do_format=1
positional=()
for a in "$@"; do
  case "$a" in
    --no-format) do_format=0 ;;
    -h | --help) sed -n '2,30p' "$0"; exit 0 ;;
    -*) echo "unknown option: $a" >&2; exit 64 ;;
    *) positional+=("$a") ;;
  esac
done
base="${positional[0]:-${CHECK_COMMITS_BASE:-feat/phone-setup-v2}}"
head="${positional[1]:-HEAD}"

base_sha="$(git rev-parse --verify --quiet "$base^{commit}")" || {
  echo "G33: unknown base: $base" >&2
  exit 64
}
head_sha="$(git rev-parse --verify --quiet "$head^{commit}")" || {
  echo "G33: unknown head: $head" >&2
  exit 64
}

status=0
count=0
while read -r sha parents; do
  [[ -z "$sha" ]] && continue
  count=$((count + 1))
  is_merge=0
  [[ $(wc -w <<<"$parents") -gt 1 ]] && is_merge=1
  subject="$(git log -1 --format=%s "$sha")"
  git log -1 --format=%B "$sha" | check_message "${sha:0:8} ${subject:0:60}" "$is_merge" || status=1
done < <(git rev-list --reverse --parents "$base_sha..$head_sha" | awk '{ s = $1; $1 = ""; print s, $0 }')

fmt_files=0
if [[ $do_format -eq 1 ]]; then
  merge_base="$(git merge-base "$base_sha" "$head_sha")"
  mapfile -t files < <(git diff --name-only --diff-filter=ACMR "$merge_base" "$head_sha" -- '*.dart')
  fmt_files=${#files[@]}
  if [[ $fmt_files -gt 0 ]]; then
    mode="$head_sha"
    # Fast path: the checkout is <head> and these files are unmodified.
    if [[ "$head_sha" == "$(git rev-parse HEAD)" ]] && git diff --quiet HEAD -- "${files[@]}"; then
      mode=worktree
    fi
    check_format_files "$mode" "${files[@]}"
    rc=$?
    [[ $rc -ne 0 ]] && status=1
  fi
fi

if [[ $status -eq 0 ]]; then
  echo "G33: OK: $count commit(s) in ${base}..${head}; $fmt_files changed Dart file(s) formatted$([[ $do_format -eq 0 ]] && echo ' (format check skipped)')."
else
  echo "G33: FAILED for ${base}..${head} ($count commit(s), $fmt_files changed Dart file(s))." >&2
  fix_hint
fi
exit $status
