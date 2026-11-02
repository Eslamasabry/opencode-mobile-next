#!/usr/bin/env bash
# Push scrub (plan 15 §2.1.1 step 6, §11.0): scans what a push would publish and BLOCKS it on any hit.
#
#   native/tool/scan_push.sh [<base> [<head>]]            scan the commits in <base>..<head> (default: <head> = HEAD)
#   native/tool/scan_push.sh --push <remote> <src>[:<dst>] [git push options...]
#                                                         scan the commits the remote does not have, then push
#   native/tool/scan_push.sh --pre-push <remote> [<url>]  git pre-push hook mode (refs on stdin); opt-in, never installed
#   native/tool/scan_push.sh --diff-file <patch>          scan a unified diff (added lines only, no commit metadata)
#   options before the mode: --base <ref> replaces "what the remote does not have" in --push / --pre-push.
#
# What it scans: every added line of every commit's patch (so a secret added and removed later still blocks), every
# commit message, and every commit's author and committer e-mail.
# What blocks:
#   - key material and tokens: PEM private keys, AWS, GitHub, Slack, Google, Anthropic and OpenAI key shapes, JWTs,
#     credentials inside URLs, and `password|secret|api_key|token = "<value>"` with a real-looking value;
#   - e-mail: the local `git config user.email` unless it is a no-reply address, any address in $OC_SCAN_EMAILS
#     (space separated; keep it out of git), and personal-mail domains (gmail, outlook, hotmail, yahoo, icloud, proton);
#   - private addresses: RFC 1918 (10/8, 172.16/12, 192.168/16), 100.64.0.0/10 (tailnet), 169.254/16 link-local,
#     IPv6 fc00::/7 and fe80::/10, and private host names (*.ts.net, *.lan, *.internal, *.home.arpa); only explicit canonical
#     network range literals are exempt outside URL authorities;
#   - extra patterns, one Python regex per line, from $OC_SCAN_EXTRA_FILE or ~/.config/oc/scan_push.local (private host
#     names; never committed);
#   - files that must never be committed: *.jks, *.keystore, *.p12, *.pfx, *.key, key.properties, local.properties,
#     google-services.json, .env, id_rsa / id_ed25519;
#   - an author or committer e-mail that is not a no-reply address (memory public-commit-email).
# Allowed exact values live in native/tool/scan_push.allow ("<rule> <value>  # reason"). Hits print masked: the
# scanner never echoes a secret it found.
# Exit: 0 clean (and, with --push, pushed), 1 hits (push blocked), 2 cannot scan or the push itself failed.
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"

command -v python3 >/dev/null || oc_die2 "python3 is required"
top="$(oc_repo_root)" || oc_die2 "no git repository"

base_override=""
if [[ "${1:-}" == "--base" ]]; then base_override="${2:-}"; shift 2; fi

mode=range
case "${1:-}" in
  --push) mode=push; shift ;;
  --pre-push) mode=prepush; shift ;;
  --diff-file) mode=difffile; shift ;;
  -h | --help) sed -n '2,32p' "$0"; exit 0 ;;
esac

own_email="$(git -C "$top" config user.email 2>/dev/null || true)"
export OC_SCAN_OWN_EMAIL="$own_email"
export OC_SCAN_ALLOW_FILE="$OC_TOOL_DIR/scan_push.allow"
extra="${OC_SCAN_EXTRA_FILE:-$HOME/.config/oc/scan_push.local}"
[[ -f "$extra" ]] && export OC_SCAN_EXTRA="$extra"

# stream_commits <rev-list args...>: commit metadata + patch per commit, NUL-delimited, for the scanner.
stream_commits() {
  local commits sha
  commits="$(git -C "$top" rev-list "$@")" || return 2
  for sha in $commits; do
    git -C "$top" show --no-color --no-ext-diff --cc -p \
      --format='%x00%x01%H%x1f%ae%x1f%ce%x1f%B%x00%x02' "$sha" || return 2
    printf '\0\3'
    git -C "$top" diff-tree --root -m -r --no-renames --no-commit-id --name-only -z \
      --diff-filter=ACMRT "$sha" || return 2
  done
}

run_scanner() { python3 -I "$OC_TOOL_DIR/lib/scan_push.py" "$@"; }

case "$mode" in
  difffile)
    [[ -f "${1:-}" ]] || oc_die2 "no patch file: ${1:-}"
    run_scanner --patch <"$1"; exit $? ;;
  range)
    base="$(oc_resolve_base "${base_override:-${1:-}}")" || oc_die2 "unknown base '${1:-${OC_BASE:-native/main}}'"
    head="${2:-HEAD}"
    git -C "$top" rev-parse --verify --quiet "$head^{commit}" >/dev/null || oc_die2 "unknown head: $head"
    echo "scan: commits in $base..$head ($(git -C "$top" rev-list --count "$base..$head"))"
    stream_commits "$base..$head" | run_scanner --commits; exit $? ;;
  push)
    remote="${1:-}"; spec="${2:-}"
    [[ -n "$remote" && -n "$spec" ]] || oc_usage "scan_push.sh --push <remote> <src>[:<dst>] [git push options...]"
    shift 2
    # Only a single branch update is supported. Options and config must not widen it.
    for option in "$@"; do
      case "$option" in
        --force | --force-with-lease | --force-with-lease=* | --dry-run | --porcelain | --quiet | --verbose | --atomic) ;;
        *) oc_die2 "unsupported push option (only single-ref options are allowed)" ;;
      esac
    done
    git -C "$top" remote get-url "$remote" >/dev/null 2>&1 || oc_die2 "push needs a named remote"
    src="${spec%%:*}"; force=""; [[ "$src" == +* ]] && force=+
    src="${src#+}"
    sha="$(git -C "$top" rev-parse --verify --quiet "$src^{commit}")" || oc_die2 "unknown source"
    if [[ "$spec" == *:* ]]; then
      dst="${spec#*:}"
      [[ "$dst" == refs/heads/* ]] || dst="refs/heads/$dst"
    else
      dst="$(git -C "$top" rev-parse --symbolic-full-name "$src")"
    fi
    [[ "$dst" == refs/heads/* && "$dst" != *:* ]] &&
      git check-ref-format "$dst" || oc_die2 "destination must be one branch"
    if [[ -n "$base_override" ]]; then range=("$base_override..$sha"); else range=("$sha" --not "--remotes=$remote"); fi
    echo "scan: $(git -C "$top" rev-list --count "${range[@]}") commit(s) not yet on $remote"
    stream_commits "${range[@]}" | run_scanner --commits
    rc=$?
    if ((rc != 0)); then echo "scan_push: push to $remote BLOCKED (exit $rc)" >&2; exit "$rc"; fi
    git -C "$top" -c "remote.$remote.mirror=false" -c push.recurseSubmodules=no \
      push --no-follow-tags "$@" "$remote" "$force$sha:$dst" || { echo "scan_push: scan clean, but git push failed" >&2; exit 2; }
    echo "scan_push: scanned clean and pushed $spec to $remote"
    exit 0 ;;
  prepush)
    remote="${1:-origin}"; status=0
    while read -r lref lsha rref rsha; do
      [[ -z "${lsha:-}" || "$lsha" =~ ^0+$ ]] && continue
      if [[ -n "$base_override" ]]; then range=("$base_override..$lsha")
      elif [[ ! "$rsha" =~ ^0+$ ]] && git -C "$top" cat-file -e "$rsha^{commit}" 2>/dev/null; then range=("$rsha..$lsha")
      else range=("$lsha" --not "--remotes=$remote"); fi
      echo "scan: $lref -> $rref"
      stream_commits "${range[@]}" | run_scanner --commits || status=$?
    done
    ((status)) && echo "scan_push: push BLOCKED" >&2
    exit $status ;;
esac
