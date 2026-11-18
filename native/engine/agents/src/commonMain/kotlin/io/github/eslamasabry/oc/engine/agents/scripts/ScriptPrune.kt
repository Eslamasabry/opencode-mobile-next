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
    /** `oc_prune_versions ROOT KEEP_NAMES PATTERN LAUNCH_LINK`. */
    val FUNCTION: String =
        """oc_prune_versions() {
  oc_pr_root=${D}1 oc_pr_keep=${D}2 oc_pr_re=${D}3 oc_pr_link=${D}4
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
    rm -rf -- "${D}oc_pr_path"
  done || true
}
# oc_prune_one PATH: one named leftover (a same-name backup), only as a real directory.
oc_prune_one() {
  [ -d "${D}1" ] && [ ! -L "${D}1" ] || return 0
  [ "${D}(readlink -f "${D}1")" = "${D}1" ] || return 0
  rm -rf -- "${D}1"
}
"""
}
