#!/usr/bin/env bash
# Snapshot push (plan 15 §11.0): local history cannot reach GitHub, so every push is a snapshot commit whose TREE
# equals the source commit's tree, parented on the GitHub branch it goes onto.
#
# Work snapshot (cloud lane, off-site copy; no GO needed):
#   native/tool/snapshot_push.sh [--source native/main] [--onto mobile-next/master] [--remote mobile-next]
#                                [--name snapshot/native-main-<YYYY-MM-DD>] [--dry-run]
# Release snapshot (the bridge, the switch candidate; needs the owner's GO):
#   native/tool/snapshot_push.sh --release --candidate <ref> --message-file <msg> [--onto ..] [--remote ..]
#                                [--go]       # --go also needs OC_OWNER_GO=<full candidate sha> in the environment
#
# Steps: commit-tree with author and committer set to the GitHub no-reply identity ($OC_SNAPSHOT_IDENT, default the
# owner's no-reply address); check `rev-parse S^{tree}` equals the source tree and S's only parent is --onto; run
# scan_push.sh over onto..S (a hit blocks); push S to the branch. Work mode refuses master, main and tags; release
# mode only ever pushes to master and never tags (the tag and the release run are separate GOs).
# Prints the line for native/LEDGER.md (the coordinator records the source sha there).
# Exit: 0 pushed (or dry run clean), 1 tree mismatch or scan hit, 2 cannot run (refs, message, GO, push failure).
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"

source_ref=native/main onto=mobile-next/master remote=mobile-next name="" dry=0 release=0 go=0 msgfile=""
while (($#)); do
  case "$1" in
    --source | --candidate) source_ref="${2:-}"; shift 2 ;;
    --onto) onto="${2:-}"; shift 2 ;;
    --remote) remote="${2:-}"; shift 2 ;;
    --name) name="${2:-}"; shift 2 ;;
    --message-file) msgfile="${2:-}"; shift 2 ;;
    --release) release=1; shift ;;
    --go) go=1; shift ;;
    --dry-run) dry=1; shift ;;
    -h | --help) sed -n '2,19p' "$0"; exit 0 ;;
    *) oc_usage "snapshot_push.sh [--source R] [--onto R] [--remote N] [--name B] [--dry-run] | --release --candidate R --message-file F [--go]" ;;
  esac
done

top="$(oc_repo_root)" || oc_die2 "no git repository"
g() { git -C "$top" "$@"; }
ident="${OC_SNAPSHOT_IDENT:-Eslamasabry <104366884+Eslamasabry@users.noreply.github.com>}"
id_name="${ident% <*}"; id_mail="${ident##*<}"; id_mail="${id_mail%>}"
[[ "$id_mail" =~ (@users\.noreply\.github\.com|^noreply@github\.com)$ ]] ||
  oc_die2 "OC_SNAPSHOT_IDENT must be a GitHub no-reply address (memory public-commit-email)"

src_sha="$(g rev-parse --verify --quiet "$source_ref^{commit}")" || oc_die2 "unknown source: $source_ref"
onto_sha="$(g rev-parse --verify --quiet "$onto^{commit}")" || oc_die2 "unknown onto: $onto (fetch it first)"
src_tree="$(g rev-parse "$src_sha^{tree}")"
today="$(date -u +%Y-%m-%d)"

if ((release)); then
  [[ -f "$msgfile" ]] || oc_die2 "--release needs --message-file"
  grep -qF '[skip ci]' "$msgfile" || oc_die2 "release message lacks [skip ci]"
  grep -qiE '^Change-Class: (hotfix|fix|feature|breaking)$' "$msgfile" ||
    oc_die2 "release message lacks a Change-Class: hotfix|fix|feature|breaking trailer (G33)"
  dest=refs/heads/master
  msg_args=(-F "$msgfile")
else
  name="${name:-snapshot/native-main-$today}"
  case "$name" in
    master | main | refs/heads/master | refs/heads/main | refs/tags/* | tags/*)
      oc_die2 "a work snapshot never goes to master, main or a tag ($name)" ;;
  esac
  dest="refs/heads/${name#refs/heads/}"
  msg_args=(-m "snapshot: ${source_ref} at ${src_sha:0:12} [skip ci]")
fi

snap="$(GIT_AUTHOR_NAME="$id_name" GIT_AUTHOR_EMAIL="$id_mail" GIT_COMMITTER_NAME="$id_name" \
  GIT_COMMITTER_EMAIL="$id_mail" g commit-tree "$src_tree" -p "$onto_sha" "${msg_args[@]}")" ||
  oc_die2 "git commit-tree failed"

snap_tree="$(g rev-parse "$snap^{tree}")"
parents="$(g rev-list --parents -n1 "$snap" | cut -d' ' -f2-)"
echo "source:   $source_ref ${src_sha}"
echo "onto:     $onto ${onto_sha}"
echo "snapshot: ${snap} (author and committer: $id_name, no-reply)"
echo "tree:     source ${src_tree} / snapshot ${snap_tree}"
if [[ "$snap_tree" != "$src_tree" || "$parents" != "$onto_sha" ]]; then
  echo "snapshot_push: FAILED, the snapshot's tree or parent does not match" >&2
  exit 1
fi
echo "tree-equal: yes; parent: $onto"

"$OC_TOOL_DIR/scan_push.sh" "$onto_sha" "$snap"
rc=$?
if ((rc != 0)); then echo "snapshot_push: push BLOCKED by scan_push.sh (exit $rc)" >&2; exit "$rc"; fi

echo "ledger:   | $today | ${dest#refs/heads/} | source ${src_sha:0:12} | snapshot ${snap:0:12} | tree ${src_tree:0:12} |"
if ((release)); then
  if ((!go)); then
    echo "dry run (release): would run: git push $remote $snap:$dest  (needs --go and OC_OWNER_GO=<candidate sha>)"
    exit 0
  fi
  [[ "${OC_OWNER_GO:-}" == "$src_sha" ]] || oc_die2 "release push needs OC_OWNER_GO set to the full candidate sha"
elif ((dry)); then
  echo "dry run: would run: git push $remote $snap:$dest"
  exit 0
fi
g push "$remote" "$snap:$dest" || oc_die2 "git push failed"
echo "snapshot_push: pushed ${snap:0:12} to $remote $dest"
