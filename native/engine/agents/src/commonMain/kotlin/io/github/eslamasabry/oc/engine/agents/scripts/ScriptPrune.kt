package io.github.eslamasabry.oc.engine.agents.scripts

private const val D = "$"

/**
 * The one shell function every install script uses to remove OLD versions of a component, only after the new version
 * is switched in and has started from its final place. It is deliberately narrow:
 *
 * - the root folder must itself be a real folder (no link in the way) and the running program's launch link must
 *   resolve INTO it, or nothing is removed;
 * - only names that match the strict version pattern are considered (the list is built from `ls` and `grep -E`, never
 *   a wildcard of the delete itself);
 * - each entry is skipped when it is in the keep list, is not a real directory, is a link, does not resolve to its own
 *   path, or is the folder the launch link resolves to;
 * - what is deleted is `root/name`, one explicit path per loop turn.
 */
internal object ScriptPrune {
    /**
     * `oc_in_use PATH`: 0 when some process has PATH (or anything under it) as its program, its working folder or a
     * mapped file. Only `/proc/<pid>/exe`, `cwd` and `maps` are read, never a command line; what cannot be read is
     * skipped. Under PRoot those entries carry the phone-side path, which ends with the Linux path, so the match is by
     * suffix (a wrong match only keeps a folder). `oc_prune_versions ROOT KEEP_NAMES PATTERN LAUNCH_LINK [GUARD]`:
     * GUARD is a function that must answer 0 for a folder to go.
     */
    val FUNCTION: String =
        """oc_in_use() {
  for oc_u in /proc/[0-9]*; do
    [ -d "${D}oc_u" ] || continue
    oc_u_exe=${D}(readlink "${D}oc_u/exe" 2>/dev/null) || oc_u_exe=
    oc_u_cwd=${D}(readlink "${D}oc_u/cwd" 2>/dev/null) || oc_u_cwd=
    case "${D}oc_u_exe" in *"${D}1"/*) return 0 ;; esac
    case "${D}oc_u_cwd" in *"${D}1"|*"${D}1"/*) return 0 ;; esac
    if grep -qF -- "${D}1/" "${D}oc_u/maps" 2>/dev/null; then return 0; fi
  done
  return 1
}
oc_prune_versions() {
  oc_pr_root=${D}1 oc_pr_keep=${D}2 oc_pr_re=${D}3 oc_pr_link=${D}4 oc_pr_guard=${D}{5:-}
  [ -d "${D}oc_pr_root" ] && [ ! -L "${D}oc_pr_root" ] || return 0
  [ "${D}(cd "${D}oc_pr_root" && pwd -P)" = "${D}oc_pr_root" ] || return 0
  oc_pr_live=${D}(readlink -f "${D}oc_pr_link" 2>/dev/null) || return 0
  case "${D}oc_pr_live" in "${D}oc_pr_root"/*) ;; *) return 0 ;; esac
  oc_pr_live=${D}{oc_pr_live#"${D}oc_pr_root"/}
  oc_pr_live="${D}oc_pr_root/${D}{oc_pr_live%%/*}"
  ls -1A "${D}oc_pr_root" 2>/dev/null | grep -E "${D}oc_pr_re" | while IFS= read -r oc_pr_name; do
    case " ${D}oc_pr_keep " in *" ${D}oc_pr_name "*) continue ;; esac
    oc_pr_path="${D}oc_pr_root/${D}oc_pr_name"
    [ -d "${D}oc_pr_path" ] && [ ! -L "${D}oc_pr_path" ] || continue
    [ "${D}(readlink -f "${D}oc_pr_path")" = "${D}oc_pr_path" ] || continue
    [ "${D}oc_pr_path" != "${D}oc_pr_live" ] || continue
    if [ -n "${D}oc_pr_guard" ] && ! "${D}oc_pr_guard" "${D}oc_pr_path"; then continue; fi
    # A running program of that folder (an old helper or agent started before the update) keeps it until a later check.
    if oc_in_use "${D}oc_pr_path"; then continue; fi
    rm -rf -- "${D}oc_pr_path"
  done || true
}
# oc_prune_one PATH: one named leftover (a same-name backup), only as a real directory nothing runs from.
oc_prune_one() {
  [ -d "${D}1" ] && [ ! -L "${D}1" ] || return 0
  [ "${D}(readlink -f "${D}1")" = "${D}1" ] || return 0
  if oc_in_use "${D}1"; then return 0; fi
  rm -rf -- "${D}1"
}
"""

    /** Extra cleanup helpers for stale staging folders, old downloads, probe folders and old Node archives. */
    val LEFTOVERS: String =
        """# oc_prune_stale ROOT KEEP RE LINK: an old version's .new, .old.<pid> and .oc-good folders go.
oc_prune_stale() {
  oc_ps_root=${D}1 oc_ps_keep=${D}2 oc_ps_re=${D}3 oc_ps_link=${D}4
  [ -d "${D}oc_ps_root" ] && [ ! -L "${D}oc_ps_root" ] || return 0
  [ "${D}(cd "${D}oc_ps_root" && pwd -P)" = "${D}oc_ps_root" ] || return 0
  oc_ps_live=${D}(readlink -f "${D}oc_ps_link" 2>/dev/null) || return 0
  case "${D}oc_ps_live" in "${D}oc_ps_root"/*) ;; *) return 0 ;; esac
  oc_ps_live=${D}{oc_ps_live#"${D}oc_ps_root"/}
  oc_ps_live="${D}oc_ps_root/${D}{oc_ps_live%%/*}"
  ls -1A "${D}oc_ps_root" 2>/dev/null |
    grep -E "^(${D}oc_ps_re)\.(new|oc-good|old\.[0-9]+)${D}" | while IFS= read -r oc_ps_name; do
    case "${D}oc_ps_name" in
      *.new) oc_ps_ver=${D}{oc_ps_name%.new} ;;
      *.oc-good) oc_ps_ver=${D}{oc_ps_name%.oc-good} ;;
      *) oc_ps_ver=${D}{oc_ps_name%.old.*} ;;
    esac
    case " ${D}oc_ps_keep " in *" ${D}oc_ps_ver "*) continue ;; esac
    oc_ps_pending="${D}oc_ps_root/${D}oc_ps_ver.oc-pending"
    [ ! -e "${D}oc_ps_pending" ] && [ ! -L "${D}oc_ps_pending" ] || continue
    oc_ps_path="${D}oc_ps_root/${D}oc_ps_name"
    [ -d "${D}oc_ps_path" ] && [ ! -L "${D}oc_ps_path" ] || continue
    [ "${D}(readlink -f "${D}oc_ps_path")" = "${D}oc_ps_path" ] || continue
    [ "${D}oc_ps_path" != "${D}oc_ps_live" ] || continue
    if oc_in_use "${D}oc_ps_path"; then continue; fi
    rm -rf -- "${D}oc_ps_path"
  done || true
}
# oc_prune_downloads DIR KEEP: downloads of versions no longer kept go.
oc_prune_downloads() {
  oc_pd_dir=${D}1 oc_pd_keep=${D}2
  [ -d "${D}oc_pd_dir" ] && [ ! -L "${D}oc_pd_dir" ] || return 0
  ls -1A "${D}oc_pd_dir" 2>/dev/null | grep -E -- '-[0-9a-f]{64}${D}' | while IFS= read -r oc_pd_name; do
    oc_pd_kept=
    for oc_pd_k in ${D}oc_pd_keep; do
      case "${D}oc_pd_name" in "${D}oc_pd_k"-*) oc_pd_kept=1 ;; esac
    done
    [ -z "${D}oc_pd_kept" ] || continue
    oc_pd_path="${D}oc_pd_dir/${D}oc_pd_name"
    [ -f "${D}oc_pd_path" ] && [ ! -L "${D}oc_pd_path" ] || continue
    rm -f -- "${D}oc_pd_path"
  done || true
}
# oc_prune_probes ROOT: day-old, unused version-check folders a killed install left go.
oc_prune_probes() {
  oc_pp_root=${D}1
  [ -d "${D}oc_pp_root" ] && [ ! -L "${D}oc_pp_root" ] || return 0
  ls -1A "${D}oc_pp_root" 2>/dev/null | grep -E '^\.probe\.[A-Za-z0-9]+${D}' | while IFS= read -r oc_pp_name; do
    oc_pp_path="${D}oc_pp_root/${D}oc_pp_name"
    [ -d "${D}oc_pp_path" ] && [ ! -L "${D}oc_pp_path" ] || continue
    [ -n "${D}(find "${D}oc_pp_path" -maxdepth 0 -mmin +1440 2>/dev/null)" ] || continue
    if oc_in_use "${D}oc_pp_path"; then continue; fi
    rm -rf -- "${D}oc_pp_path"
  done || true
}
# oc_prune_node_parts DIR PREFIX: Node downloads of versions no longer pinned go.
oc_prune_node_parts() {
  oc_pn_dir=${D}1 oc_pn_keep=${D}2
  [ -d "${D}oc_pn_dir" ] && [ ! -L "${D}oc_pn_dir" ] || return 0
  ls -1A "${D}oc_pn_dir" 2>/dev/null |
    grep -E '^node-v[0-9]+\.[0-9]+\.[0-9]+-linux-[a-z0-9]+\.tar\.gz${D}' | while IFS= read -r oc_pn_name; do
    case "${D}oc_pn_name" in "${D}oc_pn_keep"*) continue ;; esac
    oc_pn_path="${D}oc_pn_dir/${D}oc_pn_name"
    [ -f "${D}oc_pn_path" ] && [ ! -L "${D}oc_pn_path" ] || continue
    rm -f -- "${D}oc_pn_path"
  done || true
}
"""
}
