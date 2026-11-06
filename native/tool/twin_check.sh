#!/usr/bin/env bash
# Twin drift check (plan 02 §5.6): the ONE drift mechanism for files copied from the Flutter tree into native/.
#
#   native/tool/twin_check.sh [--kind kotlin|res|arb|pins|other|all] [--flutter-repo <dir>] [--ref <rev>]
#                             [--twins <TWINS.md>] [--verify-copy] [--list-only]
#   native/tool/twin_check.sh --fixes [--since <rev>] [--flutter-repo <dir>] [--ref <rev>] [--twins <TWINS.md>]
#
# For every row of native/TWINS.md (| Native path | Flutter source path | Source commit at copy | Last synced Flutter
# commit | Native owner |) it checks that:
#   1. both SHAs name commits in the Flutter repository, and "last synced" is not older than "at copy";
#   2. the Flutter source path exists at the last synced commit, and the native path exists in this tree;
#   3. no Flutter commit in <last synced>..<ref> touched the source path (each one is listed: the native owner
#      mirrors it within the week and updates the "last synced" column);
#   4. with --verify-copy, Kotlin/Java twins are byte-equal to the source at the last synced commit
#      (resource twins are subsets by design and are not compared).
# Two Flutter lines feed native: the 2.2.0 freeze snapshot (a commit whose parent is the release line's fork point)
# and the release line (master, 2.1.x). A row synced on one line and checked against the other is fine while the two
# share history: <last synced>..<ref> is then exactly the ref line's commits the copy has not seen. Only a commit with
# no shared history (an orphan snapshot) is invalid.
#
# --fixes is the fix ledger (E20-S06): every non-merge Flutter commit in <since>..<ref> needs a row in TWINS.md's
# "Flutter fix ledger" table (| Flutter commit | Subject | Native twin or not-applicable reason |) with a non-empty
# third column. A row matches by SHA, or by identical subject when the commit was rebased since (its SHA changed).
# <since>: --since, $OC_FLUTTER_FREEZE, else the `<!-- fix-ledger since=<rev> -->` marker in TWINS.md.
# Flutter repository: --flutter-repo, $OC_FLUTTER_REPO, else this repository (the Flutter tree sits beside native/).
# Ref: --ref, $OC_FLUTTER_REF, else origin/master (the released app, the source of truth) when it exists, else HEAD
# of that repository (the coordinator passes int/freeze-2026-11-01 to check the freeze line).
# --kind kotlin is what tool/native/kotlin-drift.sh wraps (plan 03 S9). --list-only reports drift but exits 0 for it.
# Exit: 0 no drift and every row valid, 1 drift or an invalid row (--fixes: a commit without a row), 2 cannot check
# (no repo, shallow history).
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"

kind=all repo="${OC_FLUTTER_REPO:-}" ref="${OC_FLUTTER_REF:-}" twins="$OC_NATIVE/TWINS.md" verify=0 list_only=0
fixes=0 since="${OC_FLUTTER_FREEZE:-}"
while (($#)); do
  case "$1" in
    --kind) kind="${2:-}"; shift 2 ;;
    --flutter-repo) repo="${2:-}"; shift 2 ;;
    --ref) ref="${2:-}"; shift 2 ;;
    --twins) twins="${2:-}"; shift 2 ;;
    --verify-copy) verify=1; shift ;;
    --list-only) list_only=1; shift ;;
    --fixes) fixes=1; shift ;;
    --since) since="${2:-}"; shift 2 ;;
    -h | --help) sed -n '2,33p' "$0"; exit 0 ;;
    *) oc_usage "twin_check.sh [--kind K] [--flutter-repo DIR] [--ref REV] [--twins FILE] [--verify-copy] [--list-only] | --fixes [--since REV]" ;;
  esac
done
case "$kind" in kotlin | res | arb | pins | other | all) ;; *) oc_die2 "unknown kind: $kind" ;; esac

top="$(oc_repo_root)" || oc_die2 "no git repository for the native tree"
repo="${repo:-$top}"
git -C "$repo" rev-parse --git-dir >/dev/null 2>&1 || oc_die2 "not a git repository: $repo"
if [[ -z "$ref" ]]; then
  ref=HEAD
  git -C "$repo" rev-parse --verify --quiet "origin/master^{commit}" >/dev/null && ref=origin/master
fi
ref_sha="$(git -C "$repo" rev-parse --verify --quiet "$ref^{commit}")" || oc_die2 "unknown Flutter ref: $ref"
[[ -f "$twins" ]] || oc_die2 "no twins file: $twins"
shallow="$(git -C "$repo" rev-parse --is-shallow-repository 2>/dev/null)"
trim() { sed 's/^[[:space:]]*//; s/[[:space:]]*$//' <<<"$1"; }

# ------------------------------------------------------------------------------------------------ --fixes (ledger)
if ((fixes)); then
  if [[ -z "$since" ]]; then
    since="$(sed -n 's/.*<!-- *fix-ledger since=\([0-9A-Za-z._/-]*\) *-->.*/\1/p' "$twins" | head -n1)"
  fi
  [[ -n "$since" ]] || oc_die2 "no fix-ledger start: pass --since, set OC_FLUTTER_FREEZE or add the marker to $twins"
  since_sha="$(git -C "$repo" rev-parse --verify --quiet "$since^{commit}")" || {
    [[ "$shallow" == true ]] && oc_die2 "fix-ledger start $since not in this shallow clone (git fetch --unshallow)"
    echo "INVALID fix-ledger start '$since' is not a commit in the Flutter repository"; exit 1
  }
  git -C "$repo" merge-base --is-ancestor "$since_sha" "$ref_sha" || {
    echo "INVALID fix-ledger start $since is not on the history of $ref"; exit 1
  }
  declare -A ledger_sha ledger_subject
  ledger_rows=0 ledger_bad=0
  while IFS='|' read -r _ lsha lsubject ltwin _; do
    lsha="$(trim "$lsha")"
    [[ "$lsha" =~ ^[0-9a-f]{7,40}$ ]] || continue
    lsubject="$(trim "$lsubject")"; ltwin="$(trim "$ltwin")"
    ledger_rows=$((ledger_rows + 1))
    if [[ -z "$ltwin" ]]; then
      echo "INVALID fix-ledger row $lsha has no native twin and no not-applicable reason"; ledger_bad=$((ledger_bad + 1))
      continue
    fi
    full="$(git -C "$repo" rev-parse --verify --quiet "$lsha^{commit}" 2>/dev/null)" && ledger_sha[$full]=1
    [[ -n "$lsubject" ]] && ledger_subject[$lsubject]=1
  done <"$twins"
  total=0 missing=0
  while IFS=$'\t' read -r sha short date subject; do
    [[ -n "$sha" ]] || continue
    total=$((total + 1))
    [[ -n "${ledger_sha[$sha]:-}" || -n "${ledger_subject[$subject]:-}" ]] && continue
    missing=$((missing + 1))
    echo "MISSING $short $date $subject"
  done < <(git -C "$repo" log --no-merges --reverse --format=$'%H\t%h\t%ad\t%s' --date=short "$since_sha..$ref_sha")
  echo "twin_check --fixes: $total Flutter commit(s) in ${since}..${ref} (${ref_sha:0:12}), $ledger_rows ledger row(s)"
  echo "twin_check --fixes: $missing commit(s) without a TWINS.md row, $ledger_bad invalid row(s)"
  ((missing || ledger_bad)) && exit 1
  exit 0
fi

kind_of() {
  case "$1" in
    *.kt | *.java | *.kts) echo kotlin ;;
    */res/*) echo res ;;
    *.arb) echo arb ;;
    */assets/agents/* | *package-lock.json) echo pins ;;
    *) echo other ;;
  esac
}

rows=0 checked=0 drift_rows=0 bad=0 cannot=0
declare -A seen_commit
while IFS='|' read -r _ npath fpath at_copy synced owner _; do
  npath="$(trim "$npath")"
  [[ "$npath" == native/* ]] || continue # Other tables (the fix ledger) hold free text: never parse it here.
  fpath="$(trim "$fpath")"; at_copy="$(trim "$at_copy")"; synced="$(trim "$synced")"; owner="$(trim "$owner")"
  rows=$((rows + 1))
  k="$(kind_of "$npath")"
  [[ "$kind" == all || "$kind" == "$k" ]] || continue
  checked=$((checked + 1))
  for sha in "$at_copy" "$synced"; do
    if ! git -C "$repo" cat-file -e "$sha^{commit}" 2>/dev/null; then
      if [[ "$shallow" == true ]]; then
        echo "CANNOT  $npath: commit $sha not in this shallow clone (git fetch --unshallow)"; cannot=$((cannot + 1))
      else
        echo "INVALID $npath: '$sha' is not a commit in the Flutter repository"; bad=$((bad + 1))
      fi
      continue 2
    fi
  done
  # Not older: the copy commit is on the synced commit's history, or the two sit on sibling lines (the freeze
  # snapshot and the release line) that share a fork point. Older or unrelated is invalid.
  if ! git -C "$repo" merge-base --is-ancestor "$at_copy" "$synced" 2>/dev/null; then
    if git -C "$repo" merge-base --is-ancestor "$synced" "$at_copy" 2>/dev/null ||
      ! git -C "$repo" merge-base "$at_copy" "$synced" >/dev/null 2>&1; then
      echo "INVALID $npath: last synced $synced is older than (or unrelated to) the copy commit $at_copy"; bad=$((bad + 1))
    fi
  fi
  if ! git -C "$repo" cat-file -e "$synced:$fpath" 2>/dev/null; then
    echo "INVALID $npath: source $fpath does not exist at $synced"; bad=$((bad + 1)); continue
  fi
  if [[ ! -e "$top/$npath" ]]; then
    echo "INVALID $npath: the native copy is missing"; bad=$((bad + 1))
  elif ((verify)) && [[ "$k" == kotlin ]]; then
    if ! git -C "$repo" show "$synced:$fpath" | cmp -s - "$top/$npath"; then
      echo "DIFFERS $npath: not byte-equal to $fpath at $synced"; bad=$((bad + 1))
    fi
  fi
  # A drift range is only meaningful when the last synced commit shares history with the ref: on the ref's own
  # history, or on a sibling line forked from it (the freeze snapshot's parent is the release line's fork point, so
  # <synced>..<ref> is exactly the release commits the copy has not seen). A shallow clone or an orphan snapshot
  # history (plan 15 §11.0) breaks that; then `git log A..B` would list unrelated commits.
  if ! git -C "$repo" merge-base "$synced" "$ref_sha" >/dev/null 2>&1; then
    if [[ "$shallow" == true ]]; then
      echo "CANNOT  $npath: $synced is not an ancestor of $ref in this shallow clone (git fetch --unshallow)"
      cannot=$((cannot + 1))
    else
      echo "INVALID $npath: last synced $synced is not on the history of $ref"; bad=$((bad + 1))
    fi
    continue
  fi
  key="$synced $fpath"
  if [[ -z "${seen_commit[$key]:-}" ]]; then
    seen_commit[$key]="$(git -C "$repo" log --format='%h %ad %s' --date=short "$synced..$ref_sha" -- "$fpath")"
  fi
  if [[ -n "${seen_commit[$key]}" ]]; then
    drift_rows=$((drift_rows + 1))
    echo "DRIFT   $npath ($owner) <- $fpath since ${synced}:"
    sed 's/^/          /' <<<"${seen_commit[$key]}"
  fi
done <"$twins"

echo "twin_check: $checked of $rows rows checked (kind $kind) against ${ref} ${ref_sha:0:12} in ${repo/#$top/<this repo>}"
echo "twin_check: $drift_rows row(s) with Flutter commits to mirror, $bad invalid, $cannot not checkable"
((cannot)) && exit 2
((bad)) && exit 1
((drift_rows && !list_only)) && exit 1
exit 0
