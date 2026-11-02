#!/usr/bin/env bash
# Write-set gate (plan 02 §7.5, plan 15 §3.2 step 7): a branch may only change paths inside its unit's write set.
#
#   native/tool/check_write_set.sh <brief.md> [--base <ref>] [--also <glob>]...
#   native/tool/check_write_set.sh --globs '<glob> <glob> ...' [--base <ref>] [--also <glob>]...
#
# Compares `git diff --name-only $(git merge-base <base> HEAD)` plus staged, unstaged and untracked files with the
# write set. The brief's write set is the text after "Write set:" on its line, up to "Single-owner" (plan 02 §7.13);
# globs are separated by spaces or commas, backticks are ignored. Glob syntax: `**` any depth, `*` and `?` inside one
# path segment, `{a,b}` alternatives; a glob ending in `/` means everything below it. Paths are repository-relative.
# --also adds a glob (e.g. the unit's report, docs/native/units/<UNIT>.md). Base: --base, $OC_BASE, native/main,
# origin/native/main.
# Exit: 0 every changed path is inside, 1 a path is outside (each one printed), 2 cannot check (no git, no base).
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"

brief="" globs_arg="" base_arg="" also=()
while (($#)); do
  case "$1" in
    --globs) globs_arg="${2:-}"; shift 2 ;;
    --base) base_arg="${2:-}"; shift 2 ;;
    --also) also+=("${2:-}"); shift 2 ;;
    -h | --help) sed -n '2,16p' "$0"; exit 0 ;;
    -*) oc_usage "check_write_set.sh <brief.md> | --globs '<globs>' [--base <ref>] [--also <glob>]" ;;
    *) brief="$1"; shift ;;
  esac
done

top="$(oc_repo_root)" || oc_die2 "no git repository (run this on the PC, not the VPS copy; plan 02 §7.3)"

if [[ -n "$brief" ]]; then
  [[ -f "$brief" ]] || oc_die2 "no brief: $brief"
  line="$(grep -m1 -E '^[*_ ]*Write set:' "$brief")" || oc_die2 "the brief has no 'Write set:' line: $brief"
  globs_arg="$(sed -E 's/^[*_ ]*Write set:[*_ ]*//; s/[[:space:]]*Single-owner.*$//' <<<"$line")"
fi
[[ -n "$globs_arg" ]] || oc_die2 "no write set (give a brief or --globs)"

# Split on spaces/commas outside {...}, drop backticks and parenthesised notes.
split_globs() {
  awk '{
    s = $0; gsub(/`/, "", s); gsub(/\([^)]*\)/, " ", s)
    depth = 0; tok = ""
    for (i = 1; i <= length(s); i++) {
      c = substr(s, i, 1)
      if (c == "{") depth++
      if (c == "}") depth--
      if (depth == 0 && (c == " " || c == "," || c == "\t" || c == ";")) { if (tok != "") print tok; tok = ""; continue }
      tok = tok c
    }
    if (tok != "") print tok
  }'
}

# Glob -> anchored ERE.
glob_to_regex() {
  local g="$1" out="" i c n depth=0
  [[ "$g" == */ ]] && g="${g}**"
  n=${#g}
  for ((i = 0; i < n; i++)); do
    c="${g:i:1}"
    case "$c" in
      '*')
        if [[ "${g:i+1:1}" == '*' ]]; then
          if [[ "${g:i+2:1}" == '/' ]]; then out+='(.*/)?'; i=$((i + 2)); else out+='.*'; i=$((i + 1)); fi
        else
          out+='[^/]*'
        fi ;;
      '?') out+='[^/]' ;;
      '{') out+='('; depth=$((depth + 1)) ;;
      '}') out+=')'; depth=$((depth - 1)) ;;
      ',') if ((depth > 0)); then out+='|'; else out+=','; fi ;;
      '.' | '+' | '(' | ')' | '|' | '^' | '$' | '[' | ']' | '\\') out+="\\$c" ;;
      *) out+="$c" ;;
    esac
  done
  echo "^${out}\$"
}

mapfile -t globs < <({ split_globs <<<"$globs_arg"; for a in "${also[@]}"; do echo "$a"; done; } | sed '/^$/d')
regexes=()
for g in "${globs[@]}"; do regexes+=("$(glob_to_regex "$g")"); done

base="$(oc_resolve_base "$base_arg")" || oc_die2 "unknown base '${base_arg:-${OC_BASE:-native/main}}' (pass --base <ref>)"
changed_text="$(cd "$top" && oc_changed_files "$base")" || oc_die2 "cannot enumerate changed paths"
changed=()
[[ -z "$changed_text" ]] || mapfile -t changed <<<"$changed_text"

echo "write set (${#globs[@]} globs): ${globs[*]}"
echo "base: $base (merge-base $(git -C "$top" merge-base "$base" HEAD | cut -c1-12)); changed paths: ${#changed[@]}"
outside=0
for p in "${changed[@]}"; do
  ok=0
  for r in "${regexes[@]}"; do
    if [[ "$p" =~ $r ]]; then ok=1; break; fi
  done
  if ((!ok)); then echo "OUTSIDE: $p"; outside=$((outside + 1)); fi
done
if ((outside)); then
  echo "write-set: FAILED, $outside path(s) outside the write set"
  exit 1
fi
echo "write-set: OK, every changed path is inside the write set"
exit 0
