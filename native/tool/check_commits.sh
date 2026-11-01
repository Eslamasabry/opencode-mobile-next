#!/usr/bin/env bash
# Native commit-message gate (plan 02 §9.1): the repo's G33 rule (tool/qa/check_commits.sh) without its Dart-format
# step, plus the native subject and trailer rules. The native format check is detekt/ktfmt on changed files.
#
# Range mode (before a push or a merge):
#   native/tool/check_commits.sh [<base> [<head>]]     base: $OC_BASE, native/main, origin/native/main; head: HEAD
# Message mode (for a personal commit-msg hook; the repo installs no hook):
#   native/tool/check_commits.sh --message-file <file> [--merge]
#
# Every commit message:
#   - contains "[skip ci]"; the subject is not empty and a blank line follows it;
#   - an optional "Change-Class:" is one of hotfix|fix|feature|breaking.
# A non-merge commit, also:
#   - subject `type(scope): plain words [skip ci]`, type in feat|fix|test|refactor|perf|docs|build|chore|merge, scope a
#     module name with ':' turned into '-' (`engine-delivery`), at most 72 characters before "[skip ci]";
#   - a body (a line between the subject and the trailer block that is not blank and not only "[skip ci]");
#   - the last paragraph is a trailer block ("Key: value" lines) holding "Co-Authored-By: Name <email>";
#     "Claude-Session:" is an https URL; "Behaviors:" lists catalog ids (B-AREA-NNN, comma separated).
# Range mode only (it needs the changed files): a commit that changes only tests or docs carries no Change-Class
# (plan 02 §9.1 content rules, memory change-class-values).
# OC_COMMIT_HUMAN=1 skips only the Co-Authored-By rule (a person committing by hand).
# Exit: 0 all pass, 1 a rule failed (each problem printed), 2 cannot check (bad refs, no git).
set -uo pipefail

SKIP_CI='[skip ci]'

check_message() {
  local label="$1" is_merge="$2"
  awk -v label="$label" -v is_merge="$is_merge" -v human="${OC_COMMIT_HUMAN:-0}" -v skip="$SKIP_CI" '
    { lines[NR] = $0 }
    END {
      n = NR
      while (n > 0 && lines[n] ~ /^[ \t]*$/) n--
      bad = 0
      if (n == 0) { print label ": empty message"; exit 1 }
      all = ""
      for (i = 1; i <= n; i++) all = all lines[i] "\n"
      if (index(all, skip) == 0) { print label ": missing \"" skip "\""; bad = 1 }
      if (lines[1] ~ /^[ \t]*$/) { print label ": empty subject line"; bad = 1 }
      if (n >= 2 && lines[2] !~ /^[ \t]*$/) { print label ": no blank line after the subject"; bad = 1 }
      for (i = 1; i <= n; i++) {
        if (tolower(lines[i]) ~ /^change-class:/) {
          v = tolower(lines[i]); sub(/^change-class:[ \t]*/, "", v); sub(/[ \t]+$/, "", v)
          if (v != "hotfix" && v != "fix" && v != "feature" && v != "breaking") {
            print label ": Change-Class must be hotfix, fix, feature or breaking (got \"" v "\")"; bad = 1
          }
        }
      }
      if (is_merge == 1) exit bad
      subj = lines[1]
      if (subj !~ /^(feat|fix|test|refactor|perf|docs|build|chore|merge)(\([a-z0-9][a-z0-9.-]*\))?: [^ ]/) {
        print label ": subject must be type(scope): plain words (types feat fix test refactor perf docs build chore merge; scope like engine-delivery)"; bad = 1
      }
      pre = subj; p = index(pre, skip); if (p > 0) pre = substr(pre, 1, p - 1); sub(/[ \t]+$/, "", pre)
      if (length(pre) > 72) { print label ": subject is " length(pre) " characters before [skip ci] (at most 72)"; bad = 1 }
      start = n
      while (start > 1 && lines[start - 1] !~ /^[ \t]*$/) start--
      trailers = (start > 1)
      for (i = start; i <= n && trailers; i++)
        if (lines[i] !~ /^[A-Za-z0-9][A-Za-z0-9-]*: [^ ]/ && lines[i] != skip) trailers = 0
      body_end = trailers ? start - 1 : n
      body = 0
      for (i = 2; i <= body_end; i++) { l = lines[i]; gsub(/[ \t]/, "", l); if (l != "" && l != "[skipci]") { body = 1; break } }
      if (!body) { print label ": no body (say what changed for the person and why)"; bad = 1 }
      if (!trailers) {
        if (human != 1) { print label ": does not end with a trailer block (Co-Authored-By: ..., Claude-Session: ...)"; bad = 1 }
        exit bad
      }
      co = 0
      for (i = start; i <= n; i++) {
        if (lines[i] ~ /^Co-Authored-By: [^<]*[^ <][^<]* <[^<>@ ]+@[^<> ]+>$/) co = 1
        if (lines[i] ~ /^Claude-Session: / && lines[i] !~ /^Claude-Session: https:\/\/[^ ]+$/) {
          print label ": Claude-Session trailer is not an https URL"; bad = 1
        }
        if (lines[i] ~ /^Behaviors: / && lines[i] !~ /^Behaviors: B-[A-Z0-9]+-[0-9]+(, ?B-[A-Z0-9]+-[0-9]+)*$/) {
          print label ": Behaviors trailer must list catalog ids like B-STATE-018, B-STATE-019"; bad = 1
        }
      }
      if (!co && human != 1) { print label ": trailer block has no \"Co-Authored-By: Name <email>\""; bad = 1 }
      exit bad
    }'
}

if [[ "${1:-}" == "--message-file" ]]; then
  f="${2:-}"; is_merge=0
  [[ "${3:-}" == "--merge" ]] && is_merge=1
  [[ -f "$f" ]] || { echo "no message file: $f" >&2; exit 2; }
  if sed '/^# -\{24\} >8 -\{24\}$/,$d' "$f" | git stripspace --strip-comments | check_message "commit message" "$is_merge"; then
    exit 0
  fi
  echo "native commit rules (plan 02 §9.1): commit refused." >&2
  exit 1
fi
case "${1:-}" in -h | --help) sed -n '2,25p' "$0"; exit 0 ;; esac

# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"
top="$(oc_repo_root)" || oc_die2 "no git repository"
base="$(oc_resolve_base "${1:-}")" || oc_die2 "unknown base '${1:-${OC_BASE:-native/main}}'"
head="${2:-HEAD}"
head_sha="$(git -C "$top" rev-parse --verify --quiet "$head^{commit}")" || oc_die2 "unknown head: $head"

status=0 count=0
while read -r sha parents; do
  [[ -z "$sha" ]] && continue
  count=$((count + 1))
  is_merge=0; [[ $(wc -w <<<"$parents") -gt 1 ]] && is_merge=1
  subject="$(git -C "$top" log -1 --format=%s "$sha")"
  label="${sha:0:8} ${subject:0:60}"
  git -C "$top" log -1 --format=%B "$sha" | check_message "$label" "$is_merge" || status=1
  if ((!is_merge)) && git -C "$top" log -1 --format=%B "$sha" | grep -qi '^Change-Class:'; then
    # Test-only or docs-only commits carry no Change-Class.
    files="$(git -C "$top" diff-tree --no-commit-id --name-only -r "$sha")"
    if [[ -n "$files" ]] &&
      ! grep -vqE '(^|/)src/[A-Za-z]*[Tt]est[A-Za-z]*/|(^|/)docs/|\.md$|(^|/)test/|_test\.(py|sh|dart)$' <<<"$files"; then
      echo "$label: a test-only or docs-only commit carries no Change-Class"; status=1
    fi
  fi
done < <(git -C "$top" rev-list --reverse --parents "$base..$head_sha" | awk '{ s = $1; $1 = ""; print s, $0 }')

if ((status == 0)); then
  echo "native commits: OK, $count commit(s) in $base..$head"
else
  echo "native commits: FAILED for $base..$head ($count commit(s)); rules: plan 02 §9.1" >&2
fi
exit $status
